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
import outwatch.dom.helpers.{EmitterBuilder, SimpleEmitterBuilder}
import rx._
import views.MediaViewer
import wust.graph._
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.parsers.NodeDataParser
import wust.webApp.views.Rendered._
import wust.webApp.{DragPayload, DragTarget, GlobalState, marked}

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
    SimpleEmitterBuilder { observer: Observer[dom.KeyboardEvent] =>
      VDomModifier(managed(IO(events.document.onKeyDown.filter(e => e.keyCode == KeyCode.Escape).subscribe(observer)))).unsafeRunSync
    }

  def valueWithEnter: SimpleEmitterBuilder[String, Modifier] = SimpleEmitterBuilder {
    (observer: Observer[String]) =>
      (for {
        userInput <- Handler.create[String]
        clearHandler = userInput.map(_ => "")
        actionSink = ObserverSink(observer)
        modifiers <- Seq(
          value <-- clearHandler,
          managed(actionSink <-- userInput),
          onEnter.value.filter(_.nonEmpty) --> userInput
        )
      } yield modifiers).unsafeRunSync() //TODO: https://github.com/OutWatch/outwatch/issues/195
  }

  def nodeTag(state: GlobalState, tag: Node): VNode = {
    val contentString = Rendered.trimToMaxLength(tag.data.str, Some(20))
    span(
      cls := "node tag",
      contentString, //TODO trim correctly! fit for tag usage...
      backgroundColor := tagColor(tag.id),
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      draggableAs(state, DragPayload.Tag(tag.id)),
      dragTarget(DragTarget.Tag(tag.id))
    )
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

  private def renderNodeCardCompact(state:GlobalState, node:Node, injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int])(implicit ctx: Ctx.Owner):VNode = {
    div(
      cls := "node nodecardcompact",
      draggableAs(state, DragPayload.Node(node.id)),
      dragTarget(DragTarget.Node(node.id)),
      div(
        cls := "nodecardcompact-content",
        injected
      ),
      onClick.stopPropagation --> sideEffect(()),
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig
    )
  }
  def nodeCardCompact(state:GlobalState, node:Node, injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner):VNode = {
    renderNodeCardCompact(state, node, VDomModifier(renderNodeData(node.data, maxLength),
      injected), maxLength)
  }
  def nodeCardCompactEditable(state:GlobalState, node:Node, editable:Var[Boolean], submit:Observer[GraphChanges], injected: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner):VNode = {
    renderNodeCardCompact(state, node, VDomModifier(editableNode(state, node, editable, submit, maxLength),
        injected), maxLength)
  }


  def dragTarget(dragTarget: DragTarget) = {
    import io.circe.syntax._
    attr(DragTarget.attrName) := dragTarget.asJson.noSpaces
  }

  def draggableAs(state:GlobalState, payload:DragPayload):VDomModifier = {
    import io.circe.syntax._
    Seq(
      cls := "draggable", // makes this element discoverable for the Draggable library
      outline := "none", // hide outline when focused
      attr(DragPayload.attrName) := payload.asJson.noSpaces,
      registerDraggableContainer(state)
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
    val currentText = Var("")
    val nodeData = PublishSubject[NodeData.Content]
    val renderedNodeData = nodeData.map(renderNodeData(_, maxLength))

    editable.filter(_ == true).foreach { _ =>
      currentText() = node.data.str // on edit show markdown code
    }

    def save(): Unit = {
      if(editable.now) {
        val graph = state.graphContent.now
        val changes = NodeDataParser.addNode(currentText.now, contextNodes = graph.nodes, baseNode = node)
        submit.onNext(changes)

        editable() = false
      }
    }

    def discardChanges():Unit = {
      if(editable.now) {
        editable() = false
        nodeData.onNext(node.data)
      }
    }

    div(
      renderedNodeData.startWith(renderNodeData(node.data, maxLength) :: Nil).map{ rendered =>
        Seq[VDomModifier](
          Rx[VDomModifier] {
            if(editable())
              Seq[VDomModifier](
                node.data.str,
          contentEditable := true,
          backgroundColor := "#FFF",
          cursor.auto
              )
          else
            rendered
      },
      onPostPatch.asHtml --> sideEffect{(_,node) => if(editable.now) node.focus()},
      onInput.map(_.target.textContent) --> currentText,

          onEnter --> sideEffect { save() },
          onBlur --> sideEffect { discardChanges() },
        )
      }
    )
  }

}
