package wust.webApp.views

import wust.webApp.{DragPayload, DragTarget, GlobalState, marked}
import wust.webApp.views.Rendered._
import org.scalajs.dom
import wust.sdk.NodeColor._
import rx._
import org.scalajs.dom.ext.KeyCode
import outwatch.{ObserverSink, Sink}
import outwatch.dom.helpers.{EmitterBuilder, SimpleEmitterBuilder}
import outwatch.dom._
import outwatch.dom.dsl._
import wust.graph._
import monix.reactive.Observer
import wust.ids.NodeData
import wust.webApp.outwatchHelpers._
import org.scalajs.dom.window
import org.scalajs.dom.console
import org.scalajs.dom.html

import scala.scalajs.js
import views.MediaViewer
import wust.ids._
import wust.util._
import wust.webApp.parsers.NodeDataParser

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

  val renderNodeData: NodeData => VNode = {
    case NodeData.Markdown(content)  => mdHtml(content)
    case NodeData.PlainText(content) => div(content)
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

  def onEnter: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown.collect {
      case e if e.keyCode == KeyCode.Enter && !e.shiftKey => e.preventDefault(); e
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
    val rawString = tag.data.str.trim
    val contentString = if (rawString.length > 20) rawString.take(17) + "..." else rawString
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

  def nodeCardCompact(state:GlobalState, node:Node, injected: VDomModifier = VDomModifier.empty, cutLength: Boolean = false, editable:Var[Boolean] = Var(false))(implicit ctx: Ctx.Owner) = {
    val content:VNode = if(cutLength) {
      val rawString = node.data.str.trim
      if (rawString.length > 20) span(rawString.take(17) + "...") else renderNodeData(node.data)
    } else renderNodeData(node.data)

    div(
      cls := "node nodecardcompact",
      draggableAs(state, DragPayload.Node(node.id)),
      dragTarget(DragTarget.Node(node.id)),
      div(
        cls := "nodecardcompact-content",
        editableNode(state, node, content, editable),
        injected
      ),
      onClick.stopPropagation --> sideEffect(()),
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig
    )
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


  def editableNodeOnClick(state: GlobalState, node: Node, domContent: VNode)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val editable = Var(false)
    editableNode(state, node, domContent, editable)(ctx){
      onClick.stopPropagation.stopImmediatePropagation --> sideEffect {
        if (!editable.now) {
          editable() = true
        }
      }
    }
  }


  def editableNode(state: GlobalState, node: Node, domContent: VNode, editable:Var[Boolean])(
      implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNode(state, contentNode, domContent, editable)
      case _                         => domContent
    }
  }

  def editableNode(state: GlobalState, node: Node.Content, domContent: VNode, editable:Var[Boolean])(
      implicit ctx: Ctx.Owner
  ): VNode = {
    val domElement = Var[html.Element](null)
    def save(): Unit = {
      val userInput: String = domElement.now.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String] //TODO: https://github.com/scala-js/scala-js-dom/issues/19

      val graph = state.graphContent.now
      val changes = NodeDataParser.addNode(userInput, contextNodes = graph.nodes, baseNode = node)
      state.eventProcessor.enriched.changes.onNext(changes)

      editable() = false
    }
    editable.foreach(editable => if(editable) domElement.now.asInstanceOf[js.Dynamic].innerText = node.data.str) // replace the trimmed/formatted content with the node's source code
    val resultNode = domContent(
      Rx {
        editable().ifTrueSeq(Seq(
            contentEditable := true,
            backgroundColor := "#FFF",
            cursor.auto
        ))
      },
      onInsert.asHtml --> domElement,
      onPostPatch.asHtml --> sideEffect{(_,node) => if(editable.now) node.focus()},
      onClick --> sideEffect{ e => if(editable.now) e.stopPropagation() },
      onEnter --> sideEffect { if(editable.now) save() },
      onBlur --> sideEffect { if(editable.now) save() }
    )
    resultNode
  }
}
