package wust.facades.fullstory

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobalScope

// FullStory JavaScript API:
// https://help.fullstory.com/hc/en-us/sections/360003732794-JavaScript-API

object FS {
  def identify(uid: String): Unit = ifLoaded { _.identify(uid) }
  def identify(state: Boolean): Unit = ifLoaded { _.identify(state) }
  def event(eventName: String): Unit = ifLoaded { _.event(eventName) }
  def event(eventName: String, eventProperties: js.Object): Unit = ifLoaded { _.event(eventName, eventProperties) }

  @inline private def ifLoaded(f: FS => Unit): Unit = FullStory.FS.foreach(f)
}

@js.native
@JSGlobalScope
object FullStory extends js.Object {
  def FS: js.UndefOr[FS] = js.native
}

@js.native
trait FS extends js.Object {
  def identify(uid: String): Unit = js.native
  def identify(state: Boolean): Unit = js.native
  def event(eventName: String): Unit = js.native
  def event(eventName: String, eventProperties: js.Object): Unit = js.native
}
