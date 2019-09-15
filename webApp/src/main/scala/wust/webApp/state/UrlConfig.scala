package wust.webApp.state

import acyclic.file
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{View, NodeId, ViewName}

final case class UrlConfig(view: Option[ViewName], pageChange: PageChange, systemView: Option[View.System], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token], focusId: Option[NodeId]) {
  def clearFocus: UrlConfig = copy(view = None, pageChange = PageChange(Page.empty))
  def focusOverride(systemView: View.System): UrlConfig = copy(view = None, pageChange = PageChange(Page.empty), systemView = Some(systemView))
  def focus(systemView: View.System): UrlConfig = copy(systemView = Some(systemView))
  def unfocusSystem: UrlConfig = copy(systemView = None)

  @inline def focus(view: ViewName): UrlConfig = focus(Some(view))
  @inline def focus(page: Page, view: ViewName): UrlConfig = focus(page, Some(view))
  @inline def focus(view: Option[ViewName]): UrlConfig = copy(view = view, systemView = None, focusId = None)
  @inline def focus(page: Page, view: ViewName, needsGet: Boolean): UrlConfig = focus(page, Some(view), needsGet)
  def focus(page: Page, view: Option[ViewName] = None, needsGet: Boolean = true): UrlConfig = copy(pageChange = PageChange(page, needsGet = needsGet), view = view, systemView = None, focusId = None)
}

object UrlConfig {
  val default = UrlConfig(view = None, pageChange = PageChange(Page.empty), systemView = None, shareOptions = None, invitation = None, focusId = None)
}
