package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import autowire.Core.Request
import boopickle.Default._
import org.scalatest._
import wust.framework.message._
import wust.framework.state._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable

class TestRequestHandler(eventActor: ActorRef) extends RequestHandler[String, String, Option[String]] {
  private val stupidPhrase = "the stupid guy"
  private val stupidUser = Future.successful(Option(stupidPhrase))
  private val otherUser = Future.successful(Option("anon"))
  private val clients = mutable.ArrayBuffer.empty[EventSender[String]]

  override def initialState = None

  override def validate(state: Option[String]): Option[String] = state.filterNot(_ == stupidPhrase)

  override def onRequest(holder: StateHolder[Option[String], String], request: Request[ByteBuffer]) = {
    import holder._
    val handler: PartialFunction[Request[ByteBuffer], Future[ByteBuffer]] = {
      case Request("api" :: Nil, args) =>
        (state: Option[String]) => Future.successful(args.values.headOption.map(Unpickle[String].fromBytes).map(_.reverse).map(s => Pickle.intoBytes(s)).get)
      case Request("event" :: Nil, _) =>
        (state: Option[String]) => Future.successful(respondWithEvents(Pickle.intoBytes[Boolean](true), "event"))
      case Request("state" :: Nil, _) =>
        (state: Option[String]) => Future.successful(Pickle.intoBytes[Option[String]](state))
      case Request("state" :: "change" :: Nil, _) =>
        (state: Option[String]) => StateEffect(otherUser, Future.successful(Pickle.intoBytes[Boolean](true)))
      case Request("state" :: "stupid" :: Nil, _) =>
        (state: Option[String]) => StateEffect(stupidUser, Future.successful(Pickle.intoBytes[Boolean](true)))
      case Request("broken" :: Nil, _) =>
        (state: Option[String]) => Future.failed(new Exception("an error"))
    }

    handler.lift(request).toRight("path not found")
  }

  override def toError: PartialFunction[Throwable, String] = { case e => e.getMessage }

  override def publishEvent(event: String): Unit = { eventActor ! event }

  override def triggeredEvents(event: String, state: Option[String]) = event match {
    case "FORBIDDEN" => Future.successful(Seq.empty)
    case other => Future.successful(Seq(other))
  }

  override def onEvent(event: String, state: Option[String]) = state

  override def onClientConnect(sender: EventSender[String], state: Option[String]) = {
    sender.send("started")
    clients += sender
    ()
  }
  override def onClientDisconnect(sender: EventSender[String], state: Option[String]) = {
    clients -= sender
    ()
  }
  override def onClientInteraction(sender: EventSender[String], prevState: Option[String], state: Option[String]) = ()
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers {
  val messages = new Messages[String, String]
  import messages._

  val requestHandler = new TestRequestHandler(self)
  def newActor: ActorRef = TestActorRef(new ConnectedClient(messages, requestHandler))
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
        CallResponse(3, Right(pickledResponse3)))
    }

    "filter stupid after switch state" in connectedActor { actor =>
      actor ! CallRequest(1, Seq("state"), Map.empty)
      actor ! CallRequest(2, Seq("state", "stupid"), Map.empty)
      actor ! CallRequest(3, Seq("state"), Map.empty)

      val pickledResponse1 = AutowireServer.write[Option[String]](None)
      val pickledResponse2 = AutowireServer.write[Boolean](true)
      val pickledResponse3 = AutowireServer.write[Option[String]](None)
      expectMsgAllOf(
        10 seconds,
        CallResponse(1, Right(pickledResponse1)),
        CallResponse(2, Right(pickledResponse2)),
        CallResponse(3, Right(pickledResponse3)))
    }

    "send event" in connectedActor { actor =>
      actor ! CallRequest(2, Seq("event"), Map.empty)

      val pickledResponse = AutowireServer.write[Boolean](true)
      expectMsgAllOf(
        10 seconds,
        "event",
        CallResponse(2, Right(pickledResponse)))
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
