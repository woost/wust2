package wust.frontend.views

import org.scalajs.dom
import org.scalajs.dom.ext.KeyCode
import outwatch.Sink
import outwatch.dom._
import wust.util.outwatchHelpers._

//TODO: merge with util.Tags
object Elements {
  def scrollToBottom(elem: dom.Element):Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  def textAreaWithEnter(actionSink: Sink[String]) = {
    // consistent across mobile + desktop:
    // - textarea: enter emits keyCode for Enter
    // - input: Enter triggers submit

    // val userInput = Handler.create[String]().unsafeRunSync()
    val setInputValue = Handler.create[String]().unsafeRunSync()
    // val clearHandler = setInputValue.map(_ => "a")
    // val insertFieldValue = userInput.merge(clearHandler)

    // val submitHandler = Handler.create[dom.Event]().unsafeRunSync()
    // val enterKeyHandler = Handler.create[dom.KeyboardEvent]().unsafeRunSync()
    // val actionHandler = submitHandler
    //   .merge(enterKeyHandler)
    //   // .replaceWithLatestFrom(insertFieldValue)
    //   .withLatestFrom(insertFieldValue).map(_._2)
    //   .filter(_.nonEmpty)

    // (actionSink <-- actionHandler).unsafeRunSync()
    // (setInputValue <-- actionHandler.delay(1000).map(_ => "")).unsafeRunSync() //TODO: only trigger clearHandler
    // (setInputValue <-- Observable.interval(1000).map(_.toString)).unsafeRunSync()
    // enterKeyHandler( event => event.preventDefault() )
    // submitHandler( event => event.preventDefault() )
        // insertFieldValue { text => println(s"Insertfield: '${text}'") }
        // enterKeyHandler { _ => println(s"EnterKeyHandler") }
        // submitHandler { _ => println(s"SumbitHandler") }
        // actionHandler { text => println(s"ActionHandler: ${text}") }

    setInputValue.debug("setInputValue")
    form(
      // input(tpe := "checkbox", checked <-- rxscalajs.Observable.interval(5000).map(_ % 2 == 0)),//clearHandler.map(_ => false)),
      button(tpe := "button", "clear", onClick("A") --> setInputValue),
      textArea(
        onKeyDown.map(_.toString) --> setInputValue
      ),
      textArea(
        tpe := "text", //TODO: dom-types attribute enums
        placeholder := "Create new post. Press Enter to submit.",
        width := "100%",
        // onInputString --> userInput, //TODO: outwatch: this is not triggered when setting the value with `value <-- observable`
        value <-- setInputValue,
        onKeyDown.filter(e => e.keyCode == KeyCode.Enter && !e.shiftKey).map(_.toString) --> setInputValue
      ),
      input(tpe := "submit", value := "insert"),
      // onSubmit --> submitHandler
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
