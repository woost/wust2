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

case class UrlHash(url: String, hash: Option[String]) {
  def toUrl = url + hash.map("#" + _).getOrElse("")
}
object UrlHash {
  def apply(url: String): UrlHash = {
    val parts: Array[String] = url.split("#")
    val baseUrl = parts.init.fold("")(_ + _)
    val hash = parts.tail.lastOption
    UrlHash(baseUrl, hash)
  }
}

object UrlRouter {
  def route[T: Routeable](page: Var[T])(implicit ctx: Ctx.Owner) {
    val routeable = implicitly[Routeable[T]]

    page.foreach { page =>
      val newHash = routeable.toRoute(page)
      val current = UrlHash(window.location.href)

      if (current.hash != Some(newHash))
        window.location.href = current.copy(hash = Some(newHash)).toUrl
    }

    window.onhashchange = { ev: HashChangeEvent =>
      val current = UrlHash(ev.newURL)
      val newPage = current.hash.flatMap(routeable.fromRoute.lift).getOrElse {
        val hash = routeable.toRoute(routeable.default)
        window.location.href = current.copy(hash = Some(hash)).toUrl
        routeable.default
      }

      if (page.now != newPage)
        page() = newPage
    }
  }
}
