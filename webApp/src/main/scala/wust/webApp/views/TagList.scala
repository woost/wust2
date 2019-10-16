package wust.webApp.views

import acyclic.file
import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webUtil.Ownable
import wust.webUtil.outwatchHelpers._

object TagList {
  val addTagText = "Add Tag"

  def movableWindow(viewRender: ViewRenderLike, position: MovableElement.Position)(implicit ctx: Ctx.Owner): MovableElement.Window = {

    MovableElement.Window(
      title = VDomModifier(
        Icons.tags,
        span(marginLeft := "5px", "Tags")
      ),
      toggleLabel = VDomModifier(
        Icons.tags,
        span(marginLeft := "5px", "Tags"),
        border := "2px solid transparent",
        borderRadius := "3px",
        padding := "2px",
        GlobalState.graphTransformations.map {
          case list if list.exists(_.isInstanceOf[GraphOperation.OnlyTaggedWith]) =>
            Rx{
              VDomModifier(
                border := "2px solid rgb(255,255,255)",
                color.white
              )
            }: VDomModifier
          case _ => VDomModifier.empty
        }
      ),
      isVisible = GlobalState.showTagsList,
      initialPosition = position,
      initialWidth = 200,
      initialHeight = 300,
      resizable = true,
      titleModifier = Ownable(implicit ctx =>
        Rx{
          VDomModifier(
            backgroundColor := GlobalState.pageStyle().pageBgColor,
            color.white
          )
        }),
      bodyModifier = Ownable(implicit ctx => body(viewRender).apply(overflowY.auto))
    )
  }

  def body(viewRender: ViewRenderLike)(implicit ctx:Ctx.Owner) = {
    val newTagFieldActive: Var[Boolean] = Var(false)
    div(
      Rx {
        val page = GlobalState.page()
        val graph = GlobalState.rawGraph()
        VDomModifier.ifTrue(GlobalState.viewIsContent())(
          page.parentId.map { pageParentId =>
            val pageParentIdx = graph.idToIdxOrThrow(pageParentId)
            val workspaces = graph.workspacesForParent(pageParentIdx)
            val firstWorkspaceIdx = workspaces.head
            val firstWorkspaceId = graph.nodeIds(firstWorkspaceIdx)
            VDomModifier(
              plainList(firstWorkspaceId, viewRender, newTagFieldActive).prepend(
                padding := "5px"
              ),
              drag(target = DragItem.TagBar(firstWorkspaceId)),
              registerDragContainer
            )
          }
        )
      }
    )
  }

  def plainList(
    workspaceId: NodeId,
    viewRender: ViewRenderLike,
    newTagFieldActive: Var[Boolean] = Var(false)
  )(implicit ctx: Ctx.Owner) = {

    val tags: Rx[Seq[Tree]] = Rx {
      val graph = GlobalState.rawGraph()
      val workspaceIdx = graph.idToIdxOrThrow(workspaceId)
      graph.tagChildrenIdx(workspaceIdx).map(tagIdx => graph.roleTree(root = tagIdx, NodeRole.Tag))
    }

    def renderTag(parentId: NodeId, tag: Node) = checkboxNodeTag(tag, viewRender, tagModifier = removableTagMod(() =>
      GlobalState.submitChanges(GraphChanges.disconnect(Edge.Child)(ParentId(parentId), ChildId(tag.id)))), dragOptions = id => DragComponents.drag(DragItem.Tag(id)), withAutomation = true)

    def renderTagTree(parentId: NodeId, trees: Seq[Tree])(implicit ctx: Ctx.Owner): VDomModifier = trees.map {
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

      addTagField(parentId = workspaceId, workspaceId = workspaceId, newTagFieldActive = newTagFieldActive).apply(marginTop := "10px")
    )
  }

  def checkboxNodeTag(
    tagNode: Node,
    viewRender: ViewRenderLike,
    tagModifier: VDomModifier = VDomModifier.empty,
    pageOnClick: Boolean = false,
    dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
    withAutomation: Boolean = false
  )(implicit ctx: Ctx.Owner): VNode = {

    div( // checkbox and nodetag are both inline elements because of fomanticui
      cls := "tagWithCheckbox",
      Styles.flex,
      alignItems.center,
      div(
        Styles.flexStatic,
        cls := "ui checkbox",
        ViewFilter.addFilterCheckbox(
          tagNode.str, // TODO: renderNodeData
          GraphOperation.OnlyTaggedWith(tagNode.id)
        ),
        label(), // needed for fomanticui
      ),
      nodeTag(tagNode, pageOnClick, dragOptions).apply(tagModifier),
      VDomModifier.ifTrue(withAutomation)(
        GraphChangesAutomationUI.settingsButton(
          tagNode.id,
          activeMod = visibility.visible,
          viewRender = viewRender,
          tooltipDirection = "left center"
        ).apply(cls := "singleButtonWithBg", marginLeft.auto)
      )
    )
  }

  private def addTagField(
    parentId: NodeId,
    workspaceId: NodeId,
    newTagFieldActive: Var[Boolean]
  )(implicit ctx: Ctx.Owner): VNode = {
    def submitAction(sub: InputRow.Submission) = {
      val createdNode = Node.MarkdownTag(sub.text)
      val change = GraphChanges.addNodeWithParent(createdNode, ParentId(parentId) :: Nil)

      GlobalState.submitChanges(change merge sub.changes(createdNode.id))
      FeatureState.use(Feature.CreateTag)
    }

    def blurAction(v: String): Unit = {
      if (v.isEmpty) newTagFieldActive() = false
    }

    div(
      cls := "kanbanaddnodefield",
      Rx {
        if (newTagFieldActive())
          InputRow(
            None,
            submitAction,
            autoFocus = true,
            blurAction = Some(blurAction),
            submitIcon = freeSolid.faPlus,
            enableMentions = false
          )
        else
          div(
            cls := "kanbanaddnodefieldtext",
            s"+ $addTagText",
            color := "gray",
            onClick.stopPropagation foreach { newTagFieldActive() = true }
          )
      }
    )
  }
}
