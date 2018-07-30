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
import wust.webApp.views.graphview.GraphView

object Topbar {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = div(
    paddingRight := "5px",
    height := "35px",
    backgroundColor <-- state.pageStyle.map(_.sidebarBgColor),
    color := "white",
    transition := "background-color 0.5s", // fades on page change
    display.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.center,

    header(state).apply(marginRight := "10px"),
    appUpdatePrompt(state).apply(marginRight := "10px"),
    beforeInstallPrompt().apply(marginRight := "10px"),
//    undoRedo(state)(ctx)(marginRight.auto),
    viewSwitcher(state).apply(marginRight := "auto"),
    authentication(state)
  )

  def header(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      display.flex,
      alignItems.center,

      hamburger(state),
      state.user.map(
        user =>
          div(
            padding := "5px 5px",
            fontSize := "14px",
            fontWeight.bold,
            "Woost",
            color := "white",
            textDecoration := "none",
            onClick(ViewConfig.default) --> state.viewConfig,
            cursor.pointer
          )
      ),
      div(
        "beta",
        backgroundColor := "#F2711C",
        color := "white",
        borderRadius := "3px",
        padding := "0px 5px",
        fontWeight.bold,
        style("transform") := "rotate(-7deg)",

        marginRight := "5px"
      ),
      syncStatus(state)(ctx)(fontSize := "12px"),
    )
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
    val syncingIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "#4EBA4C")
      }),
      fontawesome.icon(
        freeSolid.faSync,
        new Params {
          transform = new Transform { size = 10.0 }
          classes = scalajs.js.Array("fa-spin")
          styles = scalajs.js.Dictionary[String]("color" -> "white")
        }
      ))

    val syncedIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "#4EBA4C")
      }),
      fontawesome.icon(freeSolid.faCheck, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      }))

    val offlineIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "tomato")
      }),
      fontawesome.icon(freeSolid.faBolt, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      }))

    val syncStatusIcon = Rx {
      (state.isOnline(), state.isSynced()) match {
        case (true, true)  => span(syncedIcon, title := "Everything is up to date")
        case (true, false) => span(syncingIcon, title := "Syncing changes...")
        case (false, _)    => span(offlineIcon, color := "tomato", title := "Disconnected")
      }
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
    val historySink = state.eventProcessor.history.action
    div(
      state.eventProcessor.changesHistory
        .startWith(Seq(ChangesHistory.empty))
        .combineLatestMap(state.view.toObservable) { (history, view) =>
          div(
            if (view.isContent)
              Seq(
                display.flex,
                style("justify-content") := "space-evenly", //TODO dom-types
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

  def viewSwitcher(state:GlobalState)(implicit ctx:Ctx.Owner):VNode = {
    dom.console.log(freeBrands.asInstanceOf[js.Any])
    div(
      display.flex,
      flexDirection.row,
      justifyContent.spaceBetween,
      alignItems.center,
      div(freeRegular.faComments, onClick(ChatView:View) --> state.view, cursor.pointer),
      div(freeSolid.faColumns, onClick(KanbanView:View) --> state.view, cursor.pointer, marginLeft := "5px"),
      div(freeBrands.faCloudsmith, onClick(GraphView:View) --> state.view, cursor.pointer, marginLeft := "5px"),
    )
  }

  def authentication(state: GlobalState)(implicit ctx: Ctx.Owner): VDomModifier =
    state.user.map {
      case user: AuthUser.Assumed  => login(state)
      case user: AuthUser.Implicit => login(state)
      case user: AuthUser.Real => VDomModifier(
        Avatar.user(user.id)(height := "20px"),
        span(user.name, padding := "0 5px"),
        logout(state))
    }

  def login(state: GlobalState)(implicit ctx: Ctx.Owner) =
    div(
      span(
        onClick(state.viewConfig.now.overlayView(SignupView)) --> state.viewConfig,
        "Signup",
        color := "white",
        cursor.pointer
      ),
      " or ",
      span(
        onClick(state.viewConfig.now.overlayView(LoginView)) --> state.viewConfig,
        "Login",
        color := "white",
        cursor.pointer
      )
    )

  def logout(state: GlobalState) =
    button(
      cls := "tiny compact ui inverted grey button",
      "Logout",
      onClick --> sideEffect {
        Client.auth.logout().foreach { _ =>
          state.viewConfig() = state.viewConfig.now.copy(page = Page.empty).overlayView(LoginView)
        }
        ()
      }
    )

}
