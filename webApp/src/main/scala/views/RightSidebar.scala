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
    val nodeStyle = state.rightSidebarNode.map(PageStyle.ofNode)

    val boxMod = VDomModifier(
      borderRadius := "3px",
      backgroundColor <-- nodeStyle.map(_.bgLightColor),
    )

    GenericSidebar.right(
      toggleVar,
      config = Ownable { implicit ctx => GenericSidebar.Config(
        openModifier = VDomModifier(
          div(
            marginTop := "-20px",
            width := "20px",
            cls := "fa-fw", freeSolid.faTimes,
            cursor.pointer,
            onClick(None) --> state.rightSidebarNode
          ),
          div(
            color.black,
            height := "100%",
            Styles.flex,
            flexDirection.column,
            justifyContent.spaceBetween,
            overflowY.auto,

            nodeDetailsMenu(state, state.rightSidebarNode).apply(
              minHeight := "300px",
              flex := "1",
              margin := "0px 5px 0px 5px",
              padding := "5px",
              boxMod,
              overflowY.auto,

            ),
            viewContent(state, boxMod).apply(
              flexShrink := 0,
              margin := "5px",
            )
          )
        )
      )}
    )
  }

  private def viewContent(state: GlobalState, viewModifier: VDomModifier)(implicit ctx: Ctx.Owner) = {

    div(
      state.rightSidebarNode.map(_.map { nodeId =>
        val bestView = state.graph.now.nodesByIdGet(nodeId).fold[View.Visible](View.Empty)(ViewHeuristic.bestView(state.graph.now, _))
        val viewVar = Var[View](bestView)
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
              onClick.foreach { state.urlConfig.update(_.focus(Page(nodeId))) }
            ),
            PageHeader.viewSwitcher(state, nodeId, viewVar),
          ),
          Rx {
            val view = viewVar().asInstanceOf[View.Visible] // TODO
            ViewRender(view, state, state.rightSidebarNode).apply(
              width := "100%",
              height := "500px",
              viewModifier
            )
          }
        )
      })
    )
  }

  def nodeDetailsMenu(state: GlobalState, focusedNodeId: Rx[Option[NodeId]])(implicit ctx: Ctx.Owner) = {

    div(
      Rx {
        focusedNodeId().flatMap { nodeId =>
          state.graph().nodesByIdGet(nodeId).map { node =>
            VDomModifier(
              Components.nodeCardEditableOnClick(state, node),
              nodeProperties(state, state.graph(), node)
            )
          }
        }
      }
    )
  }

  private def nodeProperties(state: GlobalState, graph: Graph, node: Node)(implicit ctx: Ctx.Owner) = {
    val nodeIdx = graph.idToIdxOrThrow(node.id)

    val assignedUsers = graph.assignedUsersIdx(nodeIdx).map(idx => graph.nodes(idx).asInstanceOf[Node.User])
    val tags = graph.tagParentsIdx(nodeIdx).map(idx => graph.nodes(idx).asInstanceOf[Node.Content])
    val propertyEdges = graph.propertiesEdgeIdx(nodeIdx).map(idx => graph.edges(idx).asInstanceOf[Edge.LabeledProperty])

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
          tags.map { tag =>
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
          assignedUsers.map { user =>
            Components.removableAssignedUser(state, user, node.id)
          },
        ),
        right = VDomModifier(
          searchInput("Assign User", filter = _.data.isInstanceOf[NodeData.User]).foreach { userId =>
            state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Assigned)(UserId(userId), node.id))
          }
        )
      ).apply(marginTop := "10px"),
      UI.horizontalDivider("Custom Fields").apply(ItemProperties.manageProperties(state, node.id, button(cls := "ui button mini", freeSolid.faPlus, marginLeft := "10px"))),

      div(
        propertyEdges.map { propertyEdge =>
          graph.nodesByIdGet(propertyEdge.propertyId).map { property =>
            Components.removablePropertySection(state, propertyEdge, property).apply(
              commonPropMod
            )
          }
        },
      ),
    )
  }
}
