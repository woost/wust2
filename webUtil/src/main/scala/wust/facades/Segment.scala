package wust.facades.segment

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// Segment JavaScript API:
// https://segment.com/docs/spec/

object Segment {
  // default API
  def identify(userId:String) = ifLoaded(_.identify(userId))
  def trackEvent(event:String) = ifLoaded(_.track(event))
  def trackEvent(event:String, properties: js.Object) = ifLoaded(_.track(event, properties))
  def page(name:String) = ifLoaded(_.page(name))

  // B2B SaaS
  // https://segment.com/docs/spec/b2b-saas
  def trackSignedUp(tpe:String) = ifLoaded(_.track("Signed Up", js.Dynamic.literal(`type` = tpe))) // This event should be sent when a user signs up for your service.
  def trackSignedIn() = ifLoaded(_.track("Signed In")) // This event should be sent when a user signs in to your service.
  def trackSignedOut() = {
    // This event should be sent when a user signs out for your service.
    ifLoaded{ analytics =>
      analytics.track("Signed Out")
      analytics.reset()
    }
  }
  def trackInviteSent() = ifLoaded(_.track("Invite Sent")) // This event should be sent when a user invites another user.

  // custom
  def trackError(event:String, errorMessage: String) = ifLoaded(_.track(event, js.Dynamic.literal(error = errorMessage)))

  @inline def ifLoaded(f: Analytics => Unit): Unit = SegmentGlobalScope.analytics.foreach(f)
}

@js.native
@JSGlobalScope
object SegmentGlobalScope extends js.Object {
  def analytics: js.UndefOr[Analytics] = js.native
}

@js.native
trait Analytics extends js.Object {
  def identify(userId: String): Unit = js.native
  def track(event: String): Unit = js.native
  def track(event: String, properties: js.Object): Unit = js.native
  def page(name: String): Unit = js.native
  def page(name: String, properties: js.Object): Unit = js.native
  def reset(): Unit = js.native
}
