package wust.core

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes, Uri}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import wust.api.{AuthUser, Authentication, UserDetail}
import wust.ids._
import wust.core.auth.{JWT, OAuthClientServiceLookup}
import wust.core.config.{ServerConfig, StripeConfig}
import wust.core.pushnotifications.PushClients
import wust.db.{Data, Db}
import monix.eval.Task

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

import com.stripe.model._
import com.stripe.net._
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.checkout._
import com.google.gson.JsonSyntaxException
import akka.http.scaladsl.server.Directives._
import java.util.{ArrayList, HashMap}

class StripeApi(config: StripeConfig, serverConfig: ServerConfig) {

  sealed trait Error
  case class RequestError(str: String) extends Error
  case class ServerError(str: String) extends Error

  type Result[T] = Either[Error, T]

  //ugly side effect, set api key.
  Stripe.apiKey = config.apiKey

  private val endpointSecret = config.endpointSecret // endpointSecret per webhook

  def publicKey = config.publicKey // public api key

  def createCustomer(customerEmail: String): Task[Customer] = {

    scribe.info(s"Creating Customer for User: $customerEmail")

    val params = new HashMap[String, AnyRef]
    params.put("email", customerEmail)

    Task(Customer.create(params))
  }

  def createSubscription(customerId: String, paymentPlan: PaymentPlan): Task[Subscription] = {

    scribe.info(s"Creating customer subscription for plan: $paymentPlan (customer: $customerId)")

    val paymentPlanId = paymentPlanToId(paymentPlan)

    val item = new HashMap[String, Object]
    item.put("plan", paymentPlanId)

    val items = new HashMap[String, Object]
    items.put("0", item)

    val expandList = new ArrayList[String]
    expandList.add("latest_invoice.payment_intent")

    val params = new HashMap[String, Object]
    params.put("customer", customerId)
    params.put("items", items)
    params.put("expand", expandList)

    Task(Subscription.create(params))
  }

  def createCheckoutSession(paymentPlan: PaymentPlan, customerId: String): Task[Session] = {

    scribe.info(s"Creating checkout session for plan: $paymentPlan")

    val paymentPlanId = paymentPlanToId(paymentPlan)

    val params = new HashMap[String,AnyRef]

    val paymentMethodTypes = new ArrayList[String]
    paymentMethodTypes.add("card")
    params.put("payment_method_types", paymentMethodTypes)

    params.put("customer", customerId)

    val subscriptionData = new HashMap[String,AnyRef]
    val items = new HashMap[String,AnyRef]
    val item = new HashMap[String,AnyRef]
    item.put("plan", paymentPlanId)
    items.put("0", item)
    subscriptionData.put("items", items)
    params.put("subscription_data", subscriptionData)

    params.put("success_url", s"https://${serverConfig.host}/#info=plan-success")
    params.put("cancel_url", s"https://${serverConfig.host}/cancel")

    Task(Session.create(params))
  }

  def deserializeEvent(payload: String, signature: String): Result[Event] = {
    try {
      Right(Webhook.constructEvent(payload, signature, endpointSecret))
    } catch {
      case e:JsonSyntaxException => // Invalid payload
        Left(RequestError(s"Error while constructing stripe event: ${e.getMessage}"))
      case e: SignatureVerificationException => // Invalid signature
        Left(RequestError(s"Invalid signature in stripe event: ${e.getMessage}"))
    }
  }

  def deserializeStripeObject(dataObjectDeserializer: EventDataObjectDeserializer): Result[StripeObject] = {
    if (dataObjectDeserializer.getObject().isPresent()) {
      Right(dataObjectDeserializer.getObject().get())
    } else {
      // Deserialization failed, probably due to an API version mismatch.
      // Refer to the Javadoc documentation on `EventDataObjectDeserializer` for
      // instructions on how to handle this case, or return an error here.
      Left(RequestError("Cannot deserialize StripeObject in stripe event, probably due to an API version mismatch."))
    }
  }

  val paymentPlanToId: PaymentPlan => String = {
    case PaymentPlan.Business => "Business-1"
  }
  val idToPaymentPlanKind: PartialFunction[String, PaymentPlan] = {
    case "Business-1" => PaymentPlan.Business
  }
}
