package wust.webApp

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// Google Analytics Event Tracking documentation:
// https://developers.google.com/analytics/devguides/collection/analyticsjs/events

// from https://gist.github.com/spaced/34544395414404b8036a
object Analytics {
  private def isScriptLoaded = js.Dynamic.global.ga.asInstanceOf[js.UndefOr[js.Function]].isDefined
  def sendEvent(category: String, action: String): Unit = {

    if(isScriptLoaded) GoogleAnalytics.ga("send", "event", new EventOptions {
      var eventCategory = category
      var eventAction = action
    })
  }
  def sendEvent(category: String, action: String, label: String): Unit = {
    if(isScriptLoaded) GoogleAnalytics.ga("send", "event", new EventOptions {
      var eventCategory = category
      var eventAction = action
      eventLabel = label
    })
  }
  def sendEvent(category: String, action: String, label: String, value: Int): Unit = {
    if(isScriptLoaded) GoogleAnalytics.ga("send", "event", new EventOptions {
      var eventCategory = category
      var eventAction = action
      eventLabel = label
      eventValue = value
    })
  }
}

@js.native
@JSGlobalScope
object GoogleAnalytics extends js.Object {
  def ga(send: String, event: String, eventOptions:EventOptions): Unit = js.native
}

trait EventOptions extends js.Object {
  var eventCategory: String
  var eventAction: String
  var eventLabel: js.UndefOr[String] = js.undefined
  var eventValue: js.UndefOr[Int] = js.undefined
}
