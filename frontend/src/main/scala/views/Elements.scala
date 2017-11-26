package wust.frontend.views

import org.scalajs.dom.Event
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.raw.Element
import outwatch.Sink
import outwatch.dom._
import wust.util.outwatchHelpers._

//TODO: merge with util.Tags
object Elements {
  def scrollToBottom(elem: Element):Unit = {
    //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
    try {
      elem.scrollTop = elem.scrollHeight
    } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
  }

  def textAreaWithEnter(actionSink: Sink[String]) = {
    // consistent across mobile + desktop:
    // - textarea: enter emits keyCode for Enter
    // - input: Enter triggers submit

    val userInput = createStringHandler().unsafeRunSync()
    val setInputValue = createStringHandler().unsafeRunSync()
    val clearHandler = setInputValue.map(_ => "")//scala.util.Random.nextInt.toString)
    val insertFieldValue = userInput.merge(clearHandler)

    val submitHandler = createHandler[Event]().unsafeRunSync()
    val enterKeyHandler = createKeyboardHandler().unsafeRunSync()
    val actionHandler = submitHandler
      .merge(enterKeyHandler)
      .replaceWithLatestFrom(insertFieldValue)
      .filter(_.nonEmpty)

    (actionSink <-- actionHandler).unsafeRunSync()
    (setInputValue <-- actionHandler.delay(1000)).unsafeRunSync() //TODO: only trigger clearHandler
    enterKeyHandler( event => event.preventDefault() )
    submitHandler( event => event.preventDefault() )
    //     insertFieldValue { text => println(s"Insertfield: '${text}'") }
    //     enterKeyHandler { _ => println(s"EnterKeyHandler") }
    //     submitHandler { _ => println(s"SumbitHandler") }
    //     actionHandler { text => println(s"ActionHandler: ${text}") }

    form(
      textarea(
        placeholder := "Create new post. Press Enter to submit.",
        // data.bla <-- Observable.interval(2000).map(_.toString),
        // div(child <-- clearHandler),
        width := "100%",
        inputString --> userInput, //TODO: outwatch: this is not triggered when setting the value with `value <-- observable`
        value <-- clearHandler,
        keydown.filter(e => e.keyCode == KeyCode.Enter && !e.shiftKey) --> enterKeyHandler
      ),
      input(tpe := "submit", value := "insert"),
      onSubmit --> submitHandler
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
