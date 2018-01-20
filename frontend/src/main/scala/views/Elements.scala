package wust.frontend.views

import wust.frontend.marked
import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.Sink
import outwatch.dom._
import outwatch.dom.dsl._
import wust.util.outwatchHelpers._
import monix.execution.Scheduler.Implicits.global

object Placeholders {
  val newPost = placeholder := "Create new post. Press Enter to submit."
  val newTag = placeholder := "Create new tag. Press Enter to submit."
}

object Rendered {
  def mdHtml(str: String) = prop("innerHTML") := marked(str)
  def mdHtml(str: Observable[String]) = prop("innerHTML") <-- str.map(marked(_))
  def mdString(str: String) = marked(str)
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

  def textAreaWithEnter(actionSink: Sink[String]) = {
    val userInput = Handler.create[String].unsafeRunSync()
    val clearHandler = userInput.map(_ => "")

    textArea(
      width := "100%",
      value <-- clearHandler,
      managed(actionSink <-- userInput),
      onKeyDown.collect { case e if e.keyCode == KeyCode.Enter && !e.shiftKey =>
        e.preventDefault(); e.target.asInstanceOf[dom.html.TextArea].value
      } --> userInput
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
