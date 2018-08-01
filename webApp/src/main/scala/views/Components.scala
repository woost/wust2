package wust.webApp.views

import monix.reactive.Observer
import org.scalajs.dom
import org.scalajs.dom.{document, window}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import views.MediaViewer
import wust.graph._
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import wust.webApp._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Rendered._
import wust.webApp.views.Elements._

object Placeholders {
  val newNode = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Rendered {
  val htmlPostData: NodeData => String = {
    case NodeData.Markdown(content)  => mdString(content)
    case NodeData.PlainText(content) =>
      // assure html in text is escaped by creating a text node, appending it to an element and reading the escaped innerHTML.
      val text = window.document.createTextNode(content)
      val wrap = window.document.createElement("div")
      wrap.appendChild(text)
      wrap.innerHTML
    case NodeData.Link(url)  => s"<a href=$url>" //TODO
    case user: NodeData.User => s"User: ${user.name}"
  }

  def trimToMaxLength(str:String, maxLength:Option[Int]):String = {
    maxLength.fold(str) { length =>
      val rawString = str.trim
      if (rawString.length > length)
        rawString.take(length - 3) + "..."
      else rawString
    }
  }

  def renderNodeData(nodeData:NodeData, maxLength: Option[Int] = None):VNode = nodeData match {
    case NodeData.Markdown(content)  => mdHtml(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case c: NodeData.Link            => MediaViewer.embed(c)
    case user: NodeData.User         => div(s"User: ${user.name}")
  }

  def mdHtml(str: String) = div(div(prop("innerHTML") := marked(str))) // intentionally double wrapped. Because innerHtml does not compose with other modifiers
  def mdString(str: String): String = marked(str)
}

object Components {
  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(state, DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id))
    )
  }

  def nodeTagDot(state: GlobalState, tag: Node): VNode = {
    span(
      cls := "node tagdot",
      backgroundColor := tagColor(tag.id).toHex,
      title := tag.data.str,
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(state, DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id))
    )
  }

  def nodeTag(state: GlobalState, tag: Node): VNode = {
    val contentString = Rendered.trimToMaxLength(tag.data.str, Some(20))
    renderNodeTag(state, tag, contentString)
  }

  def editableNodeTag(state: GlobalState, tag: Node, editable:Var[Boolean], submit:Observer[GraphChanges], maxLength:Option[Int] = Some(20))(implicit ctx:Ctx.Owner): VNode = {
    renderNodeTag(state, tag, editableNode(state, tag, editable, submit, maxLength))
  }

  def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId, graph: Graph): VNode = {
    nodeTag(state, tag)(
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation --> sideEffect {
          // when removing last parent, fall one level lower into the still existing grandparents
          val removingLastParent = graph.parents(taggedNodeId).size == 1
          val addedGrandParents: scala.collection.Set[Edge] =
            if (removingLastParent)
              graph.parents(tag.id).map(Edge.Parent(taggedNodeId, _))
            else
              Set.empty

          state.eventProcessor.changes.onNext(
            GraphChanges(
              delEdges = Set(Edge.Parent(taggedNodeId, tag.id)),
              addEdges = addedGrandParents
            )
          )
          ()
        },
      )
    )
  }

  def renderNodeCardCompact(state:GlobalState, node:Node, injected: VDomModifier)(implicit ctx: Ctx.Owner):VNode = {
    div(
      cls := "node nodecardcompact",
      div(
        cls := "nodecardcompact-content",
        injected
      ),
      onClick.stopPropagation --> sideEffect(()),
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig
    )
  }
  def nodeCardCompact(state:GlobalState, node:Node, injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner):VNode = {
    renderNodeCardCompact(
      state, node,
      injected = VDomModifier(renderNodeData(node.data, maxLength), injected)
    )
  }
  def nodeCardCompactEditable(state:GlobalState, node:Node, editable:Var[Boolean], submit:Observer[GraphChanges], injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner):VNode = {
    renderNodeCardCompact(
      state, node,
      injected = VDomModifier(editableNode(state, node, editable, submit, maxLength), injected)
    )
  }

  def draggableAs(state:GlobalState, payload:DragPayload):VDomModifier = {
    import DragItem.payloadEncoder
    import io.circe.syntax._
    Seq(
      cls := "draggable", // makes this element discoverable for the Draggable library
      attr(DragItem.payloadAttrName) := payload.asJson.noSpaces,
    )
  }

  def dragTarget(dragTarget: DragTarget) = {
    import DragItem.targetEncoder
    import io.circe.syntax._
    attr(DragItem.targetAttrName) := dragTarget.asJson.noSpaces
  }

  def registerDraggableContainer(state: GlobalState):VDomModifier = Seq(
    key := cuid.Cuid(),
//    border := "2px solid blue",
    outline := "none", // hides focus outline
    cls := "draggable-container",
    onInsert.asHtml --> sideEffect { elem =>
//      console.log("Adding Draggable Container:", elem)
      state.draggable.addContainer(elem)
    },
    onDestroy.asHtml --> sideEffect { elem =>
      state.draggable.removeContainer(elem)
    }
  )

  def registerSortableContainer(state: GlobalState, container: DragContainer):VDomModifier = {
    import DragContainer.encoder
    import io.circe.syntax._
    Seq(
      key := cuid.Cuid(),
//          border := "2px solid violet",
      outline := "none", // hides focus outline
      cls := "sortable-container",
      attr(DragContainer.attrName) := container.asJson.noSpaces,
      onInsert.asHtml --> sideEffect { elem =>
//        console.log("Adding Sortable Container:", elem)
        state.sortable.addContainer(elem)
      },
      onDestroy.asHtml --> sideEffect { elem =>
        state.sortable.removeContainer(elem)
      }
    )
  }


  def editableNodeOnClick(state: GlobalState, node: Node, submit:Observer[GraphChanges])(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val editable = Var(false)
    editableNode(state, node, editable, submit)(ctx)(
      onClick.stopPropagation.stopImmediatePropagation --> sideEffect {
        if (!editable.now) {
          editable() = true
        }
      }
    )
  }


  def editableNode(state: GlobalState, node: Node, editable:Var[Boolean], submit:Observer[GraphChanges], maxLength: Option[Int] = None)(
      implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNode(state, contentNode, editable, submit, maxLength)
      case _                         => renderNodeData(node.data, maxLength)
    }
  }

  def editableNode(state: GlobalState, node: Node.Content, editable:Var[Boolean], submit:Observer[GraphChanges], maxLength: Option[Int])(
      implicit ctx: Ctx.Owner
  ): VNode = {

    val initialRender:Var[VDomModifier] = Var(renderNodeData(node.data, maxLength))

    def save(text:String): Unit = {
      if(editable.now) {
        val graph = state.graphContent.now
        val changes = NodeDataParser.addNode(text, contextNodes = graph.nodes, baseNode = node)
        submit.onNext(changes)

        initialRender() = renderNodeData(changes.addNodes.head.data)
        editable() = false
      }
    }

    def discardChanges():Unit = {
      if(editable.now) {
        editable() = false
      }
    }

    div(
      outline := "none", // hides contenteditable outline
      Rx {
        if(editable()) VDomModifier(
          node.data.str, // Markdown source code
          contentEditable := true,
          backgroundColor := "#FFF",
          color := "#000",
          cursor.auto,

          onPostPatch.asHtml --> sideEffect{(_,node) => if(editable.now) node.focus()},

          onEnter.map(_.target.asInstanceOf[dom.html.Element].textContent) --> sideEffect { text => save(text) },
          onBlur --> sideEffect { discardChanges() },
          onFocus --> sideEffect {e => document.execCommand("selectAll",false,null)}
        ) else initialRender()
      }
    )
  }

}
