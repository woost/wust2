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

object TestRequestHandler extends RequestHandler[Int, String, String, String, String] {
  //TODO: cornerman: is this fix correct? See the diff of this commit.
  def fakeAuth = Future.successful(Option(""))
  override def router(user: Future[Option[String]]): PartialFunction[Request[ByteBuffer], (Future[Option[String]], Future[ByteBuffer])] = {
    case Request("api" :: Nil, args) => (fakeAuth, Future.successful(args.values.headOption.map(Unpickle[String].fromBytes).map(_.reverse).map(s => Pickle.intoBytes(s)).get))
    case Request("user" :: Nil, _) => (fakeAuth, user.map(u => Pickle.intoBytes[String](u.get)))
    case Request("broken" :: Nil, _) => (fakeAuth, Future.failed(new Exception("an error")))
  }

  // override def createImplicitAuth(): Future[String] = Future.successful("anon")

  override def pathNotFound(path: Seq[String]) = "path not found"
  override def toError: PartialFunction[Throwable, String] = { case e => e.getMessage }
  override def authenticate(auth: String): Future[Option[String]] = Future.successful(if (auth.isEmpty) None else Option(auth))
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers with MockitoSugar {

  val messages = new Messages[Int, String, String, String, String]
  import messages._

  val dispatcher = mock[Dispatcher[Int, String]]

  def newActor = TestActorRef(new ConnectedClient(messages, TestRequestHandler, dispatcher))
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

    "no control request" in {
      actor ! ControlRequest(2, Login(""))
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

    "call api" in pendingUntilFixed {
      actor ! CallRequest(2, Seq("api"),
        Map("s" -> AutowireServer.write[String]("hans")))

      val pickledResponse = AutowireServer.write[String]("snah")
      expectMsg(CallResponse(2, Right(pickledResponse)))
    }
  }

  "control notification" - {
    val actor = newActor
    connectActor(actor)

    "anon login" in pendingUntilFixed {
      actor ! CallRequest(2, Seq("user"), Map.empty)
      val pickledResponse = AutowireServer.write[String]("anon")
      expectMsgAllOf(
        10 seconds,
        ControlNotification(ImplicitLogin("anon")),
        CallResponse(2, Right(pickledResponse))
      )
    }
  }

  "control request" - {
    val actor = newActor
    connectActor(actor)

    "invalid login" in pendingUntilFixed {
      actor ! ControlRequest(2, Login(""))
      expectMsg(ControlResponse(2, false))

      actor ! CallRequest(2, Seq("user"), Map.empty)
      val pickledResponse = AutowireServer.write[String]("anon")
      expectMsgAllOf(
        10 seconds,
        ControlNotification(ImplicitLogin("anon")),
        CallResponse(2, Right(pickledResponse))
      )
    }

    "valid login" in pendingUntilFixed {
      val userName = "pete"
      actor ! ControlRequest(2, Login(userName))
      expectMsg(ControlResponse(2, true))

      actor ! CallRequest(2, Seq("user"), Map.empty)
      val pickledResponse = AutowireServer.write[String](userName)
      expectMsg(CallResponse(2, Right(pickledResponse)))
    }

    "logout" in {
      actor ! ControlRequest(2, Logout())
      expectMsg(ControlResponse(2, true))
    }

    "subscribe" in {
      actor ! ControlRequest(2, Subscribe(1))
      expectMsg(ControlResponse(2, true))
      verify(dispatcher).subscribe(self, 1)
    }

    "unsubscribe" in {
      actor ! ControlRequest(2, Unsubscribe(1))
      expectMsg(ControlResponse(2, true))
      verify(dispatcher).unsubscribe(self, 1)
    }
  }

  "stop" - {
    val actor = newActor
    connectActor(actor)

    "stops actor" in pendingUntilFixed {
      actor ! ConnectedClient.Stop
      actor ! Ping()
      expectNoMsg
      verify(dispatcher).unsubscribe(self)
    }
  }
}
