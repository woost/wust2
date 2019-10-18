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

object PaymentView {
  sealed trait Error
  object Error {
    case class UserActionNeeded(str: String) extends Error
    case class Fatal(str: String) extends Error
    case object AlreadySubscribed extends Error
  }

  //TODO get current payment plan of user to visualize what the user currently uses.
  val render = {
    val stripe: Task[Either[Error, StripeInstance]] =
      Task.fromFuture(Client.api.getStripePublicKey)
        .map(_.toRight(Error.Fatal("No public key available")).flatMap(p => Stripe.get(p.publicKey).toRight(Error.Fatal("Stripe.js is not available"))))
        .memoizeOnSuccess

    def session(paymentPlan: PaymentPlan): Task[Either[Error, StripeSessionId]] =
      Task.fromFuture(Client.api.createStripeCheckoutSession(paymentPlan)).map {
        case StripeCheckoutResponse.NewSession(sessionId) => Right(sessionId)
        case StripeCheckoutResponse.AlreadySubscribed => Left(Error.AlreadySubscribed)
        case StripeCheckoutResponse.Forbidden => Left(Error.UserActionNeeded("Please login into your account and verify your email address"))
        case StripeCheckoutResponse.Error => Left(Error.Fatal("Got Error Response from Backend"))
      }

    def redirect(stripe: StripeInstance, stripeSessionId: StripeSessionId): Task[Either[Error, Unit]] = {
      //TOOD: why does new CheckoutOptions create a filed bitmapInit?!
      val opts = js.Dynamic.literal(sessionId = stripeSessionId.sessionId).asInstanceOf[CheckoutOptions]
      Task.fromFuture(stripe.redirectToCheckout(opts).toFuture).map { result =>
        result.error.map(error => Error.Fatal(s"Stripe failed to redirect, because: ${error.message}")).toLeft(())
      }
    }

    def checkoutTask(paymentPlan: PaymentPlan): EitherT[Task, Error, Unit] = for {
      stripe <- EitherT(stripe)
      sessionId <- EitherT(session(paymentPlan))
      result <- EitherT(redirect(stripe, sessionId))
    } yield result

    def sendFatalPaymentWarning(): Unit = {
      UI.toast("We are sorry, but the payment provider is currently unavailable. Please try again later.", "Payment is unavailable", level = UI.ToastLevel.Warning)
    }

    def sendToStripe(paymentPlan: PaymentPlan): Unit = checkoutTask(paymentPlan).value.runAsync {
      case Right(Right(())) => ()
      case Right(Left(Error.UserActionNeeded(error))) =>
        UI.toast(error, level = UI.ToastLevel.Warning)
      case Right(Left(Error.AlreadySubscribed)) =>
        UI.toast("You are already subcribed to this Plan", level = UI.ToastLevel.Success)
      case Right(Left(Error.Fatal(error))) =>
        scribe.error(s"Failed to initiate checkout session with stripe: $error")
        sendFatalPaymentWarning()
      case Left(ex) =>
        scribe.error(s"Unexpected error while initiating checkout session with stripe", ex)
        sendFatalPaymentWarning()
    }

    def paymentBlock(icon: VDomModifier, name: String, description: String, items: List[String], paymentIntent: Option[PaymentPlanIntent]) = div(
      margin := "5px",
      padding := "10px",
      height := "400px",
      minWidth := "250px",

      boxShadow := "0px 10px 18px -6px rgba(0,0,0,0.4)",

      Styles.flex,
      flexDirection.column,
      justifyContent.spaceBetween,

      div(
        Styles.flex,
        flexDirection.column,
        alignItems.center,

        h3(
          Styles.flex,
          alignItems.center,

          icon,
          span(name, marginLeft := "5px"),
        ),

        div(
          marginTop := "10px",
          description
        ),

        div(
          marginTop := "20px",

          items.map { item =>
            div(
              margin := "5px",
              Styles.flex,
              alignItems.center,

              freeSolid.faCheck,
              span(marginLeft := "5px", item)
            )
          }
        ),
      ),

      div(
        Styles.flex,
        flexDirection.column,
        alignItems.center,

        paymentIntent match {
          case Some(intent) => VDomModifier(
            h4(
              marginTop := "20px",

              intent.price, " €"// TODO: locale price...
            ),

            button(
              cls := "ui primary button",

              "Buy",

              onClickDefault.foreach(sendToStripe(intent.plan))
            )
          )
          case None => VDomModifier(
            button(
              cls := "ui primary button",

              "Use for Free",

              onClickDefault.foreach(GlobalState.urlConfig.update(_.focus(View.Welcome)))
            )
          )
        }
      )
    )

    div(
      padding := "20px",
      Styles.growFull,
      Styles.flex,
      justifyContent.center,

      div(

        h3("Plans"),

        div(
          marginTop := "20px",
          Styles.flex,
          flexWrap.wrap,
          alignItems.flexStart,

          paymentBlock(
            freeSolid.faUser,
            "Free",
            "For Personal Usage",
            "1 GB Storage" ::
            "5 Clients" ::
            "5 Automations" ::
            Nil,
            None
          ),
          paymentBlock(
            freeSolid.faBuilding,
            "Business",
            "For Business Usage",
            "100 GB Storage" ::
            "25 Clients" ::
            "Unlimited Automations" ::
            "Reminders" ::
            "High-Priority Support" ::
            Nil,
            Some(PaymentPlanIntent(PaymentPlan.Business, price = 40))
          ),
        )
      )
    )
  }

  val focusButton = {
    button(
      margin := "0 5px",

      cls := "ui mini compact button basic",

      "Pricing",

      onClickDefault.foreach(GlobalState.urlConfig.update(_.focus(View.Payment)))
    )
  }

  case class PaymentPlanIntent(plan: PaymentPlan, price: Int)
}
