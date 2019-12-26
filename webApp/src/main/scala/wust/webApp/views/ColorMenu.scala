package wust.webApp.views

//import acyclic.file
import fontAwesome._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.ext.monix._
import outwatch.reactive.{ SinkObserver, _ }
import outwatch.reactive.handler._
import rx._
import wust.css.{ CommonStyles, Styles }
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.{ SearchOptions, SearchSourceEntry }
import wust.facades.jquery.JQuerySelection
import wust.graph._
import wust.ids.{ Feature, _ }
import wust.sdk.{ BaseColors, NodeColor }
import wust.util.StringOps._
import wust.util._
import wust.util.macros.{ InlineList, SubObjects }
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.state._
import wust.webApp.views.UploadComponents._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ BrowserDetect, Elements, UI }
import wust.facades.segment.Segment
import java.util.regex.Pattern
import colorado.HCL

import scala.collection.breakOut
import scala.scalajs.js

object ColorMenu {
  def apply(baseColor: HCL, node: Node.Content)(implicit ctx: Ctx.Owner) = {
    val colorCount = Var(8)
    val stepSize = Rx{ 1.0 / colorCount() }
    val squareSize = "30px"
    div(
      width := "160px",
      div(
        Styles.flex,
        flexWrap.wrap,
        color.white,
        fontWeight.bold,
        Rx{
          Range(0, colorCount()).map { colorIndex =>
            val hueFraction = colorIndex * stepSize()
            val hue = NodeColor.goodHue(hueFraction)
            div(
              "A",
              width := squareSize,
              height := squareSize,
              Styles.flex,
              alignItems.center,
              justifyContent.center,
              borderRadius := "4px",
              margin := "5px",
              backgroundColor := baseColor.copy(h = hue).toHex,
              onClickDefault.foreach {
                val newNode = node.updateSettings(_.updateGlobal(globalSettings => globalSettings.copy(colorHue = Some(hue))))
                val change = GraphChanges.addNode(newNode)
                GlobalState.submitChanges(change)
                ()
              }
            )
          }
        }
      ),
      Rx{
        VDomModifier.ifTrue(colorCount() == 8)(
          div(textAlign.right, "more", opacity := 0.5, onClickDefault.use(16) --> colorCount, marginRight := "5px")
        )
      }
    )
  }
}
