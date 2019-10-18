package wust.facades.stripe

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

@js.native
@JSGlobalScope
object StripeGlobalScope extends js.Object {
  def Stripe: js.UndefOr[StripeConstructor] = js.native
}

@js.native
trait StripeConstructor extends js.Object {
  def apply(publicKey: String): StripeInstance = js.native
}

@js.native
trait StripeInstance extends js.Object {
  def redirectToCheckout(options: CheckoutOptions): js.Promise[RedirectResult] = js.native
}

trait CheckoutOptions extends js.Object {
  var items: js.UndefOr[js.Array[PurchaseItem]] = js.undefined
  var successUrl: js.UndefOr[String] = js.undefined
  var cancelUrl: js.UndefOr[String] = js.undefined

  var clientReferenceId: js.UndefOr[String] = js.undefined
  var customerEmail: js.UndefOr[String] = js.undefined
  var billingAddressCollection: js.UndefOr[String] = js.undefined
  var sessionId: js.UndefOr[String] = js.undefined
  var locale: js.UndefOr[String] = js.undefined
  var submitType: js.UndefOr[String] = js.undefined
}

trait PurchaseItem extends js.Object {
}

@js.native
trait RedirectResult extends js.Object {
  def error: js.UndefOr[ErrorObject] = js.native
}
@js.native
trait ErrorObject extends js.Object {
  def message: String = js.native
}

object Stripe {
  def get(publicKey: String) = StripeGlobalScope.Stripe.map(_(publicKey)).toOption
}
