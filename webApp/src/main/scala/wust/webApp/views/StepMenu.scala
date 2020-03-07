package wust.webApp.views

import cats.data.EitherT
import outwatch._
import outwatch.dsl._
import colibri.ext.rx._
import colibri._
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
  )(implicit ctx: Ctx.Owner) = EmitterBuilder.ofModifier[Unit] { sink =>
    require(steps.nonEmpty, "Must have non-empty steps in step menu")

    val stepLength = steps.length

    val currentStepIndex = Var(0)
    val currentStep = currentStepIndex.map(steps(_))
    val isLastStep = currentStepIndex.map(_ == stepLength - 1)
    val canGoLeft = currentStepIndex.map(_ > 0)
    val canGoRight = Rx {
      currentStep().finished() && !isLastStep()
    }

    val header = div(
      Styles.flex,
      flexDirection.column,
      alignItems.center,

      div(
        fontSize.small,
        color.gray,
        "Step ", currentStepIndex.map(_ + 1), " of ", stepLength,
      ),
      b(
        currentStep.map(_.name),
      )
    )

    div(
      width := "100%",
      header,
      div(
        padding := "10px",

        currentStep.map(_.content)
      ),

      div(
        marginTop := "10px",
        float.right,

        button(
          cls := "ui basic mini button",
          disabled <-- canGoLeft.map(!_),
          freeSolid.faCaretLeft,
          onClickDefault.collect { case _ if canGoLeft.now => currentStepIndex.now - 1 } --> currentStepIndex
        ),
        button(
          cls := "ui primary button",
          disabled <-- Rx { !currentStep().finished() },
          isLastStep.map {
            case false => VDomModifier(
              "Continue",
              onClick.stopPropagation.useLazy(currentStepIndex.now + 1) --> currentStepIndex
            )
            case true => VDomModifier(
              "Finish",
              onClick.stopPropagation.useLazy(()) --> sink
            )
          }

        )
      )
    )
  }
}
