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

object WoostAppView {

  def render(woostApp: AppDefinition)(implicit ctx: Ctx.Owner) = {

    val state = SinkSourceHandler[AppDefinition.State](AppDefinition.State.Landing)

    div(
      Styles.growFull,
      padding := "20px",

      state.map {
        case AppDefinition.State.Landing => woostApp.landing(state)(ctx)
        case AppDefinition.State.App => woostApp.app(state)(ctx)
      }
    )
  }
}