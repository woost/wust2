package wust.webApp.views

import fastparse.core.Parsed
import wust.graph._

case class ViewConfig(view: View, page: Page)
object ViewConfig {
  val default = ViewConfig(View.default, Page.empty)

  def fromUrlHash(hash: String): ViewConfig = {
    ViewConfigParser.viewConfig.parse(hash) match {
      case Parsed.Success(url, _) => url
      case failure: Parsed.Failure[_,_] =>
        val errMsg = s"Failed to parse url from hash '$hash' at ${failure.msg}"
        ViewConfig(new ErrorView(errMsg), Page.empty)
    }
  }

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
