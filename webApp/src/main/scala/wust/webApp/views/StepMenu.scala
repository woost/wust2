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

object StepMenu {

  case class Step(
    name: String,
    content: VDomModifier,
    finished: Rx[Boolean] = Var(true)
  )

  def render(
    steps: Array[Step],
  )(implicit ctx: Ctx.Owner) = {
    require(steps.nonEmpty, "Must have non-empty steps in step menu")

    val stepLength = steps.length

    val currentStepIndex = Var(0)
    val currentStep = currentStepIndex.map(steps(_))
    val canGoLeft = currentStepIndex.map(_ > 0)
    val canGoRight = currentStepIndex.map(_ < stepLength - 1)

    val header = div(
      Styles.flex,
      alignItems.center,

      div(
        marginRight := "5px",
        canGoLeft.map {
          case true => VDomModifier.empty
          case false => color := "gray"
        },
        freeSolid.faCaretLeft,
        onClickDefault.collect { case _ if canGoLeft.now => currentStepIndex.now - 1 } --> currentStepIndex
      ),

      div(
        currentStep.map(_.name),
        " (Step ", currentStepIndex.map(_ + 1), " of ", stepLength, ")",
      ),

      div(
        marginLeft := "5px",
        canGoRight.map {
          case true => VDomModifier.empty
          case false => color := "gray"
        },
        freeSolid.faCaretRight,
        onClickDefault.collect { case _ if canGoRight.now => currentStepIndex.now + 1 } --> currentStepIndex
      )
    )

    div(
      header,
      div(
        padding := "10px",

        currentStep.map(_.content)
      ),

      button(
        cls := "ui primary button",
        disabled <-- Rx { !currentStep().finished() },
        canGoRight.map {
          case true => VDomModifier(
            "Next",
            onClick.stopPropagation.useLazy(currentStepIndex.now + 1) --> currentStepIndex
          )
          case false => VDomModifier(
            "Finish"
          )
        }

      )
    )
  }
}
