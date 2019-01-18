package wust.webApp.state

import wust.api.Authentication
import wust.graph._
import wust.webApp.parsers.{ViewConfigParser, ViewConfigWriter}

case class ShareOptions(title: String, text: String, url: String)

//TODO: get rid of pagechange, currently needed to know whether we should get a new graph on page change or not.
// we only know whether we need this when changing the page. But it feels like mixing data and commands.
case class PageChange(page: Page, needsGet: Boolean = true)

case class ViewConfig(view: Option[View], pageChange: PageChange, redirectTo: Option[View], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token], prevPage: Option[Page]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def showViewWithRedirect(newView: View): ViewConfig = {
    copy(view = Some(newView), redirectTo = view.filter(canRedirectTo) orElse redirectTo)
  }

  def redirect: ViewConfig = redirectTo.fold(this)(view => copy(view = Some(view), redirectTo = None))

  @inline def goBackOrEmptyPage: ViewConfig = {
    prevPage match {
      case Some(prevPage) => copy(pageChange = PageChange(prevPage), prevPage = None)
      case None => copy(pageChange = PageChange(Page.empty))
    }
  }

  @inline def focusView(view:View): ViewConfig = copy(view = Some(view), redirectTo = None)
  @inline def focusView(page: Page, view:View, needsGet: Boolean = true): ViewConfig = focus(page, Some(view), needsGet)

  /// Sets current page
  // @param[in] needsGet Whether to pull graph
  def focus(page: Page, view:Option[View] = None, needsGet: Boolean = true): ViewConfig = {
    copy(pageChange = PageChange(page, needsGet = needsGet), view = view, redirectTo = None, prevPage = Some(page))
  }
}
object ViewConfig {
  val default = ViewConfig(view = None, PageChange(Page.empty), None, None, None, None)

  def fromUrlHash(hash: String): ViewConfig = ViewConfigParser.parse(hash)

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
