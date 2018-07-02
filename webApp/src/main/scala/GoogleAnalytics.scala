package wust.webApp

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// Google Analytics Event Tracking documentation:
// https://developers.google.com/analytics/devguides/collection/analyticsjs/events

// from https://gist.github.com/spaced/34544395414404b8036a
object Analytics {
  private def isScriptLoaded = js.Dynamic.global.ga.isInstanceOf[js.Function]
  def sendEvent(category: String, action: String, label: String): Unit = {
    if (isScriptLoaded) GoogleAnalytics.ga("send", "event", category, action, label)
  }
  def sendEvent(category: String, action: String, label: String, value: Int): Unit = {
    if (isScriptLoaded) GoogleAnalytics.ga("send", "event", category, action, label, value)
  }
}

@js.native
@JSGlobalScope
object GoogleAnalytics extends js.Object {
  def ga(send: String, event: String, category: String, action: String, label: String): Unit =
    js.native
  def ga(
      send: String,
      event: String,
      category: String,
      action: String,
      label: String,
      value: js.Any
  ): Unit = js.native
}
