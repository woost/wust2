package wust.webApp.views

import acyclic.skipped // file is allowed in dependency cycle
import fastparse.core.Parsed
import wust.graph._
import wust.webApp.parsers.{ViewConfigParser, ViewConfigWriter}

case class ShareOptions(title: String, text: String, url: String)

case class ViewConfig(view: View, page: Page, prevView: Option[View], shareOptions: Option[ShareOptions]) {
  def overlayView(newView: View): ViewConfig =
    copy(view = newView, prevView = Some(view).filter(_.isContent) orElse prevView)
  def noOverlayView: ViewConfig = prevView.fold(this)(view => copy(view = view, prevView = None))
}
object ViewConfig {
  def fromUrlHash(hash: String): ViewConfig = {
    ViewConfigParser.viewConfig.parse(hash) match {
      case Parsed.Success(url, _) => url
      case failure: Parsed.Failure[_, _] =>
        val errMsg = s"Failed to parse url from hash '$hash' at ${failure.msg}"
        ViewConfig(new ErrorView(errMsg), Page.empty, None, None)
    }
  }

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
