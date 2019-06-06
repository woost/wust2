package wust.webApp.views

import wust.webApp._
import dateFns.DateFns
import fontAwesome._
import googleAnalytics.Analytics
import monix.eval.Task
import monix.execution.Ack
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.api.{ApiEvent, AuthUser}
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor
import wust.util._
import wust.webApp.dragdrop.DragItem.DisableDrag
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webApp.views.Elements._

import scala.collection.breakOut
import scala.concurrent.Future
import scala.scalajs.js

object TagList {
  import SharedViewElements._

  def moveableWindow(state: GlobalState, position: MoveableElement.Position)(implicit ctx: Ctx.Owner): MoveableElement.Window = {
    val newTagFieldActive: Var[Boolean] = Var(false)

    MoveableElement.Window(
      VDomModifier(
        Icons.tags,
        span(marginLeft := "5px", "Tags"),
        state.graphTransformations.map {
          case list if list.exists(_.isInstanceOf[GraphOperation.OnlyTaggedWith]) =>
            Rx{VDomModifier(
              backgroundColor := state.pageStyle().pageBgColor,
              color.white,
            )}:VDomModifier
          case _ => VDomModifier.empty
        }
      ),
      toggle = state.showTagsList,
      initialPosition = position,
      initialWidth = 200,
      initialHeight = 300,
      resizable = true,
      titleModifier = Ownable(implicit ctx =>
        Rx{VDomModifier(
          backgroundColor := state.pageStyle().pageBgColor,
          color.white,
        )}
      ),
      bodyModifier = Ownable(implicit ctx => VDomModifier(
        overflowY.auto,
        Rx {
          val page = state.page()
          val graph = state.rawGraph()
          VDomModifier.ifTrue(state.view().isContent)(
            page.parentId.map { pageParentId =>
              val pageParentIdx = graph.idToIdxOrThrow(pageParentId)
              val workspaces = graph.workspacesForParent(pageParentIdx)
              val firstWorkspaceIdx = workspaces.head
              val firstWorkspaceId = graph.nodeIds(firstWorkspaceIdx)
              VDomModifier(
                plainList(state, firstWorkspaceId, newTagFieldActive).prepend(
                  padding := "5px",
                ),
                drag(target = DragItem.TagBar(firstWorkspaceId)),
                registerDragContainer(state),
              )
            }
          )
        }
      ))
    )
  }

  def plainList(
    state: GlobalState,
    workspaceId: NodeId,
    newTagFieldActive: Var[Boolean] = Var(false)
  )(implicit ctx:Ctx.Owner) = {

    val tags:Rx[Seq[Tree]] = Rx {
      val graph = state.rawGraph()
      val workspaceIdx = graph.idToIdxOrThrow(workspaceId)
      graph.tagChildrenIdx(workspaceIdx).map(tagIdx => graph.roleTree(root = tagIdx, NodeRole.Tag))
    }

    def renderTag(parentId: NodeId, tag: Node) = checkboxNodeTag(state, tag, tagModifier = removableTagMod(() =>
      state.eventProcessor.changes.onNext(GraphChanges.disconnect(Edge.Child)(ParentId(parentId), ChildId(tag.id)))
    ), dragOptions = id => DragComponents.drag(DragItem.Tag(id)), withAutomation = true)

    def renderTagTree(parentId: NodeId, trees:Seq[Tree])(implicit ctx: Ctx.Owner): VDomModifier = trees.map {
      case Tree.Leaf(node) =>
        renderTag(parentId, node)
      case Tree.Parent(node, children) =>
        VDomModifier(
          renderTag(parentId, node),
          div(
            paddingLeft := "10px",
            renderTagTree(node.id, children)
          )
        )
    }

    div(
      Rx { renderTagTree(workspaceId, tags()) },

      addTagField(state, parentId = workspaceId, workspaceId = workspaceId, newTagFieldActive = newTagFieldActive).apply(marginTop := "10px"),
    )
  }

  def checkboxNodeTag(
    state: GlobalState,
    tagNode: Node,
    tagModifier: VDomModifier = VDomModifier.empty,
    pageOnClick: Boolean = false,
    dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
    withAutomation: Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {

    div( // checkbox and nodetag are both inline elements because of fomanticui
      cls := "tagWithCheckbox",
      Styles.flex,
      alignItems.center,
      div(
        Styles.flexStatic,
        cls := "ui checkbox",
        ViewFilter.addFilterCheckbox(
          state,
          tagNode.str, // TODO: renderNodeData
          GraphOperation.OnlyTaggedWith(tagNode.id)
        ),
        label(), // needed for fomanticui
      ),
      nodeTag(state, tagNode, pageOnClick, dragOptions).apply(tagModifier),
      VDomModifier.ifTrue(withAutomation)(GraphChangesAutomationUI.settingsButton(state, tagNode.id, activeMod = visibility.visible).apply(cls := "singleButtonWithBg", marginLeft.auto)),
    )
  }


  private def addTagField(
    state: GlobalState,
    parentId: NodeId,
    workspaceId: NodeId,
    newTagFieldActive: Var[Boolean],
  )(implicit ctx: Ctx.Owner): VNode = {
    def submitAction(str:String) = {
      val createdNode = Node.MarkdownTag(str)
      val change = GraphChanges.addNodeWithParent(createdNode, ParentId(parentId) :: Nil)

      state.eventProcessor.changes.onNext(change)
    }

    def blurAction(v:String): Unit = {
      if(v.isEmpty) newTagFieldActive() = false
    }

    div(
      cls := "kanbanaddnodefield",
      keyed(parentId),
      Rx {
        if(newTagFieldActive())
          InputRow(state,
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            submitIcon = freeSolid.faPlus,
          )
        else
          div(
            cls := "kanbanaddnodefieldtext",
            "+ Add Tag",
            color := "gray",
            onClick foreach { newTagFieldActive() = true }
          )
      }
    )
  }
}
