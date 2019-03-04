package wust.webApp.state

import wust.graph.Page
import wust.ids.{NodeId, View}

case class FocusPreference(
  nodeId: NodeId,
  view: Option[View] = None
)

case class FocusState(
  view: View.Visible,
  parentId: NodeId,
  focusedId: NodeId,
  isNested: Boolean,
  viewAction: View => Unit,
  parentIdAction: NodeId => Unit
)

object FocusState {
  def fromGlobal(state: GlobalState, viewConfig: ViewConfig): Option[FocusState] = viewConfig.page.parentId.map { parentId =>
    FocusState(viewConfig.view, parentId, parentId, isNested = false, view => state.urlConfig.update(_.focus(view)), nodeId => state.urlConfig.update(_.focus(Page(nodeId))))
  }
}
