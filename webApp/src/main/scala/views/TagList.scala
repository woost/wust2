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
import wust.webApp.views.Elements._
import wust.webApp.views.Topbar.{login, logout}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.scalajs.js

object TagList {
  import SharedViewElements._

  def moveableWindow(state: GlobalState)(implicit ctx: Ctx.Owner) = VDomModifier.ifNot(BrowserDetect.isMobile) {
    val newTagFieldActive: Var[Boolean] = Var(false)

    MoveableElement.withToggleSwitch(
      "Tags",
      toggle = state.showTagsList,
      enabled = state.urlConfig.map(c => c.pageChange.page.parentId.isDefined && c.view.forall(_.isContent)),
      resizeEvent = state.rightSidebarNode.toTailObservable.map(_ => ()),
      initialPosition = MoveableElement.RightPosition(100, 400),
      bodyModifier = Ownable(implicit ctx => VDomModifier(
        width := "180px",
        height := "300px",
        overflowY.auto,
        resize := "both",
        Rx {
          val page = state.page()
          val graph = state.graph()
          VDomModifier.ifTrue(state.view().isContent)(
            backgroundColor := state.pageStyle().bgLightColor,
            page.parentId.map { pageParentId =>
              val pageParentIdx = graph.idToIdx(pageParentId)
              val workspaces = graph.workspacesForParent(pageParentIdx)
              val firstWorkspaceIdx = workspaces.head
              val firstWorkspaceId = graph.nodeIds(firstWorkspaceIdx)
              VDomModifier(
                plainList(state, firstWorkspaceId, newTagFieldActive).prepend(
                  padding := "5px",
                ),
                drag(target = DragItem.TagBar(firstWorkspaceId)),
                registerDragContainer(state),
                cls := "PENOS"
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
      val graph = state.graph()
      val workspaceIdx = graph.idToIdx(workspaceId)
      graph.tagChildrenIdx(workspaceIdx).map(tagIdx => graph.roleTree(root = tagIdx, NodeRole.Tag))
    }

    def renderTag(parentId: NodeId, tag: Node) = checkboxNodeTag(state, tag, tagModifier = removableTagMod(() =>
      state.eventProcessor.changes.onNext(GraphChanges.disconnect(Edge.Child)(ParentId(parentId), ChildId(tag.id)))
    ), dragOptions = id => drag(DragItem.Tag(id)))

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

  private def addTagField(
    state: GlobalState,
    parentId: NodeId,
    workspaceId: NodeId,
    newTagFieldActive: Var[Boolean],
  )(implicit ctx: Ctx.Owner): VNode = {
    def submitAction(str:String) = {
      val createdNode = Node.MarkdownTag(str)
      val change = GraphChanges.addNodeWithParent(createdNode, parentId :: Nil)

      state.eventProcessor.changes.onNext(change)
    }

    def blurAction(v:String): Unit = {
      if(v.isEmpty) newTagFieldActive() = false
    }

    val placeHolder = ""

    div(
      cls := "kanbanaddnodefield",
      keyed(parentId),
      Rx {
        if(newTagFieldActive())
          inputRow(state,
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            placeHolderMessage = Some(placeHolder),
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
