package wust.facades.amplitude

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// Amplitude SDK:
// https://developers.amplitude.com/?javascript#tracking-events

object Amplitude {
  def logEvent(eventName: String): Unit = ifLoaded { _.logEvent(eventName) }
  def logEvent(eventName: String, eventProperties: js.Object): Unit = ifLoaded { _.logEvent(eventName, eventProperties) }
  def setUserId(id: String): Unit = ifLoaded { _.setUserId(id) }

  @inline private def ifLoaded(f: AmplitudeInstance => Unit): Unit = {
    AmplitudeGlobal.amplitude.foreach{ _.getInstance().foreach{ f } }
  }
}

@js.native
@JSGlobalScope
object AmplitudeGlobal extends js.Object {
  def amplitude: js.UndefOr[AmplitudeGlobal] = js.native
}

@js.native
trait AmplitudeGlobal extends js.Object {
  def getInstance(): js.UndefOr[AmplitudeInstance] = js.native
}

@js.native
trait AmplitudeInstance extends js.Object {
  def setUserId(id: String): Unit = js.native
  def logEvent(eventName: String): Unit = js.native
  def logEvent(eventName: String, eventProperties: js.Object): Unit = js.native
}
