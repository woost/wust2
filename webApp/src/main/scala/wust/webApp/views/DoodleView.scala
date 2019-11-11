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

  def render(implicit ctx: Ctx.Owner) = {

    div(

      h3("Doodle"),

      StepMenu.render(Array(
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
      )),
    )
  }
}
