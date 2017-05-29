package wust.frontend.views

import org.scalajs.dom.raw.{ HTMLElement, HTMLFormElement, HTMLInputElement, HTMLTextAreaElement }
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.KeyboardEvent
import scalatags.JsDom.all._
import scalatags.JsDom.TypedTag
import org.scalajs.dom.html.TextArea

object Elements {
  def textareaWithEnterSubmit = textareaWithEnter { elem =>
    val form = elem.parentNode.asInstanceOf[HTMLFormElement]
    //TODO: calling submit directly skips onSubmit handlers
    // form.submit()
    form.querySelector("""input[type="submit"]""").asInstanceOf[HTMLInputElement].click()
    false
  }

  def onKey(e: KeyboardEvent)(f: PartialFunction[Long, Boolean]) = {
    val shouldHandle = f.lift(e.keyCode).getOrElse(true)
    if (!shouldHandle) {
      e.preventDefault()
      e.stopPropagation()
    }
  }

  def textareaWithEnter(f: HTMLTextAreaElement => Boolean): TypedTag[TextArea] = textarea(onkeydown := { (event: KeyboardEvent) =>
    onKey(event) {
      case KeyCode.Enter =>
        val elem = event.target.asInstanceOf[HTMLTextAreaElement]
        f(elem)
    }
  })

  val inputText = input(`type` := "text")
  val inputPassword = input(`type` := "password")
  def buttonClick(name: String, handler: => Any) = button(name, onclick := handler _)
}
