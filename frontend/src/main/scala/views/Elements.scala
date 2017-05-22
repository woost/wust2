package wust.frontend.views

import org.scalajs.dom.raw.{HTMLElement, HTMLFormElement, HTMLInputElement}
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.KeyboardEvent
import scalatags.JsDom.all._

object Elements {
  def textareaWithEnterSubmit = textarea(onkeydown := { (event: KeyboardEvent) =>
    if (event.keyCode == KeyCode.Enter && !event.shiftKey) {
      event.preventDefault()
      event.stopPropagation()
      val elem = event.target.asInstanceOf[HTMLElement]
      val form = elem.parentNode.asInstanceOf[HTMLFormElement]
      //TODO: calling submit directly skips onSubmit handlers
      // form.submit()
      form.querySelector("""input[type="submit"]""").asInstanceOf[HTMLInputElement].click()
    }
  })

  val inputText = input(`type` := "text")
  val inputPassword = input(`type` := "password")
  def buttonClick(name: String, handler: => Any) = button(name, onclick := handler _)
}
