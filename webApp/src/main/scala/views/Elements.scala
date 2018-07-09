package wust.webApp.views

import wust.webApp.marked
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.{ObserverSink, Sink}
import outwatch.dom.helpers.{EmitterBuilder, SimpleEmitterBuilder}
import outwatch.dom._
import outwatch.dom.dsl._
import monix.reactive.Observer
import wust.ids.NodeData
import wust.webApp.outwatchHelpers._
import org.scalajs.dom.window
import views.MediaViewer

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

  def viewConfigLink(viewConfig: ViewConfig): VNode =
    a(href := "#" + ViewConfig.toUrlHash(viewConfig))

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
