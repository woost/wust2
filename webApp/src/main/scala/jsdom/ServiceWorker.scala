package wust.webApp.jsdom

import googleAnalytics.Analytics
import io.circe.{Decoder, Encoder, Json}
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.experimental.serviceworkers.{ServiceWorker => OriginalServiceWorker}
import org.scalajs.dom.{window, _}
import wust.api.Authentication
import wust.webApp.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success, Try}

object ServiceWorker {

  // Use the window load event to keep the page load performant
  def register(): Observable[Unit] = {
    val subject = PublishSubject[Unit]()

    Navigator.serviceWorker.foreach { sw =>
      window.addEventListener("load", (_: Any) => {
        Try(sw.register("sw.js")).toEither match {
          case Right(registered) => registered.toFuture.onComplete {
            case Success(registration)      =>
              scribe.info(s"SW successfully registered")
              registration.onupdatefound = { event =>
                val installingWorker = registration.installing
                installingWorker.onstatechange = { event =>
                  val activeServiceworker = sw.controller
                  if(installingWorker.state == "installed" && activeServiceworker != null) {
                    scribe.info("New SW installed, can update.")
                    subject.onNext(())
                  }
                }
              }
            case Failure(registrationError) =>
              scribe.warn("SW registration failed: ", registrationError)
              subject.onError(registrationError)
          }
          case Left(e)             =>
            scribe.error("SW could not register:", e)
            subject.onError(e)
        }
      })
    }

    /* Register crashes can occur when privacy settings are too strong for serviceworkers, e.g. in ff when
     * cookies are deleted, history is cleared or in privacy mode.
     * To stop woost from crashing and just running without serviceworkers, we need this workaround
     */
    subject.onErrorRecoverWith{
      case e: Throwable =>
        scribe.debug("SW could not register:", e)
        Analytics.sendEvent("serviceworker", "register", "error")
        Observable.empty[Unit]
    }
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

    scribe.info("Querying serviceworker")
    Navigator.serviceWorker.foreach { sw =>
      auth match {
        case Authentication.Verified(_, _, token) =>
          val activeServiceworker =  sw.controller
          if(activeServiceworker != null) {
            scribe.info("Sending auth to serviceworker")
            activeServiceworker.postMessage((AuthMessage(token): WorkerMessage).asJson.noSpaces);
          } else scribe.info("No serviceworker found")
        case _ =>
          scribe.info("No token available")
      }
    }
  }

}
