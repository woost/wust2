package wust.webApp.views

import cats.data.EitherT
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.reactive._
import outwatch.reactive.handler._
import rx._
import wust.css.Styles
import wust.graph._
import wust.api.{StripeSessionId, StripeCheckoutResponse}
import wust.ids._
import wust.webApp.Client
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.state.GraphChangesAutomation
import wust.webUtil.UI
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import monix.eval.Task

import fontAwesome._
import wust.facades.stripe._
import org.scalajs.dom
import scala.scalajs.js
import scala.util.{Success, Failure}

import scala.concurrent.Future

object DoodleView {

  sealed trait State
  object State {
    case object Landing extends State
    case object App extends State
  }


  def landing(implicit ctx: Ctx.Owner) = {

    div(
      Styles.growFull,
      padding := "20px",
      Styles.flex,
    )
  }

  def app(implicit ctx: Ctx.Owner) = div(
    UI.segment("Create a Tootle", doodleForm),
  )

  def doodleForm(implicit ctx: Ctx.Owner) = {

    val menu = StepMenu.render(Array(
      StepMenu.Step(
        "What's the occasion?",
        div(
          1
        )
      ),
      StepMenu.Step(
        "What do you want to work on?",
        div(
          2
        )
      ),
      StepMenu.Step(
        "Collaboration Settings",
        div(
          3
        )
      ),
      StepMenu.Step(
        "Tell your participants who you are",
        div(
          4
        )
      )
    ))

    div(
      width := "600px",
      height := "500px",

      menu
    )
  }
}
