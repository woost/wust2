package wust.webApp.views

import wust.webApp.marked
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
import wust.webApp.GlobalState
import wust.ids._
import wust.util._

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

  def mdHtml(str: String) = div(prop("innerHTML") := marked(str))
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
      cls := "tag",
      contentString, //TODO trim correctly! fit for tag usage...
      onClick --> sideEffect { e =>
        state.page() = Page(Seq(tag.id)); e.stopPropagation()
      },
      backgroundColor := tagColor(tag.id),
      registerDraggableContainer(state),
      cls := "draggable",
      attr("woost_nodeid") := tag.id.toCuidString,
      attr("woost_dragtype") := "tag"
    )
  }

  def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId, graph: Graph): VNode = {
    nodeTag(state, tag)(
      span(
        "×",
        cls := "removebutton",
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

  def nodeCardCompact(state:GlobalState, node:Node, injected: VDomModifier = VDomModifier.empty, cutLength: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val content:VNode = if(cutLength) {
      val rawString = node.data.str.trim
      if (rawString.length > 20) span(rawString.take(17) + "...") else renderNodeData(node.data)
    } else renderNodeData(node.data)

    div(
      cls := "nodecardcompact",
      div(
        cls := "nodecardcompact-content",
        editableNode(state, node, content),
        cls := "draggable",
        attr("woost_nodeid") := node.id.toCuidString,
        attr("woost_dragtype") := "node",
        injected
      )
    )
  }

  def registerDraggableContainer(state: GlobalState) = {
    onInsert.asHtml --> sideEffect { elem =>
      state.draggable.addContainer(elem)
    }
  }

  def editableNode(state: GlobalState, node: Node, domContent: VNode)(
      implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNode(state, contentNode, domContent)
      case _                         => domContent
    }
  }

  def editableNode(state: GlobalState, node: Node.Content, domContent: VNode)(
      implicit ctx: Ctx.Owner
  ): VNode = {
    val editable = Var(false)
    val domElement = Var[html.Element](null)
    def save(): Unit = {
      val newContent: String =
        domElement.now.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String]
      val changes = GraphChanges.addNode(node.copy(data = NodeData.Markdown(newContent)))
      state.eventProcessor.changes.onNext(changes)
      editable() = false
    }
    domContent(
      Rx {
        editable().ifTrueSeq(
          Seq(
            contentEditable := true,
            backgroundColor := "#FFF",
            cursor.auto,
            onEnter --> sideEffect { save() },
            onBlur --> sideEffect { save() }
          )
        )
      },
      onInsert.asHtml --> domElement,
      onClick.stopPropagation.stopImmediatePropagation --> sideEffect {
        if (!editable.now) {
          editable() = true
          domElement.now.focus()
        }
      },
    )
  }

  //def inlineTextarea(submit: HTMLTextAreaElement => Any) = {
  //  textarea(
  //    onkeypress := { (e: KeyboardEvent) =>
  //      e.keyCode match {
  //        case KeyCode.Enter if !e.shiftKey =>
  //          e.preventDefault()
  //          e.stopPropagation()
  //          submit(e.target.asInstanceOf[HTMLTextAreaElement])
  //        case _ =>
  //      }
  //    },
  //    onblur := { (e: dom.Event) =>
  //      submit(e.target.asInstanceOf[HTMLTextAreaElement])
  //    }
  //  )
  //}

  //val inputText = input(`type` := "text")
  //val inputPassword = input(`type` := "password")
  //def buttonClick(name: String, handler: => Any) = button(name, onclick := handler _)
  //   val radio = input(`type` := "radio")
  //   def labelfor(id: String) = label(`for` := id)
  //   def aUrl(url:String) = a(href := url, url)
}
