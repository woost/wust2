package wust.frontend

import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import rx._

object UrlRouter {
  private def locationHash = Option(window.location.hash).map(_.drop(1)).filterNot(_.isEmpty)

  val variable: RxVar[Option[String], Option[String]] = {
    val hash = RxVar[Option[String]](locationHash)
    hash.foreach { hash =>
      if (locationHash != hash) {
        val current = hash.getOrElse("")
        // instead of setting window.hash_=, pushState does not emit a hashchange event
        window.history.pushState(null, null, s"#$current")
      }
    }

    window.onhashchange = { ev: HashChangeEvent =>
      val current = locationHash
      if (hash.now != current)
        hash() = current
    }

    hash
  }
}


//object UrlRouter {
//  private def locationHash = Option(window.location.hash).map(_.drop(1)).filterNot(_.isEmpty)
//
//  val variable: Handler[Option[String]] = {
//    val handler: Handler[Option[String]] = createHandler[Option[String]]()
//    handler { hash =>
//      if (locationHash != hash) {
//        val current = hash.getOrElse("")
//        // instead of setting window.hash_=, pushState does not emit a hashchange event
//        window.history.pushState(null, null, s"#$current")
//      }
//    }
//
//    val observable = Observable.create[Option[String]] { observer =>
//      window.onhashchange = { ev: HashChangeEvent =>
//        observer.next(locationHash)
//      }
//    }.distinctUntilChanged
//
//    handler <-- observable
//    handler
//  }
//}
