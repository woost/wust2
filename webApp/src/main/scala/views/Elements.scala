package wust.webApp.views

import cats.effect.IO
import monix.reactive.Observer
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.window
import outwatch.ObserverSink
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{CustomEmitterBuilder, EmitterBuilder}
import rx._
import views.MediaViewer
import wust.graph._
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Rendered._
import wust.webApp._

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

object Elements {
  // Enter-behavior which is consistent across mobile and desktop:
  // - textarea: enter emits keyCode for Enter
  // - input: Enter triggers submit

  def scrollToBottom(elem: dom.Element): Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  val onEnter: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown
      .filter( e => e.keyCode == KeyCode.Enter && !e.shiftKey)
      .preventDefault

  val onEscape: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown
      .filter( _.keyCode == KeyCode.Escape )
      .preventDefault

  val onGlobalEscape = 
    CustomEmitterBuilder { sink: Sink[dom.KeyboardEvent] =>
      VDomModifier(
        managed(sink <-- events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape))
      )
    }

  def valueWithEnter: CustomEmitterBuilder[String, Modifier] = CustomEmitterBuilder {
    (sink: Sink[String]) =>
      for {
        userInput <- Handler.create[String]
        clearHandler = userInput.map(_ => "")
        modifiers <- Seq(
          value <-- clearHandler,
          onEnter.value.filter(_.nonEmpty) --> userInput,
          managed(sink <-- userInput)
        )
      } yield modifiers
  }

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


  def dragTarget(dragTarget: DragTarget) = {
    import io.circe.syntax._
    import DragItem.targetEncoder
    attr(DragItem.targetAttrName) := dragTarget.asJson.noSpaces
  }

  def draggableAs(state:GlobalState, payload:DragPayload):VDomModifier = {
    import io.circe.syntax._
    import DragItem.payloadEncoder
    Seq(
      cls := "draggable", // makes this element discoverable for the Draggable library
      attr(DragItem.payloadAttrName) := payload.asJson.noSpaces,
    )
  }

  def registerDraggableContainer(state: GlobalState):VDomModifier = Seq(
    onInsert.asHtml --> sideEffect { elem =>
      state.draggable.addContainer(elem)
    },
    onDestroy.asHtml --> sideEffect { elem =>
      state.draggable.removeContainer(elem)
    }
  )


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
        ) else initialRender()
      }
    )
  }

}
