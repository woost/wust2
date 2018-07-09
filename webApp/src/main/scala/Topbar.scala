package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import org.scalajs.dom.{Event, window}
import org.scalajs.dom

import scala.scalajs.js
import rx._

import wust.webApp.views._
import wust.api.AuthUser
import outwatch.ObserverSink
import fontAwesome._
import fontAwesome.freeSolid._
import fontAwesome.freeRegular
import wust.webApp.outwatchHelpers._
import wust.graph._
import wust.ids._
import wust.webApp.views.{LoginView, PageStyle, View, ViewConfig}
import wust.webApp.views.Elements._
import wust.util.RichBoolean
import wust.sdk.{ChangesHistory, NodeColor}

object Topbar {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = div(
    paddingRight := "5px",
    height := "35px",
    backgroundColor <-- state.pageStyle.darkBgColor,
    color := "white",
    display.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.center,
    header(state)(ctx)(marginRight := "10px"),
    appUpdatePrompt(state)(ctx)(marginRight := "10px"),
    beforeInstallPrompt()(ctx)(marginRight := "10px"),
    undoRedo(state)(ctx)(marginRight.auto),
    notificationSettings()(marginRight := "10px"),
    authentication(state)
  )

  def header(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      display.flex,
      alignItems.center,
      hamburger(state),
      div(
        state.user.map(
          user =>
            viewConfigLink(ViewConfig.default.copy(page = Page.ofUser(user)))(
              "Woost",
              color := "white",
              textDecoration := "none"
            )
        ),
        padding := "5px 5px",
        fontSize := "14px",
        fontWeight.bold
      ),
      syncStatus(state)(ctx)(fontSize := "12px"),
    ),
  }

  def hamburger(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state.sidebarOpen
    div(
      padding := "10px",
      fontSize := "20px",
      width := "40px",
      textAlign.center,
      faBars,
      cursor.pointer,
      // TODO: stoppropagation is needed because of https://github.com/OutWatch/outwatch/pull/193
      onClick --> sideEffect { ev =>
        sidebarOpen() = !sidebarOpen.now; ev.stopPropagation()
      }
    )
  }

  def syncStatus(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val syncingIcon = fontawesome.layer(push => {
      push(fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "#4EBA4C")
      }))
      push(
        fontawesome.icon(
          freeSolid.faSync,
          new Params {
            transform = new Transform { size = 10.0 }
            classes = scalajs.js.Array("fa-spin")
            styles = scalajs.js.Dictionary[String]("color" -> "white")
          }
        )
      )
    })

    val syncedIcon = fontawesome.layer(push => {
      push(fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "#4EBA4C")
      }))
      push(fontawesome.icon(freeSolid.faCheck, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      }))
    })

    val offlineIcon = fontawesome.layer(push => {
      push(fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "tomato")
      }))
      push(fontawesome.icon(freeSolid.faBolt, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      }))
    })

    val isOnline = Observable.merge(
      Client.observable.connected.map(_ => true),
      Client.observable.closed.map(_ => false)
    )
    val isSynced = state.eventProcessor.changesInTransit.map(_.isEmpty)

    val syncStatusIcon = Observable.combineLatestMap2(isOnline, isSynced) {
      case (true, true)  => span(syncedIcon, title := "Everything is up to date")
      case (true, false) => span(syncingIcon, title := "Syncing changes...")
      case (false, _)    => span(offlineIcon, color := "tomato", title := "Disconnected")
    }

    div(
      syncStatusIcon
    )
  }

  def appUpdatePrompt(state: GlobalState)(implicit ctx: Ctx.Owner) =
    div(state.appUpdateIsAvailable.map { _ =>
      button(cls := "tiny ui primary button", "update", onClick --> sideEffect {
        window.location.reload(flag = false)
      })
    })

  def beforeInstallPrompt()(implicit ctx: Ctx.Owner) = {
    val prompt: Rx[Option[dom.Event]] = Rx.create(Option.empty[dom.Event]) {
      observer: Var[Option[dom.Event]] =>
        dom.window.addEventListener(
          "beforeinstallprompt", { e: dom.Event =>
            e.preventDefault(); // Prevents immediate prompt display
            dom.console.log("BEFOREINSTALLPROMPT: ", e)
            observer() = Some(e)
          }
        );
    }

    div(
      Rx {
        prompt().map { e =>
          button(cls := "tiny ui primary button", "install", onClick --> sideEffect {
            e.asInstanceOf[js.Dynamic].prompt(); ()
          })
        }
      }
    )
  }

  def undoRedo(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val historySink = ObserverSink(state.eventProcessor.history.action)
    div(
      state.eventProcessor.changesHistory
        .startWith(Seq(ChangesHistory.empty))
        .combineLatestMap(state.view.toObservable) { (history, view) =>
          div(
            if (view.isContent)
              Seq(
                display.flex,
                style("justify-content") := "space-evenly",
                button(
                  cls := "ui button",
                  faUndo,
                  padding := "5px 10px",
                  marginRight := "2px",
                  fontSize.small,
                  title := "Undo last change",
                  onClick(ChangesHistory.Undo) --> historySink,
                  disabled := !history.canUndo
                ),
                button(
                  cls := "ui button",
                  faRedo,
                  padding := "5px 10px",
                  fontSize.small,
                  title := "Redo last undo change",
                  onClick(ChangesHistory.Redo) --> historySink,
                  disabled := !history.canRedo
                )
              )
            else Seq.empty[VDomModifier]
          )
        }
    )
  }

  val notificationSettings: VNode = {
    div(
      Notifications.permissionStateObservable.map { state =>
        if (state == PermissionState.granted) Option.empty[VNode]
        else if (state == PermissionState.denied)
          Some(
            span(
              freeRegular.faBellSlash,
              color := "tomato",
              title := "Notifications blocked by browser. Reconfigure your browser allow Notifications for this site."
            )
          )
        else
          Some(
            div(
              freeRegular.faBellSlash,
              cursor.pointer,
              onClick --> sideEffect { Notifications.requestPermissions() },
              title := "Enable Notifications"
            )
          )
      }
    )
  }

  def authentication(state: GlobalState)(implicit ctx: Ctx.Owner): VDomModifier =
    state.user.map {
      case user: AuthUser.Assumed  => login(state): VDomModifier
      case user: AuthUser.Implicit => login(state): VDomModifier
      case user: AuthUser.Real =>
        VDomModifier(
          Avatar.user(user.id)(height := "20px"),
          span(user.name, padding := "0 5px"),
          logout
        )
    }

  def login(state: GlobalState)(implicit ctx: Ctx.Owner) = state.viewConfig.map { viewConfig =>
    div(
      viewConfigLink(viewConfig.overlayView(SignupView))("Signup", color := "white"),
      " or ",
      viewConfigLink(viewConfig.overlayView(LoginView))("Login", color := "white")
    )
  }

  val logout =
    button(cls := "tiny compact ui inverted grey button", "Logout", onClick --> sideEffect {
      Client.auth.logout(); ()
    })

}
