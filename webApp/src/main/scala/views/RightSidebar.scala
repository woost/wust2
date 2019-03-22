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
import wust.webApp.views.Elements._
import wust.webApp.views.SharedViewElements._
import wust.webApp.BrowserDetect
import wust.webApp.{Icons, ItemProperties, Ownable}

import scala.collection.breakOut

object RightSidebar {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = apply(state, state.rightSidebarNode, nodeId => state.rightSidebarNode() = nodeId.map(FocusPreference(_)))
  def apply(state: GlobalState, focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit, openModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val toggleVar = Var(focusedNodeId.now.isDefined)
    focusedNodeId.triggerLater(opt => toggleVar() = opt.isDefined)
    toggleVar.triggerLater(show => if (!show) parentIdAction(None))

    GenericSidebar.right(
      toggleVar,
      config = Ownable { implicit ctx => GenericSidebar.Config(
        openModifier = VDomModifier(content(state, focusedNodeId, parentIdAction), openModifier)
      )}
    )
  }

  // TODO rewrite to rely on static focusid
  def content(state: GlobalState, focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    val nodeStyle = focusedNodeId.map(n => PageStyle.ofNode(n.map(_.nodeId)))

    def accordionEntry(name: VDomModifier, body: VDomModifier): (VDomModifier, VDomModifier) = {
      VDomModifier(
        marginTop := "5px",
        b(name),
        Styles.flexStatic,
      ) -> VDomModifier(
        margin := "4px",
        padding := "0px",
        body
      )
    }

    div(
      height := "100%",
      Styles.flex, // we need flex here because otherwise the height of this element is wrong - it overflows.
      flexDirection.column,
      borderRadius := "3px",
      margin := "5px",
      color.black,
      backgroundColor <-- nodeStyle.map(_.bgLightColor),
      onClick.stopPropagation.foreach {}, // prevents clicks to bubble up, become globalClick and close sidebar

      div(
        Styles.flex,
        alignItems.center,
        div(
          fontSize.xLarge,
          cls := "fa-fw", freeSolid.faAngleDoubleRight,
          cursor.pointer,
          onClick(None).foreach(parentIdAction),
          onGlobalClick(None).foreach(parentIdAction),
        ),
        div(
          marginLeft := "5px",
          nodeBreadcrumbs(state, focusedNodeId, parentIdAction),
        ),
      ),
      UI.accordion(
        Seq(
          accordionEntry(VDomModifier(Rx {
            focusedNodeId().map { focusPreference =>
              val graph = state.rawGraph()
              val node = graph.nodesById(focusPreference.nodeId)
              node.role.toString
            }
          }), VDomModifier(
            nodeContent(state, focusedNodeId, parentIdAction),
            overflowY.auto,
            maxHeight := "40%"
          )),
          accordionEntry("Properties", VDomModifier(
            nodeDetailsMenu(state, focusedNodeId, parentIdAction),
            overflowY.auto,
            flex := "1 1 20%"
          )),
          accordionEntry("Views", VDomModifier(
            viewContent(state, focusedNodeId, parentIdAction),
            flex := "1 1 40%"
          )),
        ),
        styles = "styled fluid",
        exclusive = false, //BrowserDetect.isMobile,
        initialActive = Seq(0, 1, 2), //if (BrowserDetect.isMobile) Seq(0) else Seq(0, 1, 2),
      ).apply(
        height := "100%",
        marginBottom := "5px",
        Styles.flex,
        flexDirection.column,
        justifyContent.flexStart,
        backgroundColor <-- nodeStyle.map(_.bgLightColor), //explicitly overwrite bg color from accordion.
        boxShadow := "none", //explicitly overwrite boxshadow from accordion.
      )
    )
  }
  private def viewContent(state: GlobalState, focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      Styles.flex,
      flexDirection.column,
      focusedNodeId.map(_.map { focusPref =>
        val graph = state.rawGraph.now
        val initialView = graph.nodesByIdGet(focusPref.nodeId).fold[View.Visible](View.Empty)(ViewHeuristic.bestView(graph, _))
        val viewVar = Var[View.Visible](initialView)
        def viewAction(view: View): Unit = viewVar() = ViewHeuristic.visibleView(graph, focusPref.nodeId, view)

        VDomModifier(
          div(
            Styles.flexStatic,
            Styles.flex,
            alignItems.center,
            ViewSwitcher(state, focusPref.nodeId, viewVar, viewAction, focusPref.view.map(ViewHeuristic.visibleView(graph, focusPref.nodeId, _))),
            borderBottom := "2px solid black",
            button(
              cls := "ui mini button compact",
              marginLeft := "10px",
              Icons.zoom,
              cursor.pointer,
              onClick.foreach { state.urlConfig.update(_.focus(Page(focusPref.nodeId), viewVar.now)) }
            ),
          ),
          Rx {
            val view = viewVar()
              ViewRender(state, FocusState(view, focusPref.nodeId, focusPref.nodeId, isNested = true, viewAction, nodeId => parentIdAction(Some(nodeId))), view).apply(
                Styles.growFull,
                flexGrow := 1,
              ).prepend(
                overflow.visible,
              )
          }
        )
      })
    )
  }

  private def nodeBreadcrumbs(state: GlobalState, focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      Rx {
        BreadCrumbs(state, state.page().parentId, focusedNodeId.map(_.map(_.nodeId)), nodeId => parentIdAction(Some(nodeId))).apply(paddingBottom := "3px")
      }
    )
  }

  private def nodeContent(state: GlobalState, focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    val editMode = Var(false)

    VDomModifier(
      Rx {
        focusedNodeId().flatMap { focusPref =>
          state.rawGraph().nodesByIdGet(focusPref.nodeId).map { node =>
            div(
              div(
                nodeAuthor(state.rawGraph(), focusPref.nodeId)
              ),

              div(
                Styles.flex,
                alignItems.flexStart,
                Components.nodeCardEditable(state, node, editMode).apply(
                  Styles.wordWrap, width := "100%", margin := "3px 0px 3px 3px", cls := "enable-text-selection"
                ),
                div(
                  Icons.edit,
                  marginLeft := "5px",
                  cursor.pointer,
                  onClick.stopPropagation(true) --> editMode
                )
              ),
            )
          }
        }
      }
    )
  }

  private def nodeAuthor(graph: Graph, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val idx = graph.idToIdxOrThrow(nodeId)
    val author = graph.nodeCreator(idx)
    val creationEpochMillis = graph.nodeCreated(idx)

    VDomModifier(
      Styles.flex,
      alignItems.center,
      author.map { userNode =>
        VDomModifier(
          Avatar.user(userNode.id)(
            marginRight := "2px",
            width := "22px",
            height := "22px",
            cls := "avatar",
          ),
          Components.displayUserName(userNode.data),
          div(
            Styles.flex,
            alignItems.center,
            marginLeft := "auto",
            SharedViewElements.modifications(userNode, graph.nodeModifier(idx)),
            Elements.creationDate(creationEpochMillis),
          )
        )
      },
    )
  }

  private def nodeDetailsMenu(state: GlobalState, focusedNodeId: Rx[Option[FocusPreference]], parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      Rx {
        focusedNodeId().flatMap { focusPref =>
          state.rawGraph().nodesByIdGet(focusPref.nodeId).map { node =>
            nodeProperties(state, state.rawGraph(), node)
          }
        }
      }
    )
  }

  private def nodeProperties(state: GlobalState, graph: Graph, node: Node)(implicit ctx: Ctx.Owner) = {
    val nodeIdx = graph.idToIdxOrThrow(node.id)

    val propertySingle = PropertyData.Single(graph, nodeIdx)

    def renderSplit(left: VDomModifier, right: VDomModifier) = div(
      Styles.flex,
      justifyContent.spaceBetween,
      div(
        left
      ),
      div(
        Styles.flex,
        justifyContent.flexEnd,
        right
      )
    )

    def createNewTag(str: String): Boolean = {
      val createdNode = Node.MarkdownTag(str)
      val change = GraphChanges.addNodeWithParent(createdNode, ParentId(state.page.now.parentId)) merge
        GraphChanges.connect(Edge.Child)(ParentId(createdNode.id), ChildId(node.id))
      state.eventProcessor.changes.onNext(change)
      true
    }

    def searchInput(placeholder: String, filter: Node => Boolean, createNew: String => Boolean = _ => false, showNotFound: Boolean = true) =
      Components.searchInGraph(state.rawGraph, placeholder = placeholder, filter = filter, showNotFound = showNotFound, createNew = createNew, inputModifiers = VDomModifier(
        width := "140px",
        padding := "2px 10px 2px 10px",
      ), elementModifier = VDomModifier(
        padding := "3px 0px 3px 0px",
      ))

    VDomModifier(
      div(
        Styles.flex,
        justifyContent.flexEnd,
        div(
          button(
            cls := "ui compact button mini",
            "+ Custom field"
          ),
          ItemProperties.managePropertiesDropdown(state, ItemProperties.Target.Node(node.id), dropdownModifier = cls := "top right"),
        )
      ),
      renderSplit(
        left = VDomModifier(
          searchInput("Add Tag", filter = _.role == NodeRole.Tag, createNew = createNewTag(_), showNotFound = false).foreach { tagId =>
            state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Child)(ParentId(tagId), ChildId(node.id)))
          }
        ),
        right = VDomModifier(
          Styles.flex,
          alignItems.center,
          flexWrap.wrap,
          propertySingle.info.tags.map { tag =>
            Components.removableNodeTag(state, tag, taggedNodeId = node.id)
          },
        ),
      ).apply(marginTop := "10px"),
      renderSplit(
        left = VDomModifier(
          searchInput("Assign User", filter = _.data.isInstanceOf[NodeData.User]).foreach { userId =>
            state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Assigned)(node.id, UserId(userId)))
          }
        ),
        right = VDomModifier(
          Styles.flex,
          alignItems.center,
          flexWrap.wrap,
          propertySingle.info.assignedUsers.map { user =>
            Components.removableAssignedUser(state, user, node.id)
          },
        )
      ).apply(marginTop := "10px"),

      div(
        marginTop := "10px",
        propertySingle.properties.map { property =>
          Components.removablePropertySection(state, property.key, property.values).apply(
            marginBottom := "10px",
          )
        },

        VDomModifier.ifTrue(propertySingle.info.reverseProperties.nonEmpty)(div(
          Styles.flex,
          flexWrap.wrap,
          fontSize.small,
          span("Backlinks: ", color.gray),
          propertySingle.info.reverseProperties.map { node =>
            Components.nodeCard(node, maxLength = Some(50)).apply(
              margin := "3px",
              Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id)
            )
          }
        )),
      ),
    )
  }
}
