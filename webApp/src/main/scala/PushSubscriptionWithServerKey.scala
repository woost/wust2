package wust.webApp

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.experimental.push._
import org.scalajs.dom.experimental.serviceworkers.ServiceWorkerRegistration
import org.scalajs.dom
import scala.scalajs.js.typedarray.Uint8Array

//TODO: pr scala-js-dom: https://github.com/scala-js/scala-js-dom/pull/324
@js.native
trait PushSubscriptionOptionsWithServerKey extends PushSubscriptionOptions {
  def applicationServerKey: js.UndefOr[Uint8Array] = js.native
}

object PushSubscriptionOptionsWithServerKey {
  def apply(userVisibleOnly: Boolean, applicationServerKey: Uint8Array): PushSubscriptionOptionsWithServerKey = {
    js.Dynamic
      .literal(userVisibleOnly = userVisibleOnly, applicationServerKey = applicationServerKey)
      .asInstanceOf[PushSubscriptionOptionsWithServerKey]
  }
}
