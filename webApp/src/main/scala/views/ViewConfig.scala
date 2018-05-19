package wust.webApp.views

import fastparse.core.Parsed
import wust.graph._

case class ViewConfig(view: View, page: Page, prevView: Option[View]) {
  def overlayView(newView: View): ViewConfig = copy(view = newView, prevView = Some(view).filter(_.isContent) orElse prevView)
  def noOverlayView: ViewConfig = prevView.fold(this)(view => copy(view = view, prevView = None))
}
object ViewConfig {
  val default = ViewConfig(View.default, Page.empty, None)

  def fromUrlHash(hash: String): ViewConfig = {
    ViewConfigParser.viewConfig.parse(hash) match {
      case Parsed.Success(url, _) => url
      case failure: Parsed.Failure[_,_] =>
        val errMsg = s"Failed to parse url from hash '$hash' at ${failure.msg}"
        ViewConfig(new ErrorView(errMsg), Page.empty, None)
    }
  }

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
