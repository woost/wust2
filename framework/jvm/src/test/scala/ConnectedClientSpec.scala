package wust.framework

import akka.actor._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import java.nio.ByteBuffer
import org.scalatest._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito.verify
import akka.testkit.{TestKit, TestActorRef, ImplicitSender}
import boopickle.Default._
import autowire.Core.Request
import scala.concurrent.duration._

import message._

object TestRequestHandler extends RequestHandler[String, String, Option[String]] {
  private val otherUser = Future.successful(Option("anon"))

  override def router(sender: EventSender[String], state: Future[Option[String]]): PartialFunction[Request[ByteBuffer], RequestResult[Option[String]]] = {
    case Request("api" :: Nil, args) =>
      RequestResult(state, Future.successful(args.values.headOption.map(Unpickle[String].fromBytes).map(_.reverse).map(s => Pickle.intoBytes(s)).get))
    case Request("event" :: Nil, _) =>
      sender.send("event")
      RequestResult(state, Future.successful(Pickle.intoBytes[Boolean](true)))
    case Request("state" :: Nil, _) =>
      RequestResult(state, state.map(u => Pickle.intoBytes[Option[String]](u)))
    case Request("state" :: "change" :: Nil, _) =>
      RequestResult(otherUser, Future.successful(Pickle.intoBytes[Boolean](true)))
    case Request("broken" :: Nil, _) =>
      RequestResult(state, Future.failed(new Exception("an error")))
  }

  override def initialState = Future.successful(None)
  override def onClientStop(sender: EventSender[String], state: Option[String]) = sender.send("stopped")

  override def pathNotFound(path: Seq[String]) = "path not found"
  override def toError: PartialFunction[Throwable, String] = { case e => e.getMessage }
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers with MockitoSugar {

  val messages = new Messages[String, String]
  import messages._

  def newActor = TestActorRef(new ConnectedClient(messages, TestRequestHandler))
  def connectActor(actor: ActorRef) = actor ! ConnectedClient.Connect(self)

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
      connectActor(actor)
      actor ! Ping()
      expectNoMsg
    }
  }

  "ping" - {
    val actor = newActor
    connectActor(actor)

    "expect pong" in {
      actor ! Ping()
      expectMsg(Pong())
    }
  }

  "call request" - {
    val actor = newActor
    connectActor(actor)

    "invalid path" in {
      actor ! CallRequest(2, Seq("invalid", "path"), Map.empty)
      expectMsg(CallResponse(2, Left("path not found")))
    }

    "exception in api" in {
      actor ! CallRequest(2, Seq("broken"), Map.empty)
      expectMsg(CallResponse(2, Left("an error")))
    }

    "call api" in {
      actor ! CallRequest(2, Seq("api"),
        Map("s" -> AutowireServer.write[String]("hans")))

      val pickledResponse = AutowireServer.write[String]("snah")
      expectMsg(CallResponse(2, Right(pickledResponse)))
    }

    "switch state" in {
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

    "send event" in {
      actor ! CallRequest(2, Seq("event"), Map.empty)

      val pickledResponse = AutowireServer.write[Boolean](true)
      expectMsgAllOf(
        10 seconds,
        Notification("event"),
        CallResponse(2, Right(pickledResponse))
      )
    }
  }

  "stop" - {
    val actor = newActor
    connectActor(actor)

    "stops actor" in {
      actor ! ConnectedClient.Stop
      actor ! Ping()
      expectMsg(Notification("stopped"))
    }
  }
}
