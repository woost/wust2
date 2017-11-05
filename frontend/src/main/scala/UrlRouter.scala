package wust.frontend

import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch.dom._
import rxscalajs.Observable

object UrlRouter {
  private def locationHash = Option(window.location.hash).map(_.drop(1)).filterNot(_.isEmpty)

  val variable: Handler[Option[String]] = {
    val handler: Handler[Option[String]] = createHandler[Option[String]](locationHash).unsafeRunSync()
    handler { hash =>
      if (locationHash != hash) {
        val current = hash.getOrElse("")
        // instead of setting window.hash_=, pushState does not emit a hashchange event
        window.history.pushState(null, null, s"#$current")
      }
    }

    val observable = Observable.create[Option[String]] { observer =>
      window.onhashchange = { ev: HashChangeEvent =>
        observer.next(locationHash)
      }
    }.distinctUntilChanged

    (handler <-- observable).unsafeRunSync()
    handler
  }
}
