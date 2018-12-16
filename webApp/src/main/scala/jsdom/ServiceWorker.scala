package wust.webApp.jsdom

import io.circe.{Decoder, Encoder, Json}
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.experimental.serviceworkers.{ServiceWorker => OriginalServiceWorker}
import org.scalajs.dom.{window, _}
import wust.api.Authentication
import wust.webApp.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success}

object ServiceWorker {

  // Use the window load event to keep the page load performant
  def register(): Observable[Unit] = {
    val subject = PublishSubject[Unit]()

    Navigator.serviceWorker.foreach { sw =>
      window.addEventListener("load", (_: Any) => {
        sw.register("sw.js").toFuture.onComplete {
          case Success(registration) =>
            console.log("SW registered: ", registration)
            registration.onupdatefound = { event =>
              val installingWorker = registration.installing
              installingWorker.onstatechange = { event =>
                val activeServiceworker = sw.controller
                if (installingWorker.state == "installed" && activeServiceworker != null) {
                  console.log("New SW installed, can update.")
                  subject.onNext(())
                }
              }
            }
          case Failure(registrationError) =>
            scribe.warn("SW registration failed: ", registrationError)
            subject.onError(registrationError)
        }
      })
    }

    subject
  }


  sealed trait WorkerMessage { def message: String }
  case class AuthMessage(token: String) extends WorkerMessage {
    override def message: String = token
  }
  case class Message(message: String) extends WorkerMessage

  def sendAuth(auth: Authentication): Unit = {
    import io.circe.syntax._
    import io.circe.generic.extras.semiauto._ // nicht circe ohne generic
    import wust.ids.serialize.Circe._ // das gibt die die config mit `{"type": "SubClassName", .... }`

    implicit val serviceWorkerMessageEncoder: Encoder[WorkerMessage] = deriveEncoder[WorkerMessage]
    implicit val serviceWorkerMessageDecoder: Decoder[WorkerMessage] = deriveDecoder[WorkerMessage]

    Navigator.serviceWorker.foreach { sw =>
      auth match {
        case Authentication.Verified(_, _, token) =>
          val activeServiceworker =  sw.controller
          if(activeServiceworker != null)
            activeServiceworker.postMessage((AuthMessage(token): WorkerMessage).asJson.noSpaces);
          else scribe.debug("No serviceworker found")
        case _ =>
          scribe.debug("No token available")
      }
    }
  }

}
