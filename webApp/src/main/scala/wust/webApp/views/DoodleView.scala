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

object DoodleView extends AppDefinition {


  def landing(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    div(
      Styles.growFull,
      padding := "20px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,

      button(
        cls := "ui basic button",

        "Create Doodle",

        onClick.use(AppDefinition.State.App) --> state
      )
    )
  }

  def app(state: SinkObserver[AppDefinition.State])(implicit ctx: Ctx.Owner): VDomModifier = {
    UI.segment("Create a Tootle", doodleForm)
  }

  def doodleForm(implicit ctx: Ctx.Owner) = {

    val title = Var[String]("")

    val view = Var[Option[View.Visible]](None)

    val menu = StepMenu.render(Array(
      StepMenu.Step(
        "What's the occasion?",
        div(
          cls := "ui mini form",
          label("Title"),
          input(
            tpe := "text",
            value <-- title,
            onInput.value --> title
          )
        ),
        title.map(_.nonEmpty)
      ),
      StepMenu.Step(
        "What do you want to work on?",
        div(
          2
        ),
        view.map(_.isDefined)
      ),
      StepMenu.Step(
        "Collaboration Settings",
        div(
          3
        ),
      ),
      StepMenu.Step(
        "Tell your participants who you are",
        div(
          4
        )
      )
    )).foreach {
      println("Done")
    }

    div(
      width := "600px",
      height := "500px",

      menu
    )
  }
}
