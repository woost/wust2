package wust.frontend.views

import org.scalajs.d3v4._
import org.scalajs.dom.raw.HTMLTextAreaElement
import org.scalajs.dom.{Event, window}
import rx._
import rxext._
import scalaz.Tag
import wust.frontend.Color._
import wust.frontend._
import wust.frontend.views.Elements.textareaWithEnter
import wust.graph._
import scala.concurrent.ExecutionContext.Implicits.global
import scalaz.Tag
import scala.math.Ordering
import org.scalajs.dom.ext.KeyCode

import org.scalajs.dom.{ window, document, console }
import org.scalajs.dom.raw.{ Text, Element, HTMLElement }
import org.scalajs.dom.{ Event }
import org.scalajs.dom.raw.{ HTMLTextAreaElement }
import Elements.{ inlineTextarea, textareaWithEnter }
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import org.scalajs.dom.ext.KeyCode
import org.scalajs.dom.{ Event, KeyboardEvent }
import scala.util.control.NonFatal

import cats.effect.IO
import outwatch.dom._
import rxscalajs.Subject
import rxscalajs.Observable
import outwatch.Sink
import wust.util.outwatchHelpers._

// Outwatch TODOs:
// when writing: sink <-- obs; obs(action)
// action is always triggered first, even though it is registered after subscribe(<--)
//
// observable.filter does not accept partial functions.filter{case (_,text) => text.nonEmpty}
//
// like Handler, Subject needs to be wrapped in IO
//
// handler[A].map(A => B) should return Sink[A] with Observable[B]

object ChatView {
  def textAreaWithEnter(actionSink: Sink[String]): VNode = {
    // consistent across mobile + desktop
    // textarea: enter emits keyCode for Enter
    // input: Enter triggers submit
    
    for {
      userInput <- createStringHandler()
      setInputValue <- createStringHandler()
      clearHandler = setInputValue.map(_ => "")
      insertFieldValue = userInput.merge(clearHandler)

      submitHandler <- createHandler[Event]()
      enterKeyHandler <- createKeyboardHandler()
      actionHandler = submitHandler
        .merge(enterKeyHandler)
        .withLatestFrom(insertFieldValue) // TODO: is there a transformation which directly forwards the last value without tuple?
        .map{case (_,text) => text}
        .filter(_.nonEmpty)

      _ <- Pure(actionSink <-- actionHandler)
      _ <- Pure(setInputValue <-- actionHandler) //TODO: only trigger clearHandler
      _ <- Pure(IO{
        enterKeyHandler{ event => event.preventDefault() }
        // insertFieldValue { case text => println(s"Insertfield: '${text}'") }
        // enterKeyHandler { _ => println(s"EnterKeyHandler") }
        // submitHandler { _ => println(s"SumbitHandler") }
        // actionHandler { case (_, text) => println(s"ActionHandler: ${text}") }
      })
      form <- form(
        textArea(
          placeholder := "Create new post. Press Enter to submit.",
          Style("width", "100%"),
          onInputString --> userInput, //TODO: outwatch: this is not triggered when setting the value with `value <-- observable`
          value <-- clearHandler,
          onKeyDown.filter(_.keyCode == KeyCode.Enter) --> enterKeyHandler //TODO: not shift key
        ),
        input(tpe := "submit", "insert"),
        onSubmit --> submitHandler
      )
    } yield {
      form
    }
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner) = {

    val focusedParentIds = state.graphSelection.map(_.parentIds)

    val headLineText = Rx {
      val parents = focusedParentIds().map(state.rawGraph().postsById)
      val parentTitles = parents.map(_.title).mkString(", ")
      parentTitles
    }.toObservable

    val bgColor = Rx {
      val mixedDirectParentColors = mixColors(focusedParentIds().map(baseColor))
      mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString
    }.toObservable

    val chatHistory: Rx[Seq[Post]] = state.displayGraphWithoutParents.rx.map{ dg =>
      val graph = dg.graph
      graph.posts.toSeq.sortBy(p => Tag.unwrap(p.id))
    }

    val latestPost = Rx { chatHistory().lastOption }

    def scrollToBottom(elem: Element) {
      //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
      try {
        elem.scrollTop = elem.scrollHeight
      } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
    }

    val chatHistoryDiv = for {
      graphSelectionHandler <- (state.graphSelection: Handler[GraphSelection])
      div <- div(
        update --> { (e: Element) => println("update hook"); setTimeout(100) { scrollToBottom(e) } },
        Style("height", "100%"),
        Style("overflow", "auto"),
        Style("padding", "20px"),
        Style("backgroundColor", "white"),

        children <-- chatHistory.toObservable.map {
          _.map{ post =>
            val isMine = state.ownPosts(post.id)
            div(
              p(
                Style("maxWidth", "60%"),
                post.title,
                Style("backgroundColor", (if (isMine) "rgb(192, 232, 255)" else "#EEE")),
                Style("float", (if (isMine) "right" else "left")),
                Style("clear", "both"),
                Style("padding", "5px 10px"),
                Style("borderRadius", "7px"),
                Style("margin", "5px 0px"),
                // TODO: What about cursor when selecting text?
                Style("cursor", "pointer"),
                onClick(GraphSelection.Union(Set(post.id))) --> graphSelectionHandler //TODO: this is not triggered
              )
            )
          }
        }
      )
    } yield div

    val insertForm = textAreaWithEnter{ text: String =>
      // println(s"SUBMITTING: $text")
      val newPost = Post.newId(text)
      state.persistence.addChangesEnriched(
        addPosts = Set(newPost),
        addConnections = latestPost.now.map(latest => Connection(latest.id, newPost.id)).toSet
      )
    }

    (for {
      chat <- div(
        Style("height", "100%"),
        backgroundColor <-- bgColor,

        div(
          Style("margin", "0 auto"),
          Style("maxWidth", "48rem"),
          Style("width", "48rem"),
          Style("height", "100%"),

          Style("display", "flex"),
          Style("flexDirection", "column"),
          Style("justifyContent", "flexStart"),
          Style("alignItems", "stretch"),
          Style("alignContent", "stretch"),

          h1(child <-- headLineText),

          chatHistoryDiv,
          insertForm
        )
      )
    } yield chat).render
  }
}
