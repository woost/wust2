package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import autowire.Core.Request
import boopickle.Default._
import org.scalatest._
import wust.framework.message._
import wust.framework.state._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers {
  object TestRequestHandler extends RequestHandler[String, String, Option[String]] {
    private val stupidPhrase = "the stupid guy"
    private val stupidUser = Future.successful(Option(stupidPhrase))
    private val otherUser = Future.successful(Option("anon"))

    override def before(state: Future[Option[String]]): Future[Option[String]] = state.map(_.filterNot(_ == stupidPhrase))

    //TODO test
    override def after(prevState: Future[Option[String]], state: Future[Option[String]]): Future[Seq[Future[String]]] = Future.successful(Seq.empty)

    override def router(holder: StateHolder[Option[String], String]): PartialFunction[Request[ByteBuffer], Future[ByteBuffer]] = {
      import holder._
      {
        case Request("api" :: Nil, args) =>
          (state: Option[String]) => Future.successful(args.values.headOption.map(Unpickle[String].fromBytes).map(_.reverse).map(s => Pickle.intoBytes(s)).get)
        case Request("event" :: Nil, _) =>
          (state: Option[String]) => Future.successful(RequestResponse[ByteBuffer](Pickle.intoBytes[Boolean](true), "event"))
        case Request("state" :: Nil, _) =>
          (state: Option[String]) => Future.successful(Pickle.intoBytes[Option[String]](state))
        case Request("state" :: "change" :: Nil, _) =>
          (state: Option[String]) => StateEffect.replace(otherUser, Future.successful(Pickle.intoBytes[Boolean](true)))
        case Request("state" :: "stupid" :: Nil, _) =>
          (state: Option[String]) => StateEffect.replace(stupidUser, Future.successful(Pickle.intoBytes[Boolean](true)))
        case Request("broken" :: Nil, _) =>
          (state: Option[String]) => Future.failed(new Exception("an error"))
      }
    }

    override def isEventAllowed(event: String,state: Future[Option[String]]) = Future.successful(event != "FORBIDDEN")

    override def publishEvent(event: String): Unit = {
      self ! event
    }

    override def onClientStart(sender: EventSender[String]) = {
      sender.send("started")
      Future.successful(None)
    }
    override def onClientStop(sender: EventSender[String], state: Option[String]) = ()

    override def pathNotFound(path: Seq[String]) = "path not found"
    override def toError: PartialFunction[Throwable, String] = { case e => e.getMessage }
  }

  val messages = new Messages[String, String]
  import messages._

  def newActor: ActorRef = TestActorRef(new ConnectedClient(messages, TestRequestHandler))
  def connectActor(actor: ActorRef, shouldConnect: Boolean = true) = {
    actor ! ConnectedClient.Connect(self)
    if (shouldConnect) expectMsg(Notification("started"))
    else expectNoMsg
  }
  def connectedActor: ActorRef = {
    val actor = newActor
    connectActor(actor)
    actor
  }

  def newActor[T](f: ActorRef => T): T = f(newActor)
  def connectedActor[T](f: ActorRef => T): T = f(connectedActor)

  "event sender" - {
    "compareTo same" in {
      val actor = newActor
      val sender = new EventSender(messages, actor)
      val sender2 = new EventSender(messages, actor)
      sender.compareTo(sender2) mustEqual 0
    }

    "compareTo different" in {
      val actor = newActor
      val actor2 = newActor
      val sender = new EventSender(messages, actor)
      val sender2 = new EventSender(messages, actor2)
      sender.compareTo(sender2) must not equal(0)
    }
  }

  "unconnected" - {
    val actor = newActor

    "no pong" in {
      actor ! Ping()
      expectNoMsg
    }

    "no call request" in {
      actor ! CallRequest(2, Seq("invalid", "path"), Map.empty)
      expectNoMsg
    }

    "stop" in {
      actor ! ConnectedClient.Stop
      connectActor(actor, shouldConnect = false)
      actor ! Ping()
      expectNoMsg
    }
  }

  "ping" - {
    "expect pong" in connectedActor { actor =>
      actor ! Ping()
      expectMsg(Pong())
    }
  }

  "call request" - {
    "invalid path" in connectedActor { actor =>
      actor ! CallRequest(2, Seq("invalid", "path"), Map.empty)
      expectMsg(CallResponse(2, Left("path not found")))
    }

    "exception in api" in connectedActor { actor =>
      actor ! CallRequest(2, Seq("broken"), Map.empty)
      expectMsg(CallResponse(2, Left("an error")))
    }

    "call api" in connectedActor { actor =>
      actor ! CallRequest(2, Seq("api"),
        Map("s" -> AutowireServer.write[String]("hans")))

      val pickledResponse = AutowireServer.write[String]("snah")
      expectMsg(CallResponse(2, Right(pickledResponse)))
    }

    "switch state" in connectedActor { actor =>
      actor ! CallRequest(1, Seq("state"), Map.empty)
      actor ! CallRequest(2, Seq("state", "change"), Map.empty)
      actor ! CallRequest(3, Seq("state"), Map.empty)

      val pickledResponse1 = AutowireServer.write[Option[String]](None)
      val pickledResponse2 = AutowireServer.write[Boolean](true)
      val pickledResponse3 = AutowireServer.write[Option[String]](Option("anon"))
      expectMsgAllOf(
        10 seconds,
        CallResponse(1, Right(pickledResponse1)),
        CallResponse(2, Right(pickledResponse2)),
        CallResponse(3, Right(pickledResponse3))
      )
    }

    "filter stupid after switch state" in connectedActor { actor =>
      actor ! CallRequest(1, Seq("state"), Map.empty)
      // actor ! CallRequest(2, Seq("state", "stupid"), Map.empty)
      actor ! CallRequest(3, Seq("state"), Map.empty)

      val pickledResponse1 = AutowireServer.write[Option[String]](None)
      val pickledResponse2 = AutowireServer.write[Boolean](true)
      val pickledResponse3 = AutowireServer.write[Option[String]](None)
      expectMsgAllOf(
        10 seconds,
        CallResponse(1, Right(pickledResponse1)),
        // CallResponse(2, Right(pickledResponse2)),
        CallResponse(3, Right(pickledResponse3))
      )
    }

    "send event" in connectedActor { actor =>
      actor ! CallRequest(2, Seq("event"), Map.empty)

      val pickledResponse = AutowireServer.write[Boolean](true)
      expectMsgAllOf(
        10 seconds,
        "event",
        CallResponse(2, Right(pickledResponse))
      )
    }
  }

  "event" - {
    val actor = connectedActor

    "allowed event" in {
      actor ! Notification("something nice")
      expectMsg(Notification("something nice"))
    }

    "forbidden event" in {
      actor ! Notification("FORBIDDEN")
      expectNoMsg
    }
  }

  "stop" - {
    "stops actor" in connectedActor { actor =>
      actor ! ConnectedClient.Stop
      actor ! Ping()
      expectNoMsg
    }
  }
}
