package wust.webApp.views

import wust.webApp.marked
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.{ObserverSink, Sink}
import outwatch.dom.helpers.{EmitterBuilder, SimpleEmitterBuilder}
import outwatch.dom._
import outwatch.dom.dsl._
import monix.reactive.Observer
import wust.graph.PostContent
import wust.webApp.outwatchHelpers._

object Placeholders {
  val newPost = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Rendered {
  val showPostContent: PostContent => VNode = {
    case PostContent.Markdown(content) => mdHtml(content)
    case PostContent.Text(content)  => span(content)
  }
  def showPostContent(o: Observable[PostContent]):Observable[VNode] = o map showPostContent

  def mdHtml(str: String) = span(prop("innerHTML") := marked(str))
  def mdHtml(str: Observable[String]) = span(prop("innerHTML") <-- str.map(marked(_)))
  def mdString(str: String):String = marked(str)
}

object Elements {
  // Enter-behavior which is consistent across mobile and desktop:
  // - textarea: enter emits keyCode for Enter
  // - input: Enter triggers submit

  def scrollToBottom(elem: dom.Element):Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  def onEnter: EmitterBuilder[dom.KeyboardEvent, dom.KeyboardEvent, Emitter] =
    onKeyDown.collect { case e if e.keyCode == KeyCode.Enter && !e.shiftKey => e.preventDefault(); e }

  def valueWithEnter: SimpleEmitterBuilder[String, Modifier] = SimpleEmitterBuilder { (observer: Observer[String]) =>
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
