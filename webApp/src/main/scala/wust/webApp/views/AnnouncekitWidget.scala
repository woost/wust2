package wust.webApp.views

import wust.webUtil.BrowserDetect
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Ownable, UI }
import wust.css.Styles
import wust.ids._
import wust.sdk.Colors
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{ drag, registerDragContainer }

import scala.collection.breakOut
import scala.scalajs.js
import monix.reactive.Observer
import snabbdom.VNodeProxy

object AnnouncekitWidget {
  val widget = {
    import wust.facades.announcekit._

    def setup(): Unit = {
      announcekit.push(new AnnouncekitOptions {
        widget = "https://announcekit.app/widget/4hH5Qs"
        selector = ".announcekit-widget"
        name = "4hH5Qs"
        version = 2
        badge = new AnnouncekitBadgeOptions {
          style = js.Dynamic.literal(
            padding = "3px" // vertically center circle
          )
        }
      })
    }

    a(
      "What's new? ",
      href := "https://announcekit.app/woost/announcements",
      onClick.preventDefault.foreach {
        announcekit.asInstanceOf[js.Dynamic].widget$4hH5Qs.open()
        ()
      },
      color.white,
      fontWeight.bold,
      cls := "announcekit-widget",
      DomMountHook { proxy =>
        VNodeProxy.repairDom(proxy)
        setup()
      },
      snabbdom.VNodeProxy.repairDomBeforePatch,
    )
  }
}
