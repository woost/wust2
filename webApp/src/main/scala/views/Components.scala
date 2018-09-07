package wust.webApp.views

import highlight._
import monix.reactive.Observer
import org.scalajs.dom
import org.scalajs.dom.{console, document, window}
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.graph._
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import marked.{Marked, MarkedOptions}
import org.scalajs.dom.raw.HTMLElement
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import emojijs.EmojiConvertor

import scala.scalajs.js

object Placeholders {
  val newNode = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Rendered {
  val emoji = new EmojiConvertor()

  val htmlPostData: NodeData => String = {
    case NodeData.Markdown(content)  => mdString(content)
    case NodeData.PlainText(content) =>
      // assure html in text is escaped by creating a text node, appending it to an element and reading the escaped innerHTML.
      val text = window.document.createTextNode(content)
      val wrap = window.document.createElement("div")
      wrap.appendChild(text)
      wrap.innerHTML
    case NodeData.Link(url)          => s"<a href=$url>" //TODO
    case user: NodeData.User         => s"User: ${ user.name }"
  }

  def init(): Unit = {
    Marked.setOptions(new MarkedOptions {
      gfm = true
      highlight = ((code: String, lang: js.UndefOr[String]) => { // Only gets called for code blocks
        lang.toOption match {
          case Some(l) => "<div class = \"hljs\">" + Highlight.highlight(l, code).value + "</div>"
          case _ => "<div class = \"hljs\">" + Highlight.highlightAuto(code).value + "</div>"
        }
      }): js.Function2[String, js.UndefOr[String], String]
    })

    emoji.img_sets.apple.sheet = "/emoji-datasource/sheet_apple_32.png"
    emoji.img_sets.apple.sheet_size = 32
    emoji.img_set = "apple"
    emoji.use_sheet = true
    emoji.init_env()

//    Highlight.configure(new HighlightOptions {
//      tabReplace = "  "
//    })
  }

  def trimToMaxLength(str: String, maxLength: Option[Int]): String = {
    maxLength.fold(str) { length =>
      val rawString = str.trim
      if(rawString.length > length)
        rawString.take(length - 3) + "..."
      else rawString
    }
  }

  def renderNodeData(nodeData: NodeData, maxLength: Option[Int] = None): VNode = nodeData match {
    case NodeData.Markdown(content)  => mdHtml(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case c: NodeData.Link            => MediaViewer.embed(c)
    case user: NodeData.User         => div(user.name)
  }


  def mdHtml(str: String) = div(div(prop("innerHTML") := Marked(emoji.replace_colons(str)))) // intentionally double wrapped. Because innerHtml does not compose with other modifiers
  def mdString(str: String): String = Marked(emoji.replace_colons(str))
}

object Components {
  val woostIcon = svg.svg(
    cls := "svg-inline--fa fa-w-14",
    svg.viewBox := "0 0 700 700",
    svg.path(svg.fill := "currentColor", svg.d := "M503.9 21.1c-33.5 2.8-54.8 31.2-58.7 78.4-1.3 16.8-.1 51.2 2.4 63.9.2 1.2.6 4.4.9 7.3.4 2.9 1 6.6 1.5 8.4.5 1.8.7 3.5.5 4-1.2 1.9 2.3 1.9 13.7-.1 6.8-1.1 13.5-2.3 14.9-2.5 10.8-1.9 36.9-9.4 50.3-14.5 1.1-.4 2.4-.8 2.8-.9 1.8-.3 15.9-7.7 23.5-12.2 8-4.8 22-17.4 26-23.4 9.9-14.6 12.6-29.2 8.8-46.5-2.4-11.2-14.8-30.1-25.6-39-3-2.5-5.6-4.7-5.9-5-.3-.3-3.4-2.4-7-4.6-16.1-10-32.5-14.6-48.1-13.3zM174 23c-15.2 4-27.1 10.6-40.4 22.4-8.3 7.3-14.7 15.8-19.6 26-19.1 40 5.7 76.9 65.8 98 9.1 3.2 25 7.6 32.2 9 1.4.3 3.6.8 5 1.1 7.2 1.7 32 5.8 32 5.2 0-.2.4-2.8 1-5.8 1.7-9.4 1.9-11.3 2.6-16.9.3-3 .8-6.4 1-7.4 2.2-12 2.3-59 0-65.7-.3-.8-.7-3-.9-4.9-.7-5.7-3.5-15.3-6.6-23-8.5-20.7-22.2-33.8-40.6-38.5-7.7-2-22.9-1.7-31.5.5zm74 167.7c-5.5 26.9-12.1 52.6-19.8 77.3-12.4 39.3-21.6 64.2-34.9 94.3-3.6 8.1-6.4 14.9-6.2 15.1.2.2 2.3 1.3 4.7 2.5 11.8 6 45.5 27.2 65.7 41.4 18.2 12.8 45.9 33.7 52.1 39.2.7.5 2 1.7 3 2.5 10.1 8.1 29.7 25.2 35 30.7l2.2 2.2 3.5-3.3c26-23.8 47.7-41.4 78.7-63.8 25.4-18.3 63.5-42.7 76.8-49.1 2.3-1.2 4.2-2.4 4.2-2.8 0-.4-1.7-4.5-3.9-9.1-5.2-11.1-9.4-21.2-14.3-33.8-2.2-5.8-4.6-11.9-5.3-13.5-1.1-2.5-2.5-6.4-3-8.5-.1-.3-.4-1.2-.8-2-5.2-13-19.9-61-25.7-84-3.3-13.3-9-38.9-9-40.6 0-.3-1.7-.3-3.7 0-7.3 1-12.9 1.6-19.3 2.1-3.6.3-7.8.7-9.5.9-21.8 2.8-110.3 2.8-137 .1-1.6-.2-6.1-.6-10-.9-7.5-.7-10.3-1.1-17.7-2.1l-4.6-.7-1.2 5.9zM59.4 342.5c-21 3.8-34.7 13.5-43.2 30.4-2.3 4.4-4 8.1-3.8 8.1.2 0-.3 2.1-1.1 4.7-.9 2.7-1.6 9.6-1.8 15.8-.5 19.4 5.1 39.2 15.8 55.2 12 18 28.5 28.6 46.7 30 12.7 1 29.9-4.1 41.5-12.1 10-6.9 23-19.2 31.1-29.2 2.1-2.7 4.2-5.1 4.5-5.4 1.5-1.3 16.3-23.6 21.1-32 6.6-11.3 15.8-29.2 15.8-30.6 0-.6-7.6-4.7-16.9-9.2-45.7-22-82.9-30.7-109.7-25.7zm555.5-1.1c-.2.2-2.9.6-5.9 1-15.8 1.8-36.8 7.7-57.5 16.3-12.6 5.3-37.5 17.7-37.5 18.7 0 1.5 9 18.8 15.8 30.6 26.8 45.9 56.3 73.1 84.8 78.1 8.8 1.5 19.6.6 27.7-2.5 6.1-2.3 13.8-6.5 16.3-9 .6-.6 2.9-2.7 5.1-4.7 16.7-15 27.8-43.6 26.8-68.9-1-26.9-13.8-46.2-36.3-54.8-4.2-1.7-9.5-3.3-11.7-3.7-4.7-.7-27-1.7-27.6-1.1z"),
    svg.path(svg.fill := "currentColor", svg.d := "M339.3 506.6c-15.8 16.2-18.4 19.2-26.8 29.9-17.8 22.8-30.5 46.9-34 64.5-1.6 7.5-1.5 24.4.1 30.5 2 7.6 6.6 16.3 11.7 22.1 3.7 4.2 13.8 12.4 15.3 12.4.3 0 1.9.9 3.5 2.1 1.6 1.1 2.9 1.7 2.9 1.3 0-.3.6-.2 1.3.4 1.3 1.1 13.3 4.9 17.6 5.7 1.4.2 4.2.7 6.1 1 8.2 1.5 30.4.4 34.3-1.6.9-.6 1.7-.8 1.7-.5 0 .8 12-3.3 17.2-6 34.8-17.6 42.4-53.9 20.2-96.9-10.5-20.5-25.8-40.5-49.6-65l-10.7-11-10.8 11.1z")
  )


  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(state, DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
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
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTag(state: GlobalState, tag: Node): VNode = {
    val contentString = Rendered.trimToMaxLength(tag.data.str, Some(20))
    renderNodeTag(state, tag, contentString)
  }

  def editableNodeTag(state: GlobalState, tag: Node, editable: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = Some(20), newTagParentIds: Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode = {
    renderNodeTag(state, tag, editableNode(state, tag, editable, submit, newTagParentIds, maxLength))
  }

  def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId, graph: Graph): VNode = {
    nodeTag(state, tag)(
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation --> sideEffect {
          // when removing last parent, fall one level lower into the still existing grandparents
          //TODO: move to GraphChange factory
          // val removingLastParent = graph.parents(taggedNodeId).size == 1
          // val addedGrandParents: scala.collection.Set[Edge] =
          //   if (removingLastParent)
          //     graph.parents(tag.id).map(Edge.Parent(taggedNodeId, _))
          //   else
          //     Set.empty

          state.eventProcessor.changes.onNext(
            GraphChanges.delete(taggedNodeId, Set(tag.id))
          )
          ()
        },
      )
    )
  }

  def renderNodeCard(state: GlobalState, node: Node, injected: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "node nodecard",
      div(
        cls := "nodecard-content",
        injected
      ),
    )
  }
  def nodeCard(state: GlobalState, node: Node, injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(
      state, node,
      injected = VDomModifier(renderNodeData(node.data, maxLength), injected)
    )
  }
  def nodeCardEditable(state: GlobalState, node: Node, editable: Var[Boolean], submit: Observer[GraphChanges], newTagParentIds: Iterable[NodeId], injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(
      state, node,
      injected = VDomModifier(editableNode(state, node, editable, submit, newTagParentIds, maxLength), injected)
    )
  }

  def readDragTarget(elem: dom.html.Element):Option[DragTarget] = {
    readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
  }

  def writeDragTarget(elem: dom.html.Element, dragTarget: => DragTarget): Unit = {
    writePropertyIntoElement(elem, DragItem.targetPropName, dragTarget)
  }

  def readDragPayload(elem: dom.html.Element):Option[DragPayload] = {
    readPropertyFromElement[DragPayload](elem, DragItem.payloadPropName)
  }

  def writeDragPayload(elem: dom.html.Element, dragPayload: => DragPayload): Unit = {
    writePropertyIntoElement(elem, DragItem.payloadPropName, dragPayload)
  }

  def readDragContainer(elem: dom.html.Element):Option[DragContainer] = {
    readPropertyFromElement[DragContainer](elem, DragContainer.propName)
  }

  def writeDragContainer(elem: dom.html.Element, dragContainer: => DragContainer): Unit = {
    writePropertyIntoElement(elem, DragContainer.propName, dragContainer)
  }

  def draggableAs(state: GlobalState, payload: => DragPayload): VDomModifier = {
    Seq(
      cls := "draggable", // makes this element discoverable for the Draggable library
      onDomElementChange.asHtml --> sideEffect{ elem =>
        writeDragPayload(elem, payload)
      }
    )
  }

  def dragTarget(dragTarget: DragTarget) = {
    onDomElementChange.asHtml --> sideEffect{ elem =>
      writeDragTarget(elem, dragTarget)
    }
  }

  def registerDraggableContainer(state: GlobalState): VDomModifier = Seq(
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

  def registerSortableContainer(state: GlobalState, container: DragContainer): VDomModifier = {
    Seq(
      //          border := "2px solid violet",
      outline := "none", // hides focus outline
      cls := "sortable-container",
      onDomElementChange.asHtml --> sideEffect { elem =>
        writeDragContainer(elem, container)
      },
      onInsert.asHtml --> sideEffect { elem =>
        //        console.log("Adding Sortable Container:", elem)
        state.sortable.addContainer(elem)
      },
      onDestroy.asHtml --> sideEffect { elem =>
        state.sortable.removeContainer(elem)
      }
    )
  }


  def editableNodeOnClick(state: GlobalState, node: Node, submit: Observer[GraphChanges], newTagParentIds: Iterable[NodeId])(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val editable = Var(false)
    editableNode(state, node, editable, submit, newTagParentIds)(ctx)(
      onClick.stopPropagation.stopImmediatePropagation --> sideEffect {
        if(!editable.now) {
          editable() = true
        }
      }
    )
  }


  def editableNode(state: GlobalState, node: Node, editable: Var[Boolean], submit: Observer[GraphChanges], newTagParentIds: Iterable[NodeId], maxLength: Option[Int] = None)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNodeContent(state, contentNode, editable, submit, newTagParentIds, maxLength)
      case _                         => renderNodeData(node.data, maxLength)
    }
  }

