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
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._
import emojijs.EmojiConvertor
import monix.execution.Cancelable
import wust.util._

import scala.scalajs.js

object Placeholders {
  val newNode = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Rendered {
  val emoji = new EmojiConvertor()
  val implicitUserName = "Unregistered User"

  val htmlPostData: NodeData => String = {
    case NodeData.Markdown(content)  => mdString(content)
    case NodeData.PlainText(content) =>
      // assure html in text is escaped by creating a text node, appending it to an element and reading the escaped innerHTML.
      val text = window.document.createTextNode(content)
      val wrap = window.document.createElement("div")
      wrap.appendChild(text)
      wrap.innerHTML
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
      // sanitize = true
      // sanitizer = new SanitizeState().getSanitizer(): js.Function1[String, String]
    })

    emoji.img_sets.apple.sheet = "/emoji-datasource/sheet_apple_32.png"
    emoji.img_sets.apple.sheet_size = 32
    emoji.img_set = "apple"
    emoji.use_sheet = true
    emoji.init_env()
    emoji.include_title = true
    emoji.text_mode = false
    emoji.colons_mode = false
    emoji.allow_native = false
    emoji.wrap_native = true
    emoji.avoid_ms_emoji = true
    emoji.replace_mode = "img"

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
    case user: NodeData.User         => div(if(user.isImplicit) implicitUserName else user.name)
  }

  def replace_full_emoji(str: String): String = emoji.replace_unified(emoji.replace_colons(Marked(emoji.replace_emoticons_with_colons(str))))

  def mdHtml(str: String) = div(div(prop("innerHTML") := replace_full_emoji(str))) // intentionally double wrapped. Because innerHtml does not compose with other modifiers
  def mdString(str: String): String = replace_full_emoji(str)
}

object Components {

