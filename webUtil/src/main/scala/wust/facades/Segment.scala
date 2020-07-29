package wust.facades.segment

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// Segment JavaScript API:
// https://segment.com/docs/spec/

object Segment {
  // default API
  def identify(userId:String) = {
    scribe.debug(s"Segment: identify($userId)")
    ifLoaded(_.identify(userId))
  }
  def trackEvent(event:String) = {
    scribe.debug(s"Segment: track($event)")
    ifLoaded(_.track(event))
  }
  def trackEvent(event:String, properties: js.Object) = {
    scribe.debug(s"Segment: track($event, ${js.JSON.stringify(properties)})")
    ifLoaded(_.track(event, properties))
  }
  def page(name:String) = {
    scribe.debug(s"Segment: page($name)")
    ifLoaded(_.page(name))

    // Since the Segment -> FullStory integration does not send page-events:
    trackEvent("Viewed", js.Dynamic.literal(page = name))
  }

  // B2B SaaS
  // https://segment.com/docs/spec/b2b-saas
  def trackSignedUp() = trackEvent("Signed Up") // This event should be sent when a user signs up for your service.
  def trackSignedUp(tpe:String) = trackEvent("Signed Up", js.Dynamic.literal(`type` = tpe)) // This event should be sent when a user signs up for your service.
  def trackSignedIn() = trackEvent("Signed In") // This event should be sent when a user signs in to your service.
  def trackSignedOut() = {
    // This event should be sent when a user signs out for your service.
    trackEvent("Signed Out")
    scribe.debug("Segment: reset()")
    ifLoaded{ analytics =>
      analytics.reset()
    }
  }
  def trackInviteSent() = trackEvent("Invite Sent") // This event should be sent when a user invites another user.

  // custom
  def trackError(event:String, errorMessage: String) = trackEvent("Error", js.Dynamic.literal(error = event, details = errorMessage))

  @inline def ifLoaded(f: Analytics => Unit): Unit = {
    if(js.typeOf(js.Dynamic.global.analytics) != "undefined") {
      SegmentGlobalScope.analytics.foreach(f)
    }
  }
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
