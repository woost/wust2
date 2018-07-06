package wust.webApp

import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom.experimental.serviceworkers.ServiceWorker
import org.scalajs.dom.{experimental, window, _}
import wust.webApp.outwatchHelpers._

import scala.scalajs.js
import scala.util.{Failure, Success}

object ServiceWorker {

  // Use the window load event to keep the page load performant
  def register(): Observable[Unit] = {
    val subject = PublishSubject[Unit]()
    Navigator.serviceWorker.foreach { sw =>
      window.addEventListener(
        "load",
        (_: Any) => {
          sw.register("sw.js").toFuture.onComplete {
            case Success(registration) =>
              console.log("SW registered: ", registration)
              registration.onupdatefound = { event =>
                val installingWorker = registration.installing

                installingWorker.onstatechange = { event =>
                  console.log("Update of SW found", installingWorker, Navigator.serviceWorker.get.controller)
                  if (installingWorker.state == "installed"
                    && (Navigator.serviceWorker.get.controller
                        .asInstanceOf[js.UndefOr[ServiceWorker]]
                        .isDefined)) {
                      console.log("New SW installed, can update.")
                      subject.onNext(())
                  }
                }
              }
            case Failure(registrationError) =>
              scribe.warn("SW registration failed: ", registrationError)
              subject.onError(registrationError)
          }
        }
      )
    }

    subject
  }
}
