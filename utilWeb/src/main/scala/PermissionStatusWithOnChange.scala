package wust.utilWeb

import org.scalajs.dom

import scala.scalajs.js
import org.scalajs.dom.experimental.permissions.{PermissionDescriptor, PermissionName, PermissionState, PermissionStatus}

//TODO: pr scala-js-dom: https://github.com/scala-js/scala-js-dom/pull/321
@js.native
trait PermissionStatusWithOnChange extends dom.raw.EventTarget {
  val state: PermissionState
  var onchange: js.Function1[PermissionState, _]
}

//TODO: pr scala-js-dom
@js.native
trait PushPermissionDescriptor extends PermissionDescriptor {
  val userVisibleOnly: Boolean
}

object PushPermissionDescriptor {
  @inline
  def apply(userVisibleOnly: Boolean): PushPermissionDescriptor = {
    js.Dynamic
      .literal("name" -> PermissionName.push, "userVisibleOnly" -> userVisibleOnly)
      .asInstanceOf[PushPermissionDescriptor]
  }
}

