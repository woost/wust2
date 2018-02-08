package wust.frontend.views.graphview

import scala.scalajs.js.JSConverters._
import d3v4._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.{Element, window, console}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.dsl.styles.extra._
import rx._
import vectory._
import wust.frontend.Color._
import wust.frontend.views.View
import wust.frontend.{DevOnly, DevPrintln, GlobalState}
import wust.graph._
import wust.util.outwatchHelpers._
import wust.util.time.time
import wust.ids._

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import wust.frontend.views.Elements._
import wust.frontend.views.Placeholders


object PostCreationMenu {

  case class Menu(pos: Vec2, author: UserId)

  def apply(state: GlobalState, graphState:GraphState, m: Menu, transformRx: Rx[Transform])(implicit owner: Ctx.Owner) = {
    import graphState.rxPostIdToSimPost

    val transformStyle = transformRx.map { t =>
      val xOffset = -300 / 2
      val yOffset = -30
      val x = xOffset + t.applyX(m.pos.x)
      val y = yOffset + t.applyY(m.pos.y)
      s"translate(${x}px, ${y}px)"
    }

    val inputHandler = Handler.create[String].unsafeRunSync()
    var ySimPostOffset: Double = 50
    inputHandler.foreach{ content =>
      val newPost = Post(PostId.fresh, content, m.author)
      val changes = GraphChanges(addPosts = Set(newPost))
      state.eventProcessor.enriched.changes.unsafeOnNext(changes)

      // TODO: move created post below menu (not working yet)
      val simPostOpt = rxPostIdToSimPost.now.get(newPost.id)
      simPostOpt.foreach { simPost =>
        simPost.fx = m.pos.x
        simPost.fy = m.pos.y + ySimPostOffset / transformRx.now.k + simPost.size.height / 2
        ySimPostOffset += (simPost.size.height + 10) * transformRx.now.k
      }
    }

    //TODO: hide postMenu with ESC key
    //TODO: checkboxes for parents
    //TODO: select for group
    //TODO: close button

    div(
      position.absolute,
      transform <-- transformStyle.toObservable,
      width := "300px",
      div(
        textAreaWithEnter(inputHandler)(Placeholders.newPost, onInsert.asHtml --> sideEffect(_.focus())),
        cls := "shadow",
        padding := "3px 5px",
        border := "2px solid #DDDDDD",
        borderRadius := "5px",
        backgroundColor := "#F8F8F8"
      ),
    )
  }
}
