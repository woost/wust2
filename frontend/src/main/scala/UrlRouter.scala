package wust.frontend

import rx._
import scalajs.js
import org.scalajs.dom.window
import org.scalajs.dom.raw.HashChangeEvent

trait Routeable[T] {
  val default: T
  val fromRoute: PartialFunction[String, T]
  def toRoute(routeable: T): String
}

object UrlRouter {
  def route[T: Routeable](page: Var[T])(implicit ctx: Ctx.Owner) {
    val routeable = implicitly[Routeable[T]]

    page.foreach { page =>
      val newHash = routeable.toRoute(page)
      window.location.hash = newHash
    }

    window.onhashchange = { ev: HashChangeEvent =>
      val current = Option(window.location.hash).filterNot(_.isEmpty).map(_.drop(1))
      val newPage = current.flatMap(routeable.fromRoute.lift).getOrElse {
        val hash = routeable.toRoute(routeable.default)
        window.location.hash = hash
        routeable.default
      }

      if (page.now != newPage)
        page() = newPage
    }
  }
}
