package wust.webApp.state

import wust.ids.{NodeId, View}
import rx._

// when traversing a tree in the dom, we always have a current parent and a chain of ancestors. Needed to check cycles or operate on the parents in the views.
final case class TraverseState(
  parentId: NodeId,
  tail: List[NodeId] = Nil
) {
  def contains(nodeId: NodeId): Boolean = parentId == nodeId || tail.contains(nodeId)
  def step(nodeId: NodeId): TraverseState = TraverseState(nodeId, parentId :: tail)
}

// a class for representing a preference to focus something in a certain view. e.g. used for configuring the right sidebar
final case class FocusPreference(
  nodeId: NodeId,
  view: Option[View.Visible] = None
)

// a class representing the currently focused configuration of a view. a view can be shown in different contexts: in the main view, or in the right sidebar, or within a node card (e.g. listview)
//TODO reprensent context as ADT FocusContext: Global, Sidebar, Nodecard with contextparentid, isnested and the actions derived from it
// or make focusstate an ADT because most properties are dervied from the context
final case class FocusState(
  view: View.Visible, // how to render the current focus
  contextParentId: NodeId, // the parent of the current context: in the main view it is page (== focusedId), in the right sidebar it is rightSidebarNode (== focusedId), within a card it is either page or rightsidebarNode depending where the card is shown (!= focusedId).
  focusedId: NodeId, // the currently focused id that is the node the view should render
  isNested: Boolean, // whether the view is nested inside another: only the mainview is not nested.
  changeViewAction: View => Unit, // change the view
  contextParentIdAction: NodeId => Unit, // change the contextParentId
  itemIsFocused: NodeId => Rx[Boolean],
  onItemSingleClick: FocusPreference => Unit, // = nodeId => GlobalState.rightSidebarNode() = Some(nodeId),
  onItemDoubleClick: NodeId => Unit, // = GlobalState.focus(_),
) 
