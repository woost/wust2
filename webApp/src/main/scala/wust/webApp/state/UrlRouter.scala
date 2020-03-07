package wust.webApp.state

import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch.dsl._
import rx._
import wust.webApp.parsers.UrlConfigWriter

object UrlRouter {
  private def locationRoute = {
    val locationSearch = Option(window.location.search).map(_.drop(1)).filterNot(_.isEmpty)
    val locationHash = Option(window.location.hash).map(_.drop(1)).filterNot(_.isEmpty)
    UrlRoute(search = locationSearch, hash = locationHash)
  }

  def variable()(implicit ctx: Ctx.Owner): Var[UrlRoute] = {
    val route = Var[UrlRoute](locationRoute)
    route.foreach { route =>
      if (route != locationRoute) {
        // instead of setting window.hash_=, pushState does not emit a hashchange event
        window.history.pushState(null, null, UrlConfigWriter.urlRouteToString(route))
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
