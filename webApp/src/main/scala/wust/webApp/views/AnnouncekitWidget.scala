package wust.webApp.views

import fontAwesome.freeSolid
import outwatch._
import outwatch.dsl._
import rx._
import wust.facades.announcekit._
import wust.webApp._
import wust.webApp.state._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._

import scala.scalajs.js
import scala.util.Try
import wust.facades.segment.Segment

// https://announcekit.app/docs

object AnnouncekitWidget {
  val widgetId = "4hH5Qs"

  def widget(implicit ctx: Ctx.Owner) = {
    val unreadCount = Var(0)

    a(
      "What's new? ",
      href := "https://announcekit.app/woost/announcements",
      safeTargetBlank,
      initAnounceKit(unreadCount) map { initF =>
        onClick.preventDefault.foreach{
          initF()
        },
      },
      onClickDefault.foreach {
        Segment.trackEvent("Opened Changelog")
      },

      color := "inherit",
      fontSize := "0.85714286rem",
      fontWeight := 700,
      Rx {
          VDomModifier.ifTrue(unreadCount() > 0)(
          freeSolid.faGift
        )
      }
    )
  }


  private def initAnounceKit(unreadCount: Var[Int]): Option[() => Unit] = DeployedOnly {
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

      { () =>
        Try(
          announcekit
            .asInstanceOf[js.Dynamic]
            .selectDynamic(s"widget$$$widgetId")
            .open()
        )
        ()
      }
    }
  }.flatMap(_.toOption)
}
