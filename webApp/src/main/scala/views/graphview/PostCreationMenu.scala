package wust.webApp.views.graphview

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
import wust.sdk.PostColor._
import wust.webApp.views.View
import wust.webApp.{DevOnly, DevPrintln, GlobalState}
import wust.graph._
import wust.webApp.outwatchHelpers._
import wust.util.time.time
import wust.ids._

import scala.concurrent.ExecutionContext
import scala.scalajs.js
import wust.webApp.views.Elements._
import wust.webApp.views.Placeholders


object PostCreationMenu {
  def apply(state: GlobalState, pos: Vec2, transformRx: Rx[d3v4.Transform])(implicit owner: Ctx.Owner) = {
//    import graphState.rxPostIdToSimPost

    val transformStyle = transformRx.map { t =>
      val xOffset = -300 / 2
      val yOffset = -30
      val x = xOffset + t.applyX(pos.x)
      val y = yOffset + t.applyY(pos.y)
      s"translate(${x}px, ${y}px)"
    }

    val inputHandler = Handler.create[String].unsafeRunSync()
    var ySimPostOffset: Double = 50
    inputHandler.foreach{ content =>
      val author = state.currentUser.now
      val changes = GraphChanges.addPost(content, author.id)
      state.eventProcessor.enriched.changes.onNext(changes)

      // TODO: move created post below menu (not working yet)
//      val simPostOpt = rxPostIdToSimPost.now.get(newPost.id)
//      simPostOpt.foreach { simPost =>
//        simPost.fx = m.pos.x
//        simPost.fy = m.pos.y + ySimPostOffset / transformRx.now.k + simPost.size.height / 2
//        ySimPostOffset += (simPost.size.height + 10) * transformRx.now.k
//      }
    }

    //TODO: hide postMenu with ESC key
    //TODO: checkboxes for parents
    //TODO: select for group
    //TODO: close button

    div(
      position.absolute,
      //TODO: prevent drag events to bubble to background
      onClick --> sideEffect(_.stopPropagation()), // prevent click from bubbling to background
      transform <-- transformStyle,
      width := "300px",
      div(
        textAreaWithEnter(inputHandler)(
          Placeholders.newPost,
          onInsert.asHtml --> sideEffect(_.focus()),
          style("resize") := "none", //TODO: outwatch resize?
          margin := "0px"
        ),
        cls := "shadow",
        padding := "3px 5px 0px 5px",
        border := "2px solid #DDDDDD",
        borderRadius := "5px",
        backgroundColor := "#F8F8F8"
      ),
    )
  }
}
