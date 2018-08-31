package wust.webApp.views

import fontAwesome.{freeRegular, _}
import fontAwesome.freeSolid._
import googleAnalytics.Analytics
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.AuthUser
import wust.css.Styles
import wust.graph._
import wust.sdk.ChangesHistory
import wust.util.RichBoolean
import wust.webApp.Client
import wust.webApp.outwatchHelpers._
import wust.webApp.state._

import scala.scalajs.js

object Topbar {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = div(
    paddingRight := "5px",
    height := "35px",
    backgroundColor <-- state.pageStyle.map(_.sidebarBgColor),
    color := "white",
    transition := "background-color 0.5s", // fades on page change
    cls := "topbar",
    Styles.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.center,

    header(state).apply(marginRight := "10px"),
    appUpdatePrompt(state).apply(marginRight := "10px"),
    beforeInstallPrompt().apply(marginRight := "10px"),
    //    undoRedo(state)(ctx)(marginRight.auto),
    Rx {
      state.view().isContent.ifTrue[VDomModifier](
        viewSwitcher(state).apply(marginLeft.auto, marginRight.auto)
      )
    },
    FeedbackForm(state)(ctx)(marginLeft.auto, Styles.flexStatic),
    Rx {
      (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](
        authentication(state)
      )
    }
  )

  def banner(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    padding := "5px 5px",
    fontSize := "14px",
    fontWeight.bold,
    "Woost",
    color := "white",
    textDecoration := "none",
    onClick(ViewConfig.default) --> state.viewConfig,
    onClick --> sideEffect {
      Analytics.sendEvent("logo", "clicked")
    },
    cursor.pointer
  )

  val betaSign = div(
    "beta",
    backgroundColor := "#F2711C",
    color := "white",
    borderRadius := "3px",
    padding := "0px 5px",
    fontWeight.bold,
    style("transform") := "rotate(-7deg)",

    marginRight := "5px"
  )

  def header(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      alignItems.center,

      hamburger(state),
      Rx {
        (state.screenSize() != ScreenSize.Small).ifTrueSeq[VDomModifier](Seq(
          banner(state),
          betaSign,
        ))
      },
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
        Analytics.sendEvent("hamburger", if(sidebarOpen.now) "close" else "open")
        sidebarOpen() = !sidebarOpen.now;
        ev.stopPropagation()
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
          transform = new Transform {size = 10.0 }
          classes = scalajs.js.Array("fa-spin")
          styles = scalajs.js.Dictionary[String]("color" -> "white")
        }
      ))

    val syncedIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "#4EBA4C")
      }),
      fontawesome.icon(freeSolid.faCheck, new Params {
        transform = new Transform {size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      }))

    val offlineIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "tomato")
      }),
      fontawesome.icon(freeSolid.faBolt, new Params {
        transform = new Transform {size = 10.0 }
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
          button(cls := "tiny ui primary button", "Install as App", onClick --> sideEffect {
            e.asInstanceOf[js.Dynamic].prompt();
            ()
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
            if(view.isContent)
              VDomModifier(
                Styles.flex,
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

  def viewSwitcher(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    def viewId(view:View) = s"viewswitcher_${view.viewKey}"
    def MkLabel(currentView: View, pageStyle: PageStyle, targetView: View, icon: IconDefinition) = {
      label(`for` := viewId(targetView), icon, onClick(targetView) --> state.view, cursor.pointer,
        (currentView.viewKey == targetView.viewKey).ifTrue[VDomModifier](Seq(
          color := "#111111",
          //borderTop(2 px, solid, pageStyle.bgLightColor)
          backgroundColor := pageStyle.bgColor
        ))
      )
    }

    def MkInput(currentView: View, pageStyle: PageStyle, targetView: View) = {
      input(display.none, id := viewId(targetView), `type` := "radio", name := "viewswitcher",
        (currentView.viewKey == targetView.viewKey).ifTrue[VDomModifier](Seq(checked := true, cls := "checked")),
          onInput --> sideEffect {
          Analytics.sendEvent("viewswitcher", "switch", currentView.viewKey)
        }
      )
    }

    div(
      cls := "viewbar",
      Styles.flex,
      flexDirection.row,
      justifyContent.spaceBetween,
      alignItems.center,

      Rx {
        val currentView = state.view()
        val pageStyle = state.pageStyle()
        Seq(
          MkInput(currentView, pageStyle, View.Thread),
          MkLabel(currentView, pageStyle, View.Thread, freeSolid.faStream),
          MkInput(currentView, pageStyle, View.Chat),
          MkLabel(currentView, pageStyle, View.Chat, freeRegular.faComments),
          MkInput(currentView, pageStyle, View.Kanban),
          MkLabel(currentView, pageStyle, View.Kanban, freeSolid.faColumns),
          MkInput(currentView, pageStyle, View.Graph),
          MkLabel(currentView, pageStyle, View.Graph, freeBrands.faCloudsmith)
        )
      }
    )

  }

  def authentication(state: GlobalState)(implicit ctx: Ctx.Owner): VDomModifier =
    state.user.map {
      case user: AuthUser.Assumed  => login(state).apply(Styles.flexStatic)
      case user: AuthUser.Implicit => login(state).apply(Styles.flexStatic)
      case user: AuthUser.Real     => div(
        Styles.flex,
        alignItems.center,
        div(
          Styles.flex,
          alignItems.center,
          Avatar.user(user.id)(height := "20px", cls := "avatar"),
          span(
            user.name,
            padding := "0 5px",
            wordWrap := "break-word",
            style("word-break") := "break-word",
          ),
          cursor.pointer,
          onClick[View](View.UserSettings) --> state.view,
          onClick --> sideEffect { Analytics.sendEvent("topbar", "avatar") },
        ),
        logout(state))
    }

  def login(state: GlobalState)(implicit ctx: Ctx.Owner) =
    div(
      button(
        cls := "tiny compact ui inverted button",
        "Signup",
        onClick(state.viewConfig.now.showViewWithRedirect(View.Signup)) --> state.viewConfig,
        onClick --> sideEffect {
          Analytics.sendEvent("topbar", "signup")
        },
      ),
      button(
        cls := "tiny compact ui inverted button",
        "Login",
        onClick(state.viewConfig.now.showViewWithRedirect(View.Login)) --> state.viewConfig,
        onClick --> sideEffect {
          Analytics.sendEvent("topbar", "login")
        },
      )
    )

  def logout(state: GlobalState) =
    button(
      cls := "tiny compact ui inverted grey button",
      "Logout",
      onClick --> sideEffect {
        Client.auth.logout().foreach { _ =>
          state.viewConfig() = state.viewConfig.now.copy(page = Page.empty).showViewWithRedirect(View.Login)
        }
        Analytics.sendEvent("topbar", "logout")
      }
    )

}
