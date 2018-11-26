package wust.webApp.state

import wust.graph._
import wust.ids.NodeId
import wust.webApp.parsers.{ViewConfigParser, ViewConfigWriter}

case class ShareOptions(title: String, text: String, url: String)

case class ViewConfig(view: View, pageChange: PageChange, redirectTo: Option[View], shareOptions: Option[ShareOptions]) {
  private val canRedirectTo: View => Boolean = {
    case View.Login | View.Signup => false
    case _ => true
  }

  def showViewWithRedirect(newView: View): ViewConfig =
    copy(view = newView, redirectTo = Some(view).filter(canRedirectTo) orElse redirectTo)
  def redirect: ViewConfig = redirectTo.fold(this)(view => copy(view = view, redirectTo = None))


  def focusNode(nodeId: NodeId, needsGet: Boolean = true, focusView:View = view): ViewConfig = {
    val nextView = if (view.isContent) focusView else View.default
    val nextPage = Page(nodeId)
    copy(pageChange = PageChange(nextPage, needsGet = needsGet), view = nextView)
  }

}
object ViewConfig {
  val default = ViewConfig(View.default, PageChange(Page.empty), None, None)

  def fromUrlHash(hash: String): ViewConfig = ViewConfigParser.parse(hash)

  def toUrlHash(config: ViewConfig): String = ViewConfigWriter.write(config)
}
