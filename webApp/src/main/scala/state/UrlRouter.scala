package wust.webApp.state

import monix.execution.Scheduler
import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch.dom.dsl._
import rx._

case class UrlRoute(search: Option[String], hash: Option[String])

object UrlRouter {
  private def locationRoute = {
    val locationSearch = Option(window.location.search).map(_.drop(1)).filterNot(_.isEmpty)
    val locationHash = Option(window.location.hash).map(_.drop(1)).filterNot(_.isEmpty)
    UrlRoute(search = locationSearch, hash = locationHash)
  }

  def variable(implicit ctx: Ctx.Owner, ec: Scheduler): Var[UrlRoute] = {
    val route = Var[UrlRoute](locationRoute)
    route.foreach { route =>
      if (route != locationRoute) {
        val search = route.search.getOrElse("/") // not empty string, otherwise the old search part is not replaced in the url.
        val hash = route.hash.fold("")("#" + _)
        // instead of setting window.hash_=, pushState does not emit a hashchange event
        window.history.pushState(null, null, search + hash)
      }
    }

    // drop initial hash change on site load
    events.window.onHashChange.foreach { ev: HashChangeEvent =>
      val current = locationRoute
      if (route.now != current)
        route() = current
    }

    route
  }
}
