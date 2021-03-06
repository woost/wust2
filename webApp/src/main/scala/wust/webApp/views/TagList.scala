package wust.webApp.views

import wust.webUtil.tippy
import acyclic.file
import fontAwesome._
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{ BaseColors, NodeColor }
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{ drag, registerDragContainer }
import wust.webUtil.{ Ownable, UI }
import wust.webUtil.outwatchHelpers._
import wust.webUtil.Elements.onClickDefault

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
            backgroundColor :=? NodeColor.pageBg.of(GlobalState.page().parentId, GlobalState.graph()),
            color.white
          )
        }),
      bodyModifier = Ownable(implicit ctx => body(viewRender).apply(overflowY.auto))
    )
  }

  def body(viewRender: ViewRenderLike)(implicit ctx: Ctx.Owner) = {
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

    def renderTag(parentId: NodeId, tag: Node.Content) = checkboxNodeTag(tag, viewRender, tagModifier = removableTagMod(() =>
      GlobalState.submitChanges(GraphChanges.disconnect(Edge.Child)(ParentId(parentId), ChildId(tag.id)))), dragOptions = id => DragComponents.drag(DragItem.Tag(id)), withAutomation = true)

    def renderTagTree(parentId: NodeId, trees: Seq[Tree])(implicit ctx: Ctx.Owner): VDomModifier = trees.map {
      case Tree.Leaf(node) =>
        renderTag(parentId, node.as[Node.Content])
      case Tree.Parent(node, children) =>
        VDomModifier(
          renderTag(parentId, node.as[Node.Content]),
          div(
            paddingLeft := "10px",
            renderTagTree(node.id, children)
          )
        )
    }

    div(
      Rx { renderTagTree(workspaceId, tags()) },

      UI.toggle("Show Text of Tags", GlobalState.maximizedTags).apply(cls := "compact mini", marginTop := "10px"),

      addTagField(parentId = workspaceId, workspaceId = workspaceId, newTagFieldActive = newTagFieldActive).apply(marginTop := "10px")
    )
  }

  def checkboxNodeTag(
    tagNode: Node.Content,
    viewRender: ViewRenderLike,
    tagModifier: VDomModifier = VDomModifier.empty,
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
      nodeTag(tagNode, dragOptions).apply(tagModifier, marginRight.auto),

      div(cls := "singleButtonWithBg", marginRight := "3px", ColorMenu.menuIcon(BaseColors.tag, tagNode)),
      VDomModifier.ifTrue(withAutomation)(
        GraphChangesAutomationUI.settingsButton(
          tagNode.id,
          activeMod = visibility.visible,
          viewRender = viewRender,
        ).apply(cls := "singleButtonWithBg")
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
            onClickDefault foreach { newTagFieldActive() = true }
          )
      }
    )
  }
}
