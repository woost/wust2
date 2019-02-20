package wust.webApp.views

import fontAwesome.freeSolid
import googleAnalytics.Analytics
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.util.RichBoolean
import wust.webApp.dragdrop.{DragItem, _}
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.SharedViewElements._
import wust.webApp.{Icons, ItemProperties, Ownable}

import scala.collection.breakOut

object RightSidebar {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val toggleVar = state.rightSidebarNode.zoom(_.isDefined)((node, enabled) => if (enabled) node else None)

    GenericSidebar.right(
      toggleVar,
      config = Ownable { implicit ctx => GenericSidebar.Config(
        openModifier = content(state, state.rightSidebarNode, state.rightSidebarNode() = _)
      )}
    )
  }

  def content(state: GlobalState, focusedNodeId: NodeId, parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner):VDomModifier = content(state, Var(Some(focusedNodeId)), parentIdAction)
  // TODO rewrite to rely on static focusid
  private def content(state: GlobalState, focusedNodeId: Rx[Option[NodeId]], parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    val nodeStyle = focusedNodeId.map(PageStyle.ofNode)
    val boxMod = VDomModifier(
      borderRadius := "3px",
      backgroundColor <-- nodeStyle.map(_.bgLightColor),
    )

    VDomModifier(
      div(
        width := "20px",
        cls := "fa-fw", freeSolid.faAngleDoubleRight,
        cursor.pointer,
        onClick(None).foreach(parentIdAction),
        backgroundColor := CommonStyles.sidebarBgColor,
      ),
      div(
        color.black,
        height := "100%",
        Styles.flex,
        flexDirection.column,
        justifyContent.spaceBetween,
        overflowY.auto,

        nodeDetailsMenu(state, focusedNodeId).apply(
          minHeight := "300px",
          flex := "1",
          margin := "0px 5px 0px 5px",
          padding := "3px",
          boxMod,
          overflowY.auto,

        ),
        viewContent(state, focusedNodeId, parentIdAction, boxMod).apply(
          flexShrink := 0,
          margin := "5px",
        )
      )
    )
  }
  private def viewContent(state: GlobalState, focusedNodeId: Rx[Option[NodeId]], parentIdAction: Option[NodeId] => Unit, viewModifier: VDomModifier)(implicit ctx: Ctx.Owner) = {
    div(
      focusedNodeId.map(_.map { nodeId =>
        val initialView = state.graph.now.nodesByIdGet(nodeId).fold[View.Visible](View.Empty)(ViewHeuristic.bestView(state.graph.now, _))
        val viewVar = Var[View.Visible](initialView)
        def viewAction(view: View): Unit = viewVar() = ViewHeuristic.visibleView(state.graph.now, nodeId, view)

        VDomModifier(
          div(
            Styles.flex,
            justifyContent.spaceBetween,
            alignItems.center,
            div(
              padding := "3px",
              color.white,
              Icons.zoom,
              cursor.pointer,
              onClick.foreach { state.urlConfig.update(_.focus(Page(nodeId), viewVar.now)) }
            ),
            PageHeader.viewSwitcher(state, nodeId, viewVar, viewAction),
          ),
          Rx {
            val view = viewVar()
            ViewRender(state, FocusState(view, nodeId, nodeId, isNested = true, viewAction, nodeId => parentIdAction(Some(nodeId))), view).apply(
              width := "100%",
              height := "500px",
              viewModifier
            )
          }
        )
      })
    )
  }

  private def nodeDetailsMenu(state: GlobalState, focusedNodeId: Rx[Option[NodeId]])(implicit ctx: Ctx.Owner) = {
    val editMode = Var(false)

    div(
      Rx {
        focusedNodeId().flatMap { nodeId =>
          state.graph().nodesByIdGet(nodeId).map { node =>
            VDomModifier(
              div(
                Styles.flex,
                alignItems.flexStart,
                Components.nodeCardEditable(state, node, editMode).apply(width := "100%", marginRight := "3px"),
                div(
                  Icons.edit,
                  padding := "5px",
                  cursor.pointer,
                  onClick.stopPropagation(true) --> editMode,
                )
              ),
              nodeProperties(state, state.graph(), node)
            )
          }
        }
      }
    )
  }

  private def nodeProperties(state: GlobalState, graph: Graph, node: Node)(implicit ctx: Ctx.Owner) = {
    val nodeIdx = graph.idToIdxOrThrow(node.id)

    val propertySingle = PropertyData.Single(graph, nodeIdx)

    val commonPropMod = VDomModifier(
      width := "100%",
      marginTop := "10px",
      paddingLeft := "3px",
    )

    def renderSplit(left: VDomModifier, right: VDomModifier) = div(
      Styles.flex,
      justifyContent.spaceBetween,
      div(
        width := "100%",
        left
      ),
      div(
        Styles.flex,
        justifyContent.flexEnd,
        flexShrink := 0,
        right
      )
    )

    def searchInput(placeholder: String, filter: Node => Boolean) =
      Components.searchInGraph(state.graph, placeholder = placeholder, filter = filter, showParents = false, completeOnInit = false, inputModifiers = VDomModifier(
        width := "120px",
        padding := "2px 10px 2px 10px",
      ), elementModifier = VDomModifier(
        padding := "3px 0px 3px 0px",
      ))

    div(
      renderSplit(
        left = VDomModifier(
          Styles.flex,
          alignItems.center,
          flexWrap.wrap,
          propertySingle.info.tags.map { tag =>
            Components.removableNodeTag(state, tag, taggedNodeId = node.id)
          },
        ),
        right = VDomModifier(
          searchInput("Add Tag", filter = _.role == NodeRole.Tag).foreach { tagId =>
            state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Parent)(node.id, tagId))
          }
        ),
      ).apply(marginTop := "10px"),
      renderSplit(
        left = VDomModifier(
          Styles.flex,
          alignItems.center,
          flexWrap.wrap,
          propertySingle.info.assignedUsers.map { user =>
            Components.removableAssignedUser(state, user, node.id)
          },
        ),
        right = VDomModifier(
          searchInput("Assign User", filter = _.data.isInstanceOf[NodeData.User]).foreach { userId =>
            state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Assigned)(UserId(userId), node.id))
          }
        )
      ).apply(marginTop := "10px"),

      div(
        marginTop := "10px",
        Styles.flex,
        justifyContent.flexEnd,
        ItemProperties.managePropertiesDropdown(state, node.id).prepend(
          button(
            cls := "ui compact button mini",
            "+ Custom field"
          )
        )
      ),
      div(
        propertySingle.properties.map { property =>
          Components.removablePropertySection(state, property.key, property.values).apply(
            commonPropMod
          )
        },
      ),
    )
  }
}
