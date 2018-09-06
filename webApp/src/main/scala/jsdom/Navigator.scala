package wust.webApp.jsdom

import org.scalajs.dom
import org.scalajs.dom.window

import scala.scalajs.js

object Navigator {
  import org.scalajs.dom.experimental.permissions._
  import org.scalajs.dom.experimental.serviceworkers._

  def permissions: Option[Permissions] = window.navigator.permissions.asInstanceOf[js.UndefOr[Permissions]].toOption
  def serviceWorker: Option[ServiceWorkerContainer] = window.navigator.serviceWorker.asInstanceOf[js.UndefOr[ServiceWorkerContainer]].toOption

  //TODO make it work with facade?
  // def share: Option[ShareFunction] = window.navigator.asInstanceOf[NavigatorWithShare].share.toOption
  object share {
    private val n = dom.window.navigator.asInstanceOf[js.Dynamic]
    def isDefined = n.share.asInstanceOf[js.UndefOr[js.Dynamic]].isDefined
    // It is important to call n.share on the n-object. so that the share
    // function in js has its this pointer correctly set to Navigator.
    def apply(data: ShareData): js.Promise[Unit] = n.share(data).asInstanceOf[js.Promise[Unit]]
  }

  // test share button: as navigator share is not defined on desktop browsers
  // DevOnly {
    // dom.window.navigator.asInstanceOf[js.Dynamic].share = { (thisObj: js.Any, data: ShareData) =>
    //   org.scalajs.dom.console.log("Share function called", data, thisObj)
    //   js.Promise.resolve[Unit](())
    // }: js.ThisFunction1[js.Any, ShareData, js.Promise[Unit]]
  // }
}

//TODO contribute to scala-js
//@js.native
//trait NavigatorWithShare extends js.Any {
//  val share: js.UndefOr[ShareFunction] = js.native
//}
//@js.native
//trait ShareFunction extends js.Any {
//  def apply(data: ShareData): js.Promise[Unit] = js.native
//}
trait ShareData extends js.Object {
  var url: js.UndefOr[String] = js.undefined
  var text: js.UndefOr[String] = js.undefined
  var title: js.UndefOr[String] = js.undefined
}
