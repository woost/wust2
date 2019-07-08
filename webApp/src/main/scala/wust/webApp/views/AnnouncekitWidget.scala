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

  def widget(implicit ctx: Ctx.Owner) = {
    val unreadCount = Var(0)
    val announcekitLoaded = Var(false)

    ProductionOnly {
      Try {
        announcekit.push(new AnnouncekitOptions {
          widget = s"https://announcekit.app/widget/$widgetId"
          name = widgetId
          version = 2
          data = new AnnouncekitDataOptions {
            user_id = GlobalState.userId.now.toUuid.toString
            user_name = GlobalState.user.now.name
          }
        })

        announcekit.on("widget-unread", { e =>
          // Called when unread post count of specified widget has been updated
          unreadCount() = e.unread.asInstanceOf[Int]
        })

        announcekitLoaded() = true
      }
    }

    a(
      "What's new? ",
      href := "https://announcekit.app/woost/announcements",
      safeTargetBlank,
      ProductionOnly {
        Rx {
          VDomModifier.ifTrue(announcekitLoaded())(
            onClick.preventDefault.foreach {
              Try {
                announcekit.asInstanceOf[js.Dynamic].selectDynamic(s"widget$$$widgetId").open()
              }
              ()
            }
          )
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
