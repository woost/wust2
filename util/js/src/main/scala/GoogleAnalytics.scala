package wust.util

import scala.scalajs.js

// Google Analytics Event Tracking documentation:
// https://developers.google.com/analytics/devguides/collection/analyticsjs/events

// from https://gist.github.com/spaced/34544395414404b8036a
object EventTracker {
  def isScriptLoaded=js.Dynamic.global.ga.isInstanceOf[js.Function]
  def sendEvent(category:String,action:String,label:String):Unit={
    if (isScriptLoaded) GoogleAnalytics.ga("send","event",category,action,label)
  }
  def sendEvent(category:String,action:String,label:String,value:String):Unit={
    if (isScriptLoaded) GoogleAnalytics.ga("send","event",category,action,label,value)
  }
}

@js.native
object GoogleAnalytics extends js.GlobalScope {
  def ga(send:String,event:String,category:String,action:String,label:String ): Unit = js.native
  def ga(send:String,event:String,category:String,action:String,label:String,value:js.Any ): Unit = js.native
}
