package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webApp.fontAwesome.freeSolid._
import wust.webApp.outwatchHelpers._
import wust.ids._
import wust.webApp.views.{LoginView, PageStyle, View}
import wust.util.RichBoolean
import wust.sdk.{ChangesHistory, PostColor, SyncMode}

object Sidebar {
  import MainViewParts._

  def buttonStyle = Seq(
    width := "100%",
    padding := "5px 3px",
  )

  val notificationSettings: VNode = {
    div(
      Notifications.permissionStateObservable.map { state =>
        if (state == PermissionState.granted) Option.empty[VNode]
        else if (state == PermissionState.denied) Some(
          span("Notifications blocked", title := "To enable Notifications for this site, reconfigure your browser to not block Notifications from this domain.")
        ) else Some(
          button("Enable Notifications",
            padding := "5px 3px",
            width := "100%",
            onClick --> sideEffect { Notifications.requestPermissions() },
            buttonStyle
          )
        )
      }
    )
  }

  def sidebar(state: GlobalState)(implicit owner:Ctx.Owner): VNode = {
    import state.sidebarOpen
    state.sidebarOpen.debug("sidopen")
    div(
      id := "sidebar",
      backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
      color := "white",
      transition := "flex-basis 0.2s, background-color 0.5s",
      overflowY.auto,
//      flexBasis <-- sidebarOpen.map { case true => "175px"; case false => "30px" },

      sidebarOpen.map {
        case true =>
          div(
            div(
              display.flex, alignItems.baseline,
              // TODO: stoppropagation is needed because of https://github.com/OutWatch/outwatch/pull/193
              div(faBars, padding := "7px", cursor.pointer, onClick --> sideEffect{ev => sidebarOpen() = false; ev.stopPropagation()}),
              div("Woost", padding := "5px 5px", fontWeight.bold, fontSize := "18px"),
              syncStatus(state)(owner)(fontSize := "9px")
            ),
            undoRedo(state),
            br(),
            channels(state),
            br(),
            newGroupButton(state)(owner)(buttonStyle),
            authentication(state),
            notificationSettings
          )
        case false =>
          div(
            div(faBars, padding := "7px", cursor.pointer, onClick(true) --> sidebarOpen),
            channelIcons(state)
          )
      }
    )
  }

  def channels(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      color := "#C4C4CA",
      Rx {
        state.highLevelPosts().map{ p => 
          val selected = state.page().parentIds.contains(p.id)
          div(
            paddingRight := "3px",
            display.flex, alignItems.center,
            channelIcon(state, p.id, state.page.map(_.parentIds.contains(p.id)))(ctx)(marginRight := "5px"),
            p.content,
            cursor.pointer,
            onChannelClick(p.id)(state),
            title := p.id,
            selected.ifTrueSeq(Seq(
              color := state.pageStyle().darkBgColor.toHex,
              backgroundColor := state.pageStyle().darkBgColorHighlight.toHex
            ))
        )
        }
      }
    )
  }

  def channelIcons(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      state.highLevelPosts.map(_.map{p => channelIcon(state, p.id, state.page.map(_.parentIds.contains(p.id)))})
    )
  }

  
  def channelIcon(state: GlobalState, postId:PostId, selected:Rx[Boolean])(implicit ctx:Ctx.Owner): VNode = {
    div(
      margin := "0",
      width := "30px",
      height := "30px",
      cursor.pointer,
      onChannelClick(postId)(state),
      backgroundColor := PageStyle.Color.baseBg.copy(h = PostColor.genericBaseHue(postId)).toHex, //TODO: make different post color tones better accessible
      //TODO: https://github.com/OutWatch/outwatch/issues/187
      opacity <-- selected.map(if(_) 1.0 else 0.75),
      padding <-- selected.map(if(_) "2px" else "4px"),
      border <-- selected.map(if(_) s"2px solid ${PageStyle.Color.baseBgDark.copy(h = PostColor.genericBaseHue(postId)).toHex}" else "none"),
      Avatar.post(postId)
    )
  }

  private def onChannelClick(id: PostId)(state: GlobalState)(implicit ctx: Ctx.Owner) = onClick.map { e =>
    val page = state.page.now
    //TODO if (e.shiftKey) {
    val newParents = if (e.ctrlKey) {
      val filtered = page.parentIds.filterNot(_ == id)
      if (filtered.size == page.parentIds.size) page.parentIds :+ id
      else if (filtered.nonEmpty) filtered
      else Seq(id)
    } else Seq(id)

    page.copy(parentIds = newParents)
    } --> sideEffect { page =>
      state.view() = View.default
      state.page() = page
      //TODO: Why does Var.set not work?
      // Var.set(
      //   state.page -> page,
      //   state.view -> view
      // )
    }
}
