package wust.webApp.views

import wust.sdk.Colors
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
        openModifier = VDomModifier(focusedNodeId.map(_.map(content(state, _, parentIdAction))), openModifier)
      )}
    )
  }

  def content(state: GlobalState, focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    val nodeStyle = PageStyle.ofNode(focusPref.nodeId)

    val sidebarHeader = div(
      opacity := 0.5,

      Styles.flex,
      alignItems.center,
      div(
        freeSolid.faAngleDoubleRight,
        color := "gray",
        cls := "fa-fw", 
        fontSize.xLarge,
        cursor.pointer,
        onClick(None).foreach(parentIdAction),
        onGlobalMouseDown(None).foreach(parentIdAction), // mousedown instead of click, else a drag from sidebar to outside would close sidebar
      ),
      div(
        marginLeft := "5px",
        nodeBreadcrumbs(state, focusPref, parentIdAction, hideIfSingle = true),
      ),
    )

    def accordionEntry(title: VDomModifier, content: VDomModifier, active:Boolean): UI.AccordionEntry = {
      UI.AccordionEntry(
        title = VDomModifier(
          b(title),
          marginTop := "5px",
          Styles.flexStatic,
          ), 
        content = VDomModifier(
          margin := "5px",
          padding := "0px",
          content
        ),
        active = active
      )
    }


    div(
      height := "100%",
      Styles.flex, // we need flex here because otherwise the height of this element is wrong - it overflows.
      flexDirection.column,
      color.black,
      onMouseDown.stopPropagation.foreach {}, // prevents clicks to bubble up, become globalClick and close sidebar

      sidebarHeader.apply(Styles.flexStatic),
      nodeContent(state, focusPref, parentIdAction).apply(Styles.flexStatic),

      UI.accordion(
        content = Seq(
          accordionEntry("Properties & Custom Fields", VDomModifier(
            nodeProperties(state, focusPref),
            Styles.flexStatic,
          ), active = false),
          accordionEntry("Views", VDomModifier(
            height := "100%",
            viewContent(state, focusPref, parentIdAction, nodeStyle),
          ), active = true),
        ),
        styles = "styled fluid",
        exclusive = false, //BrowserDetect.isMobile,
      ).apply(
        height := "100%",
        Styles.flex,
        flexDirection.column,
        justifyContent.flexStart,
        boxShadow := "none", //explicitly overwrite boxshadow from accordion.
      )
    )
  }
  private def viewContent(state: GlobalState, focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit, nodeStyle:PageStyle)(implicit ctx: Ctx.Owner) = {
    val graph = state.rawGraph.now // this is per new focusPref, and ViewSwitcher just needs an initialvalue
    val initialView = graph.nodesById(focusPref.nodeId).flatMap(ViewHeuristic.bestView(graph, _)).getOrElse(View.Empty)
    val viewVar = Var[View.Visible](initialView)
    def viewAction(view: View): Unit = viewVar() = ViewHeuristic.visibleView(graph, focusPref.nodeId, view).getOrElse(View.Empty)

    VDomModifier(
      Styles.flex,
      flexDirection.column,
      margin := "0px", // overwrite accordion entry margin

      div(
        cls := "pageheader",
        backgroundColor := nodeStyle.pageBgColor,
        paddingTop := "10px", // to have some colored space above the tabs
        Styles.flexStatic,
        Styles.flex,
        alignItems.center,

        ViewSwitcher(state, focusPref.nodeId, viewVar, viewAction, focusPref.view.flatMap(ViewHeuristic.visibleView(graph, focusPref.nodeId, _))),
        NotificationView.notificationsButton(state, focusPref.nodeId, modifiers = marginLeft := "10px") --> viewVar,
      ),

      Rx {
        val view = viewVar()
        ViewRender(state, FocusState(view, focusPref.nodeId, focusPref.nodeId, isNested = true, viewAction, nodeId => parentIdAction(Some(nodeId))), view).apply(
          Styles.growFull,
          flexGrow := 1,
        ).prepend(
          overflow.visible,
          backgroundColor := Colors.contentBg,
        )
      }
    )
  }

  private def nodeBreadcrumbs(state: GlobalState, focusedNodeId: FocusPreference, parentIdAction: Option[NodeId] => Unit, hideIfSingle:Boolean)(implicit ctx: Ctx.Owner) = {
    VDomModifier(
      Rx {
        BreadCrumbs(
          state,
          state.graph(),
          state.user(),
          state.page().parentId,
          Some(focusedNodeId.nodeId),
          nodeId => parentIdAction(Some(nodeId)),
          hideIfSingle = hideIfSingle
        ).apply(paddingBottom := "3px")
      }
    )
  }

  private def nodeContent(state: GlobalState, focusPref: FocusPreference, parentIdAction: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner) = {
    val editMode = Var(false)

    val node = Rx {
      state.graph().nodesByIdOrThrow(focusPref.nodeId)
    }

    val hasNotDeletedParents = Rx {
      state.graph().hasNotDeletedParents(focusPref.nodeId)
    }

    val buttonMods = VDomModifier(
      color := "gray",
      fontSize := "18px",
      padding := "12px 8px",
      cursor.pointer,
    )

    val zoomButton = div(
      Icons.zoom,
      buttonMods,
      onClick.foreach {
        state.urlConfig.update(_.focus(Page(focusPref.nodeId)))
        state.rightSidebarNode() = None
      }
    )

    val deleteButton = Rx {
      VDomModifier.ifTrue(hasNotDeletedParents())(
        div(
          Icons.delete,
          buttonMods,
          onClick.stopPropagation.foreach { _ =>
            state.eventProcessor.changes.onNext(GraphChanges.deleteFromGraph(ChildId(focusPref.nodeId), state.graph.now))
            parentIdAction(None)
          },
        )
      )
    }

    val nodeCard = Rx {
      Components.nodeCardEditable(state, node(), editMode,
        contentInject = width := "100%" // pushes cancel button to the right
      ).apply(
        cls := "right-sidebar-node",

        Styles.flex,
        justifyContent.spaceBetween,

        fontSize := "20px",
        width := "100%",
        margin := "3px 3px 3px 3px",
        Styles.wordWrap,
        cls := "enable-text-selection",
        onClick.stopPropagation(true) --> editMode,

        Components.readObserver(state, node().id)
      )
    }

    div(
      div(
        Styles.flex,
        alignItems.flexStart,

        nodeCard,
        zoomButton,
        deleteButton
      ),

      nodeAuthor(state, focusPref.nodeId),

      div(
        Styles.flex,
        alignItems.center,

        Components.automatedNodesOfNode(state, focusPref.nodeId),
      ),
    )
  }

  private def nodeAuthor(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val authorship = Rx {
      val graph = state.graph()
      val idx = graph.idToIdxOrThrow(nodeId)
      val author = graph.nodeCreator(idx)
      val creationEpochMillis = graph.nodeCreated(idx)
      (author, creationEpochMillis)
    }

    div(
      Styles.flex,
      justifyContent.flexEnd,

      authorship.map { case (author, creationEpochMillis) =>
        chatMessageHeader(state, author, creationEpochMillis, nodeId, author.map(smallAuthorAvatar)).apply(marginRight := "5px")
      },
    )
  }

  private def nodeProperties(state: GlobalState, focusPref: FocusPreference)(implicit ctx: Ctx.Owner) = {

    val propertySingle = Rx {
      val graph = state.rawGraph()
      val nodeIdx = graph.idToIdxOrThrow(focusPref.nodeId)
      PropertyData.Single(graph, nodeIdx)
    }
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
        GraphChanges.connect(Edge.Child)(ParentId(createdNode.id), ChildId(focusPref.nodeId))
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

    sealed trait AddProperty
    object AddProperty {
      case object None extends AddProperty
      case object Custom extends AddProperty
      case class Key(key: String, tpe: NodeData.Type) extends AddProperty
    }

    val addFieldMode = Var[AddProperty](AddProperty.None)

    addFieldMode.map {
      case AddProperty.Custom =>
        ItemProperties.managePropertiesInline(
          state,
          ItemProperties.Target.Node(focusPref.nodeId)
        ).map(_ => AddProperty.None) --> addFieldMode
      case AddProperty.Key(key, tpe) =>
        ItemProperties.managePropertiesInline(
          state,
          ItemProperties.Target.Node(focusPref.nodeId),
          ItemProperties.Config(prefilledType = Some(ItemProperties.TypeSelection.Data(tpe)), hidePrefilledType = true, prefilledKey = key)
        ).map(_ => AddProperty.None) --> addFieldMode
      case AddProperty.None => VDomModifier(
        div(
          cls := "ui form",
          marginTop := "10px",
          Rx {
            VDomModifier(
              propertySingle().properties.map { property =>
                Components.removablePropertySection(state, property.key, property.values)
              },

              VDomModifier.ifTrue(propertySingle().info.reverseProperties.nonEmpty)(div(
                Styles.flex,
                flexWrap.wrap,
                fontSize.small,
                span("Backlinks: ", color.gray),
                propertySingle().info.reverseProperties.map { node =>
                  Components.nodeCard(node, maxLength = Some(50)).apply(
                    margin := "3px",
                    Components.sidebarNodeFocusMod(state.rightSidebarNode, focusPref.nodeId)
                  )
                }
              ))
            )
          }
        ),
        div(
          Styles.flex,
          justifyContent.center,

          button(
            cls := "ui compact basic primary button mini",
            "+ Add Due Date",
            cursor.pointer,
            onClick.stopPropagation(AddProperty.Key(EdgeData.LabeledProperty.dueDate.key, NodeData.DateTime.tpe)) --> addFieldMode
          ),

          button(
            cls := "ui compact basic button mini",
            "+ Add Custom Field",
            cursor.pointer,
            onClick.stopPropagation(AddProperty.Custom) --> addFieldMode
          )
        ),
        renderSplit(
          left = VDomModifier(
            searchInput("Add Tag", filter = _.role == NodeRole.Tag, createNew = createNewTag(_), showNotFound = false).foreach { tagId =>
              state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Child)(ParentId(tagId), ChildId(focusPref.nodeId)))
            }
          ),
          right = VDomModifier(
            Styles.flex,
            alignItems.center,
            flexWrap.wrap,
            Rx {
              propertySingle().info.tags.map { tag =>
                Components.removableNodeTag(state, tag, taggedNodeId = focusPref.nodeId)
              }
            }
          ),
        ).apply(marginTop := "10px"),
        renderSplit(
          left = VDomModifier(
            searchInput("Assign User", filter = _.data.isInstanceOf[NodeData.User]).foreach { userId =>
              state.eventProcessor.changes.onNext(GraphChanges.connect(Edge.Assigned)(focusPref.nodeId, UserId(userId)))
            }
          ),
          right = VDomModifier(
            Styles.flex,
            alignItems.center,
            flexWrap.wrap,
            Rx {
              propertySingle().info.assignedUsers.map { user =>
                Components.removableAssignedUser(state, user, focusPref.nodeId)
              }
            }
          )
        ).apply(marginTop := "10px"),
      )
    }
  }
}
