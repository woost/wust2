package wust.webApp.views

import scala.util.Try
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
import wust.facades.announcekit._
import org.scalajs.dom.console
import fontAwesome.freeSolid
import wust.webUtil.Elements.safeTargetBlank

// https://announcekit.app/docs

object AnnouncekitWidget {
  val widgetId = "4hH5Qs"

  val unreadCount = Var(0)

  ProductionOnly {
    announcekit.push(new AnnouncekitOptions {
      widget = s"https://announcekit.app/widget/$widgetId"
      name = widgetId
      version = 2
    })

    announcekit.on("widget-unread", { e =>
      // Called when unread post count of specified widget has been updated
      unreadCount() = e.unread.asInstanceOf[Int]
    })
  }

  val widget = {
    a(
      "What's new? ",
      href := "https://announcekit.app/woost/announcements",
      safeTargetBlank,
      ProductionOnly {
        onClick.preventDefault.foreach {
          announcekit.asInstanceOf[js.Dynamic].widget$4hH5Qs.open()
          ()
        }
      },
      color.white,
      // like semantic-ui tiny button
      fontSize := "0.85714286rem",
      fontWeight := 700,
      padding := ".58928571em 1.125em .58928571em",
      cursor.pointer,
      Rx {
        VDomModifier.ifTrue(unreadCount() > 0)(
          freeSolid.faGift
        )
      }
    )
  }
}
