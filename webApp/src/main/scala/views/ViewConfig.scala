package wust.webApp.views

import fastparse.core.Parsed
import wust.graph._

case class ViewConfig(view: View, page: Page)
object ViewConfig {
  val default = ViewConfig(View.default, Page.empty)

  def fromUrlHash(hash: Option[String]): ViewConfig = hash.flatMap { str =>
    ViewConfigParser.viewConfig.parse(str) match {
      case Parsed.Success(url, _) => Some(url)
      case failure: Parsed.Failure[_,_] =>
        scribe.warn(s"Failed to parse url from hash '$str' at ${failure.msg}")
        None
    }
  }.getOrElse(default)

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