  def editableNodeContent(state: GlobalState, node: Node.Content, editable: Var[Boolean], submit: Observer[GraphChanges], newTagParentIds: Iterable[NodeId], maxLength: Option[Int])(
    implicit ctx: Ctx.Owner
  ): VNode = {

    val initialRender: Var[VDomModifier] = Var(renderNodeData(node.data, maxLength))

    def save(text: String): Unit = {
      if(editable.now) {
        val graph = state.graphContent.now
        val changes = NodeDataParser.addNode(text, contextNodes = graph.nodes, newTagParentIds, baseNode = node)
        submit.onNext(changes)

        initialRender() = renderNodeData(changes.addNodes.head.data)
        editable() = false
      }
    }

    def discardChanges(): Unit = {
      if(editable.now) {
        editable() = false
      }
    }

    p( // has different line-height than div and is used for text by markdown
      outline := "none", // hides contenteditable outline
      Rx {
//        println("editing: " + node.data.str) // TODO: this is a leak. Reproduction: Edit a node multiple times.
        if(editable()) VDomModifier(
          node.data.str, // Markdown source code
          contentEditable := true,
          whiteSpace.preWrap, // preserve white space in Markdown code
          backgroundColor := "#FFF",
          color := "#000",
          cursor.auto,

          onPostPatch.asHtml --> sideEffect { (_, node) => if(editable.now) node.focus() },

          onEnter.map(_.target.asInstanceOf[dom.html.Element].textContent) --> sideEffect { text => save(text) },
          onBlur --> sideEffect { discardChanges() },
          onFocus --> sideEffect { e => document.execCommand("selectAll", false, null) },
          onClick.stopPropagation --> sideEffect {} // prevent e.g. selecting node, but only when editing
        ) else initialRender()
      },
    )
  }

}
