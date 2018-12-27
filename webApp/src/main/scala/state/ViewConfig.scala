package wust.webApp.state

import wust.api.Authentication
import wust.graph._
import wust.ids.NodeId
import wust.webApp.parsers.{ViewConfigParser, ViewConfigWriter}

case class ShareOptions(title: String, text: String, url: String)

case class ViewConfig(view: Option[View], pageChange: PageChange, redirectTo: Option[View], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def showViewWithRedirect(newView: View): ViewConfig = {
    copy(view = Some(newView), redirectTo = view.filter(canRedirectTo) orElse redirectTo)
  }

  def redirect: ViewConfig = redirectTo.fold(this)(view => copy(view = Some(view), redirectTo = None))

  def focusView(page: Page, view:View, needsGet: Boolean = true): ViewConfig = {
    focus(page, Some(view), needsGet)
  }
  def focus(page: Page, view:Option[View] = None, needsGet: Boolean = true): ViewConfig = {
    copy(pageChange = PageChange(page, needsGet = needsGet), view = view, redirectTo = None)
  }
}
object ViewConfig {
  val default = ViewConfig(view = None, PageChange(Page.empty), None, None, None)

  def fromUrlHash(hash: String): ViewConfig = ViewConfigParser.parse(hash)

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
