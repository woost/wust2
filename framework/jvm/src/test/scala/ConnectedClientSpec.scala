package wust.framework

import java.nio.ByteBuffer

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import autowire.Core.Request
import boopickle.Default._
import org.scalatest._
import wust.framework.message._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class TestRequestHandler(eventActor: ActorRef) extends RequestHandler[String, String, String, Option[String]] {
  val clients = mutable.ArrayBuffer.empty[NotifiableClient[String]]
  val events = mutable.ArrayBuffer.empty[String]

  override def onRequest(client: ClientIdentity, state: Future[Option[String]], request: Request[ByteBuffer]) = {
    def response[S : Pickler](reaction: Reaction, ts: S): Response = {
      val r = Future.successful(Right(Pickle.intoBytes[S](ts)))
      Response(reaction, r)
    }

    def error(reaction: Reaction, ts: String): Response = {
      val r = Future.successful(Left(ts))
      Response(reaction, r)
    }

    val handler: PartialFunction[Request[ByteBuffer], Response] = {
      case Request("api" :: Nil, args) =>
        val arg = args.values.head
        val str = Unpickle[String].fromBytes(arg)
        response(Reaction(state), str.reverse)
      case Request("event" :: Nil, _) =>
        val events = Future.successful(Seq("event"))
        response(Reaction(state, events), true)
      case Request("state" :: Nil, _) =>
        val pickledState = state.map(s => Right(Pickle.intoBytes(s)))
        Response(Reaction(state), pickledState)
      case Request("state" :: "change" :: Nil, _) =>
        val otherUser = Future.successful(Option("anon"))
        response(Reaction(otherUser), true)
      case Request("broken" :: Nil, _) =>
        error(Reaction(state), "an error")
    }

    handler.lift(request) getOrElse Response(Reaction(state), Future.successful(Left("path not found")))
  }

  override def onEvent(client: ClientIdentity, state: Future[Option[String]], event: String) = {
    events += event
    val downstreamEvents = Seq(s"${event}-ok")
    Reaction(state, Future.successful(downstreamEvents))
  }

  override def onClientConnect(client: NotifiableClient[String]): Reaction = {
    client.notify(null, "started")
    clients += client
    Reaction(Future.successful(None), Future.successful(Seq.empty))
  }
  override def onClientDisconnect(client: NotifiableClient[String], state: Future[Option[String]]) = {
    clients -= client
    ()
  }
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers {
  val messages = new Messages[String, String]
  import messages._

  def requestHandler = new TestRequestHandler(self)
  def newActor(handler: TestRequestHandler = requestHandler): ActorRef = TestActorRef(new ConnectedClient(messages, handler))
  def connectActor(actor: ActorRef, shouldConnect: Boolean = true) = {
    actor ! ConnectedClient.Connect(self)
    if (shouldConnect) expectMsg(Notification(List("started-ok")))
    else expectNoMsg
  }
  def connectedActor(handler: TestRequestHandler = requestHandler): ActorRef = {
    val actor = newActor(handler)
    connectActor(actor)
    actor
  }

  def newActor[T](f: ActorRef => T): T = f(newActor())
  def connectedActor[T](f: ActorRef => T): T = f(connectedActor())

  "unconnected" - {
    val actor = newActor()

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
        1 seconds,
        CallResponse(1, Right(pickledResponse1)),
        CallResponse(2, Right(pickledResponse2)),
        CallResponse(3, Right(pickledResponse3)))
    }

    "send event" in connectedActor { actor =>
      actor ! CallRequest(2, Seq("event"), Map.empty)

      val pickledResponse = AutowireServer.write[Boolean](true)
      expectMsgAllOf(
        1 seconds,
        Notification(List("event")),
        CallResponse(2, Right(pickledResponse)))
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
