package wust.webApp.state

import wust.api.Authentication
import wust.graph._
import wust.ids.NodeId
import wust.webApp.parsers.{ViewConfigParser, ViewConfigWriter}

case class ShareOptions(title: String, text: String, url: String)

case class ViewConfig(view: View, pageChange: PageChange, redirectTo: Option[View], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def showViewWithRedirect(newView: View): ViewConfig =
    copy(view = newView, redirectTo = Some(view).filter(canRedirectTo) orElse redirectTo)
  def redirect: ViewConfig = redirectTo.fold(this)(view => copy(view = view, redirectTo = None))

  def focus(page: Page, needsGet: Boolean = true): ViewConfig = {
    val nextView = if (view.isContent) view else View.default
    focusView(page, nextView, needsGet)
  }

  def focusView(page: Page, view:View, needsGet: Boolean = true): ViewConfig = {
    copy(pageChange = PageChange(page, needsGet = needsGet), view = view)
  }
}
object ViewConfig {
  val default = ViewConfig(View.default, PageChange(Page.empty), None, None, None)

  def fromUrlHash(hash: String): ViewConfig = ViewConfigParser.parse(hash)

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
