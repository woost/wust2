package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import fontAwesome.freeSolid._
import wust.webApp.outwatchHelpers._
import wust.graph._
import wust.ids._
import wust.webApp.views.{LoginView, PageStyle, View, ViewConfig}
import wust.webApp.views.Elements._
import wust.util.RichBoolean
import wust.sdk.{ChangesHistory, PostColor, SyncMode}

object Sidebar {
  import MainViewParts._

  def buttonStyle = Seq(
    width := "100%",
    padding := "5px 3px",
    margin := "0px"
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

  def topbar(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = div(
    paddingLeft := "5px",
    paddingRight := "5px",
    height := "35px",
    backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
    color := "white",
    display.flex,
    flexDirection.row,
    justifyContent.spaceBetween,
    alignItems.baseline,

    header(state),
    undoRedo(state)(ctx)(marginLeft := "20px", marginRight.auto),
    authentication(state)
  )

  def sidebar(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    import state.sidebarOpen
    div(
      minWidth := "40px",
      maxWidth := "250px",
      backgroundColor <-- state.pageStyle.map(_.darkBgColor.toHex),
      color := "white",
      transition := "flex-basis 0.2s, background-color 0.5s",
//      flexBasis <-- sidebarOpen.map { case true => "175px"; case false => "30px" },

      sidebarOpen.map {
        case true =>
          div(
            height := "100%",

            display.flex,
            flexDirection.column,
            justifyContent.flexStart,
            alignItems.stretch,
            alignContent.stretch,

            channels(state)(ctx)(overflowY.auto),
            newGroupButton(state)(ctx)(buttonStyle)(flexGrow := 0, flexShrink := 0),
            br(),
            notificationSettings(flexGrow := 0, flexShrink := 0)
          )
        case false =>
          div(
            height := "100%",

            display.flex,
            flexDirection.column,
            justifyContent.flexStart,
            alignItems.stretch,
            alignContent.stretch,

            channelIcons(state, 40)(ctx)(overflowY.auto),
            newGroupButton(state, "+")(ctx)(buttonStyle)(flexGrow := 0, flexShrink := 0),
          )
      }
    )
  }

  def hamburger(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    import state.sidebarOpen
    div(
      faBars,
      padding := "7px",
      cursor.pointer,
      onClick --> sideEffect{ev => sidebarOpen() = !sidebarOpen.now; ev.stopPropagation()})
  }

  def header(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    div(
      display.flex, alignItems.baseline,
      // TODO: stoppropagation is needed because of https://github.com/OutWatch/outwatch/pull/193
      hamburger(state),
      div(
        state.user.map(user =>
          viewConfigLink(ViewConfig.default.copy(page = Page.ofUser(user)))("Woost", color := "white", textDecoration := "none")
        ),
        padding := "5px 5px",
        fontSize := "14px",
        fontWeight.bold
      ),
      syncStatus(state)(ctx)(fontSize := "9px"),
    ),
  }

  def channels(state: GlobalState)(implicit ctx:Ctx.Owner): VNode = {
    def channelDiv(selected: Boolean, pageStyle: PageStyle) = div(
      paddingRight := "3px",
      display.flex, alignItems.center,
      cursor.pointer,
      selected.ifTrueSeq(Seq(
        color := pageStyle.darkBgColor.toHex,
        backgroundColor := pageStyle.darkBgColorHighlight.toHex
      ))
    )

    div(
      color := "#C4C4CA",
      Rx {
        state.channels().map { p =>
          val selected = state.page().parentIds.contains(p.id)
          channelDiv(selected, state.pageStyle())(
            //TODO: inner state.page obs again
            channelIcon(state, p, state.page.map(_.parentIds.contains(p.id)), 30)(ctx)(marginRight := "5px"),
            p.content.str,
            onChannelClick(ChannelAction.Post(p.id))(state),
            title := p.id
          )
        }
      },
      Rx {
        channelDiv(state.page().mode == PageMode.Orphans, state.pageStyle())(
            //TODO: inner state.page obs again
          noChannelIcon(state.page.map(_.mode == PageMode.Orphans), 30)(ctx)(marginRight := "5px"),
          PageMode.Orphans.toString,
          onChannelClick(ChannelAction.Page(PageMode.Orphans))(state)
        )
      }
    )
  }

  def noChannelIcon(selected: Rx[Boolean], size:Int)(implicit ctx: Ctx.Owner) = div(
    margin := "0",
    width := s"${size}px",
    height := s"${size}px",
    backgroundColor <-- selected.map {
      case true => "grey" //TODO: better
      case false => "white"
    }
  )

  def channelIcons(state: GlobalState, size:Int)(implicit ctx:Ctx.Owner): VNode = {
    div(
      state.channels.map(_.map{ p =>
        channelIcon(state, p, state.page.map(_.parentIds.contains(p.id)), size)(ctx)(
          onChannelClick(ChannelAction.Post(p.id))(state)
        )
      }),
      noChannelIcon(state.page.map(_.mode == PageMode.Orphans), size)(ctx)(
        onChannelClick(ChannelAction.Page(PageMode.Orphans))(state)
      )
    )
  }

  def channelIcon(state: GlobalState, post:Post, selected:Rx[Boolean], size:Int)(implicit ctx:Ctx.Owner): VNode = {
    div(
      margin := "0",
      width := s"${size}px",
      height := s"${size}px",
      title := post.content.str,
      cursor.pointer,
      backgroundColor := PageStyle.Color.baseBg.copy(h = PostColor.genericBaseHue(post.id)).toHex, //TODO: make different post color tones better accessible
      //TODO: https://github.com/OutWatch/outwatch/issues/187
      opacity <-- selected.map(if(_) 1.0 else 0.75),
      padding <-- selected.map(if(_) "2px" else "4px"),
      border <-- selected.map(if(_) s"2px solid ${PageStyle.Color.baseBgDark.copy(h = PostColor.genericBaseHue(post.id)).toHex}" else "none"),
      Avatar.post(post.id)
    )
  }

  sealed trait ChannelAction extends Any
  object ChannelAction {
    case class Post(id: PostId) extends AnyVal with ChannelAction
    case class Page(mode: PageMode) extends AnyVal with ChannelAction
  }
  private def onChannelClick(action: ChannelAction)(state: GlobalState)(implicit ctx: Ctx.Owner) = onClick.map { e =>
    val page = state.page.now
    //TODO if (e.shiftKey) {
    action match {
      case ChannelAction.Post(id) =>
        if (e.ctrlKey) {
          val filtered = page.parentIds.filterNot(_ == id)
          val parentIds =
            if (filtered.size == page.parentIds.size) page.parentIds :+ id
            else if (filtered.nonEmpty) filtered
            else Seq(id)
          page.copy(parentIds = parentIds)
        } else Page(Seq(id))
      case ChannelAction.Page(mode) =>
        val newMode = if (page.mode != mode) mode else PageMode.Default
        if (e.ctrlKey) page.copy(mode = newMode) else Page(Seq.empty, mode = newMode)
    }
  } --> sideEffect { page =>
    if (!state.view.now.isContent) state.view() = View.default
    state.page() = page
    //TODO: Why does Var.set not work?
    // Var.set(
    //   state.page -> page,
    //   state.view -> view
    // )
  }
}
