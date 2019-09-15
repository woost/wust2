package wust.webApp.state

import acyclic.file
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{View, NodeId, ViewName}

sealed trait ViewIntent
object ViewIntent {
  final case class ByName(name: ViewName) extends ViewIntent
  final case class ByView(view: View) extends ViewIntent
}

final case class UrlConfig(view: Option[ViewIntent], pageChange: PageChange, redirectTo: Option[ViewIntent], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token], focusId: Option[NodeId]) {
  private val canRedirectTo: ViewName => Boolean = {
    // case View.Login | View.Signup => false //FIXME
    case _ => true
  }

  def focusWithRedirect(newView: ViewName): UrlConfig = copy(view = Some(newView), redirectTo = view.filter(canRedirectTo) orElse redirectTo)

  def redirect: UrlConfig = copy(view = redirectTo, redirectTo = None)

  @inline def focus(view: ViewIntent): UrlConfig = focus(Some(view))
  @inline def focus(page: Page, view: ViewIntent): UrlConfig = focus(page, Some(view))
  @inline def focus(view: Option[ViewIntent]): UrlConfig = copy(view = view, redirectTo = None, focusId = None)
  @inline def focus(page: Page, view: ViewIntent, needsGet: Boolean): UrlConfig = focus(page, Some(view), needsGet)
  def focus(page: Page, view: Option[ViewIntent] = None, needsGet: Boolean = true): UrlConfig = copy(pageChange = PageChange(page, needsGet = needsGet), view = view, redirectTo = None, focusId = None)
}

object UrlConfig {
  val default = UrlConfig(view = None, pageChange = PageChange(Page.empty), redirectTo = None, shareOptions = None, invitation = None, focusId = None)
}
