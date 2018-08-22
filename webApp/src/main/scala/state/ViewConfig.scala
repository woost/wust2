package wust.webApp.state

import fastparse.core.Parsed
import wust.graph._
import wust.webApp.parsers.{ViewConfigParser, ViewConfigWriter}

case class ShareOptions(title: String, text: String, url: String)

case class ViewConfig(view: View, page: Page, redirectTo: Option[View], shareOptions: Option[ShareOptions]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def showViewWithRedirect(newView: View): ViewConfig =
    copy(view = newView, redirectTo = Some(view).filter(canRedirectTo) orElse redirectTo)
  def redirect: ViewConfig = redirectTo.fold(this)(view => copy(view = view, redirectTo = None))
}
object ViewConfig {
  val default = ViewConfig(View.default, Page.empty, None, None)

  def fromUrlHash(hash: String): ViewConfig = {
    ViewConfigParser.viewConfig.parse(hash) match {
      case Parsed.Success(url, _) => url
      case failure: Parsed.Failure[_, _] =>
        val errMsg = s"Failed to parse url from hash '$hash' at ${failure.msg}"
        ViewConfig(View.Error(errMsg), Page.empty, None, None)
    }
  }

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