  private val woostPathCurve = "m51.843 221.96c81.204 0-6.6913-63.86 18.402 13.37 25.093 77.23 58.666-26.098-7.029 21.633-65.695 47.73 42.949 47.73-22.746 0-65.695-47.731-32.122 55.597-7.029-21.633 25.093-77.23-62.802-13.37 18.402-13.37z"
  val woostIcon = {
    import svg._
    svg.static(keyValue)(VDomModifier(
      cls := "svg-inline--fa fa-w-14",
      viewBox := "0 0 10 10",
      g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
        path(d := woostPathCurve, fill := "currentColor")
      )
    ))
  }


  val woostLoadingAnimation:VNode = {
    import svg._
    div(
      svg(
        width := "100px", height := "100px", viewBox := "0 0 10 10",
        g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
          path(id := "woost-loading-animation", d := woostPathCurve, fill := "none", stroke := "#6636b7", strokeLineCap := "round", strokeWidth := "3.5865", pathLength := "100")
        )
      ),
      p("Loading", dsl.color := "rgba(0,0,0,0.4)", textAlign.center)
    )
  }


  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      onClick handleWith { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTagDot(state: GlobalState, tag: Node): VNode = {
    span(
      cls := "node tagdot",
      backgroundColor := tagColor(tag.id).toHex,
      title := tag.data.str,
      onClick handleWith { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTag(state: GlobalState, tag: Node): VNode = {
    val contentString = Rendered.trimToMaxLength(tag.data.str, Some(20))
    renderNodeTag(state, tag, contentString)
  }

  def editableNodeTag(state: GlobalState, tag: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = Some(20))(implicit ctx: Ctx.Owner): VNode = {
    renderNodeTag(state, tag, editableNode(state, tag, editMode, submit, maxLength))
  }

  def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId): VNode = {
    nodeTag(state, tag)(
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation handleWith {
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

  def renderNodeCard(node: Node, injected: VDomModifier): VNode = {
    div(
      keyed(node.id),
      cls := "node nodecard",
      div(
        keyed(node.id),
        cls := "nodecard-content",
        injected
      ),
    )
  }
  def nodeCard(node: Node, injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
    renderNodeCard(
      node,
      injected = VDomModifier(renderNodeData(node.data, maxLength), injected)
    )
  }
  def nodeCardEditable(state: GlobalState, node: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(
      node,
      injected = VDomModifier(
        editableNode(state, node, editMode, submit, maxLength),
        injected
      )
    ).apply(
      Rx { editMode().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },
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

  def draggableAs(payload: => DragPayload): VDomModifier = {
    Seq(
      cls := "draggable", // makes this element discoverable for the Draggable library
      onDomMount.asHtml handleWith{ elem =>
        writeDragPayload(elem, payload)
      },
      //TODO: onDomUpdate should not be necessary here. This is a workaround until outwatch executes onDomMount when triggered in an Rx
      onDomUpdate.asHtml handleWith{ elem =>
        writeDragPayload(elem, payload)
      },
    )
  }

  def dragTarget(dragTarget: DragTarget): VDomModifier = {
    VDomModifier(
      onDomMount.asHtml handleWith{ elem =>
        writeDragTarget(elem, dragTarget)
      },
      //TODO: onDomUpdate should not be necessary here. This is a workaround until outwatch executes onDomMount when triggered in an Rx
      onDomUpdate.asHtml handleWith{ elem =>
        writeDragTarget(elem, dragTarget)
      }
    )
  }

  def registerDraggableContainer(state: GlobalState): VDomModifier = {
    Seq(
      //    border := "2px solid blue",
      outline := "none", // hides focus outline
      cls := "draggable-container",
      managedElement.asHtml { elem =>
        state.draggable.addContainer(elem)
        Cancelable { () =>
          state.draggable.removeContainer(elem)
        }
      }
    )
  }

  def registerSortableContainer(state: GlobalState, container: DragContainer): VDomModifier = {
    Seq(
      //          border := "2px solid violet",
      outline := "none", // hides focus outline
      cls := "sortable-container",
      managedElement.asHtml { elem =>
        writeDragContainer(elem, container)
        state.sortable.addContainer(elem)
        Cancelable { () =>
          state.sortable.removeContainer(elem)
        }
      }
    )
  }

  def withOrder(state: GlobalState, page: Page): VDomModifier = {
    val parents = page.parentIds

    state.graph.now.lookup.beforeIdx


    div()
  }

  def editableNodeOnClick(state: GlobalState, node: Node, submit: Observer[GraphChanges])(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val editMode = Var(false)
    editableNode(state, node, editMode, submit)(ctx)(
      onClick.stopPropagation.stopImmediatePropagation handleWith {
        if(!editMode.now) {
          editMode() = true
        }
      }
    )
  }


  def editableNode(state: GlobalState, node: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = None)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNodeContent(state, contentNode, editMode, submit, maxLength)
      case _                         => renderNodeData(node.data, maxLength)
    }
  }

  def editableNodeContent(state: GlobalState, node: Node.Content, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int])(
    implicit ctx: Ctx.Owner
  ): VNode = {

    val initialRender: Var[VDomModifier] = Var(renderNodeData(node.data, maxLength))

    def save(text: String): Unit = {
      if(editMode.now) {
        val changes = GraphChanges.addNode(Node.Content(node.id, NodeData.Markdown(text)))
        submit.onNext(changes)

        initialRender() = renderNodeData(changes.addNodes.head.data)
        editMode() = false
      }
    }

    def discardChanges(): Unit = {
      if(editMode.now) {
        editMode() = false
      }
    }

    p( // has different line-height than div and is used for text by markdown
      outline := "none", // hides contenteditable outline
      keyed, // when updates come in, don't disturb current editing session
      Rx {
        if(editMode()) VDomModifier(
          node.data.str, // Markdown source code
          contentEditable := true,
          whiteSpace.preWrap, // preserve white space in Markdown code
          backgroundColor := "#FFF",
          color := "#000",
          cursor.auto,

          onEnter.map(_.target.asInstanceOf[dom.html.Element].textContent) handleWith { text => save(text) },
          onBlur handleWith { discardChanges() },
          onFocus handleWith { e => document.execCommand("selectAll", false, null) },
          onClick.stopPropagation handleWith {} // prevent e.g. selecting node, but only when editing
        ) else initialRender()
      },
      onDomUpdate.asHtml --> inNextAnimationFrame { node =>
        if(editMode.now) node.focus()
      },
    )
  }

}
