package wust.webApp.jsdom

import wust.facades.googleanalytics.Analytics
import io.circe.{Decoder, Encoder, Json}
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.window
import wust.webUtil.outwatchHelpers._
import wust.api.Authentication

import scala.util.{Failure, Success, Try}

object ServiceWorker {

  def register(location: String): Observable[Unit] = {
    val subject = PublishSubject[Unit]()

    Navigator.serviceWorker.foreach { sw =>
      // Use the window load event to keep the page load performant
      window.addEventListener("load", (_: Any) => {
        Try(sw.register(location)).toEither match {
          case Right(registered) => registered.toFuture.onComplete {
            case Success(registration) =>
              scribe.info(s"SW successfully registered")
              registration.onupdatefound = { event =>
                val installingWorker = registration.installing
                installingWorker.onstatechange = { event =>
                  val activeServiceworker = sw.controller
                  if (installingWorker.state == "installed" && activeServiceworker != null) {
                    scribe.info("New SW installed, can update.")
                    subject.onNext(())
                  }
                }
              }
            case Failure(registrationError) =>
              scribe.warn("SW registration failed: ", registrationError)
              subject.onError(registrationError)
          }
          case Left(e) =>
            scribe.error("SW could not register:", e)
            subject.onError(e)
        }
      })

      // // when a new serviceworker took over, reload the page
      // // Approach #2 from
      // // https://redfin.engineering/how-to-fix-the-refresh-button-when-using-service-workers-a8e27af6df68
      // var refreshing = false;
      // sw.addEventListener("controllerchange", { (_:Any) =>
      //   if (!refreshing) {
      //     refreshing = true;
      //     window.location.reload();
      //   }
      // })
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

  sealed trait WorkerMessage
  final case class AuthMessage(token: Authentication.Token) extends WorkerMessage
  final case object DeAuthMessage extends WorkerMessage
  final case class Message(message: String) extends WorkerMessage

  def sendAuth(auth: Authentication): Unit = {
    import io.circe.generic.extras.semiauto._
    import io.circe.syntax._
    import wust.api.serialize.Circe._ // das gibt die die config mit `{"type": "SubClassName", .... }`

    implicit val serviceWorkerMessageEncoder: Encoder[WorkerMessage] = deriveEncoder[WorkerMessage]
    implicit val serviceWorkerMessageDecoder: Decoder[WorkerMessage] = deriveDecoder[WorkerMessage]

    scribe.info("Querying serviceworker for auth-sync")
    Navigator.serviceWorker.foreach { sw =>
      val activeServiceworker = sw.controller
      if (activeServiceworker != null) auth match {
        case Authentication.Verified(_, _, token) =>
          scribe.info("Sending auth to serviceworker")
          activeServiceworker.postMessage((AuthMessage(token): WorkerMessage).asJson.noSpaces);
        case _ =>
          scribe.info("Sending de-auth to serviceworker")
          activeServiceworker.postMessage((DeAuthMessage: WorkerMessage).asJson.noSpaces);
      } else {
        scribe.info("No serviceworker found")
      }
    }
  }

}
