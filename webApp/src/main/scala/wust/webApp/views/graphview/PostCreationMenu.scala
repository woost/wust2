package wust.webApp.views.graphview

import d3v4.d3
import outwatch._
import outwatch.dsl._
import outwatch.dsl.styles.extra._
import outwatch.reactive.handler._
import rx._
import vectory._
import wust.graph._
import wust.ids._
import wust.webApp.state.{FocusState, GlobalState, Placeholder}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

object PostCreationMenu {
  def apply(focusState: FocusState, pos: Vec2, transformRx: Rx[d3.Transform])(
      implicit owner: Ctx.Owner
  ) = {
//    import graphState.rxNodeIdToSimPost

    val transformStyle = transformRx.map { t =>
      val xOffset = -300 / 2
      val yOffset = -30
      val x = xOffset + t.applyX(pos.x)
      val y = yOffset + t.applyY(pos.y)
      s"translate(${x}px, ${y}px)"
    }

    val inputHandler = Handler.unsafe[String]
    var ySimPostOffset: Double = 50

    //TODO: hide postMenu with ESC key
    //TODO: checkboxes for parents
    //TODO: select for group
    //TODO: close button

    div(
      emitter(inputHandler).foreach { content =>
        if(content.nonEmpty) {
          val author = GlobalState.user.now
          val changes = GraphChanges.addNodeWithParent(Node.MarkdownTask(content), ParentId(focusState.focusedId))
          GlobalState.submitChanges(changes)
        }

        // TODO: move created post below menu (not working yet)
        //      val simPostOpt = rxNodeIdToSimPost.now.get(newPost.id)
        //      simPostOpt.foreach { simPost =>
        //        simPost.fx = m.pos.x
        //        simPost.fy = m.pos.y + ySimPostOffset / transformRx.now.k + simPost.size.height / 2
        //        ySimPostOffset += (simPost.size.height + 10) * transformRx.now.k
        //      }
      },
      position.absolute,
      top := "0",
      left := "0",
      //TODO: prevent drag events to bubble to background
      onClick.stopPropagation.discard, // prevent click from bubbling to background
      transform <-- transformStyle,
      width := "300px",
      div(
        cls := "ui form",
        textArea(
          cls := "fluid field",
          valueWithEnter --> inputHandler,
          placeholder := Placeholder.newTask.long,
          onDomMount.asHtml foreach(_.focus()),
          resize := "none",
          margin := "0px",
          rows := 2,
        ),
        cls := "shadow",
        padding := "5px",
        border := "2px solid #DDDDDD",
        borderRadius := "5px",
        backgroundColor := "#F8F8F8"
      ),
    )
  }
}
