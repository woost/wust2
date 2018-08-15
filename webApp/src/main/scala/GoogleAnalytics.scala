package googleAnalytics

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope
import org.scalajs.dom.console

// Google Analytics Event Tracking documentation:
// https://developers.google.com/analytics/devguides/collection/analyticsjs/field-reference

object Analytics {
  // https://stackoverflow.com/questions/15744042/events-not-being-tracked-in-new-google-analytics-analytics-js-setup/40761709#40761709
  lazy val tracker = GoogleAnalytics.ga.map(_.getAll().apply(0))

  def sendEvent(category: String, action: String, label: js.UndefOr[String] = js.undefined, value: js.UndefOr[Int] = js.undefined): Unit = {
//    console.log(s"trying to send event: $category $action $label $value, tracker:", tracker)
    tracker.foreach { tracker =>
      tracker.send("event", new EventOptions {
        var eventCategory = category
        var eventAction = action
        eventLabel = label
        eventValue = value
      })
    }
  }
}

@js.native
@JSGlobalScope
object GoogleAnalytics extends js.Object {
  def ga: js.UndefOr[GA] = js.native
}

trait EventOptions extends js.Object {
  var eventCategory: String
  var eventAction: String
  var eventLabel: js.UndefOr[String] = js.undefined
  var eventValue: js.UndefOr[Int] = js.undefined
}


trait GA extends js.Object {
  def getAll(): js.Array[Tracker]
}

//https://developers.google.com/analytics/devguides/collection/analyticsjs/tracker-object-reference
trait Tracker extends js.Object {
  def send(hitType: String, event: EventOptions): Unit
}