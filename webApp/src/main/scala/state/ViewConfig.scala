package wust.webApp.state

import wust.api.Authentication
import wust.graph._
import wust.ids.View
import wust.webApp.parsers.{UrlConfigParser, UrlConfigWriter}

// ViewConfig and UrlConfig are the configurations driving our application ui.
// For example, it contains the page and view that should be displayed.
// The UrlConfig is the raw configuration derived from the url.
// The ViewConfig is the sanitized configuration for the views. For example, the
// page in viewconfig is always consistent with the graph, i.e., it is contained in the graph
// or else it will be none.

//TODO: get rid of pagechange, currently needed to know whether we should get a new graph on page change or not.
// we only know whether we need this when changing the page. But it feels like mixing data and commands.

case class ShareOptions(title: String, text: String, url: String)
case class PageChange(page: Page, needsGet: Boolean = true)

case class UrlConfig(view: Option[View], pageChange: PageChange, redirectTo: Option[View], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def showViewWithRedirect(newView: View): UrlConfig = copy(view = Some(newView), redirectTo = view.filter(canRedirectTo) orElse redirectTo)

  def redirect: UrlConfig = copy(view = redirectTo, redirectTo = None)

  @inline def focus(view: Option[View]): UrlConfig = copy(view = view, redirectTo = None)
  @inline def focus(view: View): UrlConfig = copy(view = Some(view), redirectTo = None)
  @inline def focus(page: Page, view: View): UrlConfig = focus(page, Some(view))
  @inline def focus(page: Page, view: View, needsGet: Boolean): UrlConfig = focus(page, Some(view), needsGet)
  def focus(page: Page, view: Option[View] = None, needsGet: Boolean = true): UrlConfig = copy(pageChange = PageChange(page, needsGet = needsGet), view = view, redirectTo = None)
}
object UrlConfig {
  val default = UrlConfig(view = None, PageChange(Page.empty), None, None, None)

  def fromUrlHash(hash: String): UrlConfig = UrlConfigParser.parse(hash)

  def toUrlHash(config: UrlConfig): String = UrlConfigWriter.write(config)
}

case class ViewConfig(view: View.Visible, page: Page)
