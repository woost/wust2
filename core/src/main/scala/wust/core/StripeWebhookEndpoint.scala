package wust.core

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import wust.api.Authentication
import wust.ids._
import wust.core.auth.{JWT, OAuthClientServiceLookup}
import wust.core.config.StripeConfig
import wust.core.pushnotifications.PushClients
import wust.db.{Data, Db}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.stripe.model._
import com.stripe.model.checkout._
import com.stripe.net._
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.google.gson.JsonSyntaxException
import akka.http.scaladsl.server.Directives._

//TODO: track per analytics or email if there are problems when processing events from stripe!
class StripeWebhookEndpoint(db: Db, stripeApi: StripeApi) {
  import stripeApi._

  case class PaymentInfo(customerId: String, paymentPlan: PaymentPlan)

  def receive(payload: String, signature: String)(implicit ec: ExecutionContext): Route = {
    scribe.info("Receiving Payment Request from Stripe")

    val result = for {
      event <- deserializeEvent(payload, signature)
      _ = scribe.info(s"Got Event from Stripe: ${event.getType}")
      stripeObject <- deserializeStripeObject(event.getDataObjectDeserializer)
      result <- (stripeObject, event.getType) match {
        case (session: Session, "checkout.session.completed" | "invoice.payment_succeeded") =>
          val purchasedItems = session.getDisplayItems()
          if (purchasedItems.isEmpty) {
            Left(ServerError("No Items were purchased in this order"))
          } else {
            val planId = Option(purchasedItems.get(0).getPlan).map(_.getId) //TODO: what about multiple items?
            planId.flatMap(stripeApi.idToPaymentPlanKind.lift) match {
              case Some(paymentPlan) => Right(Some(PaymentInfo(session.getCustomer, paymentPlan)))
              case None => Left(ServerError(s"Unknown payment plan: $planId"))
            }
          }
        case (session: Session, "invoice.payment_failed") =>
          // switch back to free on failed payment
          Right(Some(PaymentInfo(session.getCustomer, PaymentPlan.Free)))
        case (_, event) =>
          scribe.info(s"Got non-actionable event from stripe: $event")
          Right(None)
      }
    } yield result

    result match {
      case Right(Some(paymentInfo)) =>
        scribe.info(s"Got payment info: $paymentInfo")

        val setPayment = db.stripeCustomer.getByCustomerId(StripeCustomerId(paymentInfo.customerId)).flatMap {
          case Some(stripeCustomer) => db.user.setPaymentPlan(stripeCustomer.userId, paymentInfo.paymentPlan)
          case None => Future.successful(false)
        }

        onComplete(setPayment) {
          case Success(true) =>
            scribe.info(s"Successfuly upgraded payment plan: $paymentInfo")
            complete(StatusCodes.OK)
          case Success(false) =>
            scribe.error(s"Failed to upgraded payment plan: $paymentInfo")
            complete(StatusCodes.InternalServerError)
          case Failure(error) =>
            scribe.error(s"Failed to upgraded payment plan: $paymentInfo", error)
            complete(StatusCodes.InternalServerError)
        }
      case Right(None) =>
        scribe.info("Nothing to do for this stripe event")
        complete(StatusCodes.OK)
      case Left(RequestError(error)) =>
        scribe.warn(s"Request Error while handling stripe event: $error")
        complete(StatusCodes.BadRequest)
      case Left(ServerError(error)) =>
        scribe.warn(s"Server Error while handling stripe event: $error")
        complete(StatusCodes.InternalServerError)
    }
  }
}
