package wust.utilWeb

import scala.scalajs.js
import scala.scalajs.js.annotation._
import org.scalajs.dom.experimental.push._
import org.scalajs.dom.experimental.serviceworkers.ServiceWorkerRegistration
import org.scalajs.dom
import scala.scalajs.js.typedarray.Uint8Array

//TODO: pr scala-js-dom
trait PushSubscriptionOptionsWithServerKey extends js.Object with PushSubscriptionOptions {
  def applicationServerKey: Uint8Array
}

object PushSubscriptionOptionsWithServerKey {
  def apply(userVisibleOnly: Boolean, applicationServerKey: Uint8Array): PushSubscriptionOptionsWithServerKey = {
    js.Dynamic
      .literal(userVisibleOnly = userVisibleOnly, applicationServerKey = applicationServerKey)
      .asInstanceOf[PushSubscriptionOptionsWithServerKey]
  }
}
