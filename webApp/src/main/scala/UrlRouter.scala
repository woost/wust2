package wust.webApp

import monix.execution.Scheduler
import org.scalajs.dom.raw.HashChangeEvent
import org.scalajs.dom.window
import outwatch.dom.dsl._
import rx._

object UrlRouter {
  private def locationHash = Option(window.location.hash).map(_.drop(1)).filterNot(_.isEmpty)

  def variable(implicit ctx: Ctx.Owner, ec: Scheduler): Var[Option[String]] = {
    val hash = Var[Option[String]](locationHash)
    hash.foreach { (hash: Option[String]) =>
      if (locationHash != hash) {
        val current = hash.getOrElse("")
        // instead of setting window.hash_=, pushState does not emit a hashchange event
        window.history.pushState(null, null, s"#$current")
      }
    }

    // drop initial hash change on site load
    events.window.onHashChange.foreach { ev: HashChangeEvent =>
      val current = locationHash
      if (hash.now != current)
        hash() = current
    }

    hash
  }
}
