package wust.webApp.state

import acyclic.file
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{NodeId, View}

sealed trait PresentationMode
object PresentationMode {
  case object ContentOnly extends PresentationMode
  case object Doodle extends PresentationMode
  case object Full extends PresentationMode

  def toString(p: PresentationMode): Option[String] = Some(p) collect {
    case ContentOnly => "content"
    case Doodle => "doodle"
  }

  def fromString(s: String): Option[PresentationMode] = Some(s.toLowerCase) collect {
    case "content" => ContentOnly
    case "doodle" => Doodle
    case "full" => Full
  }
}

sealed trait InfoContent
object InfoContent {
  case object PaymentSucceeded extends InfoContent

  def toString(p: InfoContent): Option[String] = Some(p) collect {
    case PaymentSucceeded => "plan-success"
  }

  def fromString(s: String): Option[InfoContent] = Some(s.toLowerCase) collect {
    case "plan-success" => PaymentSucceeded
  }
}

final case class UrlConfig(view: Option[View], pageChange: PageChange, redirectTo: Option[View], shareOptions: Option[ShareOptions], invitation: Option[Authentication.Token], focusId: Option[NodeId], mode: PresentationMode, info: Option[InfoContent]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def focusWithRedirect(newView: View): UrlConfig = copy(view = Some(newView), redirectTo = view.filter(canRedirectTo) orElse redirectTo)

  def redirect: UrlConfig = copy(view = redirectTo, redirectTo = None)

  @inline def focus(view: View): UrlConfig = focus(Some(view))
  @inline def focus(page: Page, view: View): UrlConfig = focus(page, Some(view))
  @inline def focus(view: Option[View]): UrlConfig = copy(view = view, redirectTo = None, focusId = None)
  @inline def focus(page: Page, view: View, needsGet: Boolean): UrlConfig = focus(page, Some(view), needsGet)
  def focus(page: Page, view: Option[View] = None, needsGet: Boolean = true): UrlConfig = copy(pageChange = PageChange(page, needsGet = needsGet), view = view, redirectTo = None, focusId = None)

}

object UrlConfig {
  val default = UrlConfig(view = None, pageChange = PageChange(Page.empty), redirectTo = None, shareOptions = None, invitation = None, focusId = None, mode = PresentationMode.Full, info = None)
}
