package wust.webApp.jsdom

import io.circe.{Decoder, Encoder, Json}
import org.scalajs.dom.experimental.serviceworkers
import org.scalajs.dom.window
import outwatch.reactive._
import wust.api.Authentication
import wust.webUtil.outwatchHelpers._

import scala.util.{Failure, Success, Try}
import wust.facades.segment.Segment

object ServiceWorker {
  // TODO: need var to have active serviceworker. why is navigator.serviceWorker.controller null on initial page load where sw installed?! should be same as registration.active:
  // https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerContainer/controller
  private var activeServiceworker: Option[serviceworkers.ServiceWorker] = None

  // returns an observable that notifies whenever a new serviceworker is registered and activated
  def register(location: String): SourceStream[Unit] = {
    val subject = SinkSourceHandler.publish[Unit]

    Navigator.serviceWorker.foreach { sw =>
      // Use the window load event to keep the page load performant
      window.addEventListener("load", (_: Any) => {
        Try(sw.register(location)).toEither match {
          case Right(registered) => registered.toFuture.onComplete {
            case Success(registration) =>
              scribe.info("SW successfully registered!")
              if (registration.active != null) {
                scribe.info("SW is already active")
                activeServiceworker = Option(registration.active)
                subject.onNext(())
              }
              registration.onupdatefound = { event =>
                registration.installing.onstatechange = { event =>
                  if (registration.active != null && activeServiceworker.forall(_ != registration.active)) {
                    scribe.info("SW newly activated")
                    activeServiceworker = Option(registration.active)
                    subject.onNext(())
                  }
                }
              }
            case Failure(registrationError) =>
              scribe.warn("SW registration failed: ", registrationError)
              Segment.trackError("Serviceworker registeration failed", registrationError.getMessage())
              subject.onError(registrationError)
          }
          case Left(e) =>
            scribe.error("SW could not register:", e)
            Segment.trackError("Serviceworker could not register", e.getMessage())
            subject.onError(e)
        }
      })

      // not needed, because our updates should trigger automatically when core-version is offline...
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
    subject.recoverOption{
      case e: Throwable =>
        scribe.debug("SW could not register:", e)
        None
    }
  }

  sealed trait WorkerMessage
  object WorkerMessage {
    import io.circe.generic.extras.semiauto._
    import wust.api.serialize.Circe._ // das gibt die die config mit `{"type": "SubClassName", .... }`

    implicit val serviceWorkerMessageEncoder: Encoder[WorkerMessage] = deriveConfiguredEncoder[WorkerMessage]
    implicit val serviceWorkerMessageDecoder: Decoder[WorkerMessage] = deriveConfiguredDecoder[WorkerMessage]
  }

  final case class AuthMessage(token: Authentication.Token) extends WorkerMessage
  final case object DeAuthMessage extends WorkerMessage
  final case class Message(message: String) extends WorkerMessage


  def sendAuth(auth: Authentication): Unit = {
    import io.circe.syntax._

    activeServiceworker match {

      case Some(activeServiceworker) => auth match {
        case Authentication.Verified(_, _, token) =>
          scribe.info("Sending auth to serviceworker")
          activeServiceworker.postMessage((AuthMessage(token): WorkerMessage).asJson.noSpaces);
        case _ =>
          scribe.info("Sending de-auth to serviceworker")
          activeServiceworker.postMessage((DeAuthMessage: WorkerMessage).asJson.noSpaces);
      }

      case None => scribe.info("No serviceworker found for auth sync.")
    }
  }

}
