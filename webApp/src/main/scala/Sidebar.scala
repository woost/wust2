package wust.webApp

import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import org.scalajs.dom.{Event, window}
import org.scalajs.dom

import scala.scalajs.js
import rx._
import fontAwesome.freeSolid._
import fontAwesome.freeRegular
import wust.webApp.outwatchHelpers._
import wust.graph._
import wust.ids._
import wust.webApp.views.{LoginView, PageStyle, View, ViewConfig}
import wust.webApp.views.Elements._
import wust.util.RichBoolean
import wust.sdk.{ChangesHistory, NodeColor, SyncMode}
import MainViewParts._

object Sidebar {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
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
          VDomModifier(
            height := "100%",
            display.flex,
            flexDirection.column,
            justifyContent.flexStart,
            alignItems.stretch,
            alignContent.stretch,
            channels(state)(ctx)(overflowY.auto),
            newGroupButton(state)(ctx)(
              buttonStyle,
              marginRight := "0",
              marginTop := "5px",
              alignSelf.center,
              flexGrow := 0,
              flexShrink := 0
            ),
          )
        case false =>
          VDomModifier(
            height := "100%",
            display.flex,
            flexDirection.column,
            justifyContent.flexStart,
            alignItems.stretch,
            alignContent.stretch,
            channelIcons(state, 40)(ctx)(overflowY.auto),
            newGroupButton(state, "+")(ctx)(
              buttonStyle,
              marginRight := "0",
              marginTop := "3px",
              paddingLeft := "12px",
              paddingRight := "12px",
              alignSelf.center,
              flexGrow := 0,
              flexShrink := 0
            ),
          )
      }
    )
  }

  def buttonStyle = Seq(
    cls := "tiny compact ui inverted grey button"
  )

  def channels(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    def channelDiv(selected: Boolean, pageStyle: PageStyle) = div(
      paddingRight := "3px",
      display.flex,
      alignItems.center,
      cursor.pointer,
      selected.ifTrueSeq(
        Seq(
          color := pageStyle.darkBgColor.toHex,
          backgroundColor := pageStyle.darkBgColorHighlight.toHex
        )
      )
    )

    div(
      color := "#C4C4CA",
      Rx {
        state.channels().map {
          p =>
            val selected = state.page().parentIds.contains(p.id)
            channelDiv(selected, state.pageStyle())(
              paddingRight := "5px",
              //TODO: inner state.page obs again
              channelIcon(state, p, state.page.map(_.parentIds.contains(p.id)), 30)(ctx)(
                marginRight := "5px"
              ),
              p.data.str,
              onChannelClick(ChannelAction.Post(p.id))(state),
              title := p.id.toCuidString
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

  def noChannelIcon(selected: Rx[Boolean], size: Int)(implicit ctx: Ctx.Owner) = div(
    margin := "0",
    width := s"${size}px",
    height := s"${size}px",
    backgroundColor <-- selected.map {
      case true  => "grey" //TODO: better
      case false => "white"
    }
  )

  def channelIcons(state: GlobalState, size: Int)(implicit ctx: Ctx.Owner): VNode = {
    div(
      state.channels.map(_.map { p =>
        channelIcon(state, p, state.page.map(_.parentIds.contains(p.id)), size)(ctx)(
          onChannelClick(ChannelAction.Post(p.id))(state)
        )
      }),
      noChannelIcon(state.page.map(_.mode == PageMode.Orphans), size)(ctx)(
        onChannelClick(ChannelAction.Page(PageMode.Orphans))(state)
      )
    )
  }

  def channelIcon(state: GlobalState, post: Node, selected: Rx[Boolean], size: Int)(
      implicit ctx: Ctx.Owner
  ): VNode = {
    div(
      margin := "0",
      width := s"${size}px",
      height := s"${size}px",
      title := post.data.str,
      cursor.pointer,
      backgroundColor := PageStyle.Color.baseBg
        .copy(h = NodeColor.genericBaseHue(post.id))
        .toHex, //TODO: make different post color tones better accessible
      //TODO: https://github.com/OutWatch/outwatch/issues/187
      opacity <-- selected.map(if (_) 1.0 else 0.75),
      padding <-- selected.map(if (_) "2px" else "4px"),
      border <-- selected.map(
        if (_)
          s"2px solid ${PageStyle.Color.baseBgDark.copy(h = NodeColor.genericBaseHue(post.id)).toHex}"
        else "none"
      ),
      Avatar.node(post.id)
    )
  }

  sealed trait ChannelAction extends Any
  object ChannelAction {
    case class Post(id: NodeId) extends AnyVal with ChannelAction
    case class Page(mode: PageMode) extends AnyVal with ChannelAction
  }
  private def onChannelClick(action: ChannelAction)(state: GlobalState)(implicit ctx: Ctx.Owner) =
    onClick.map { e =>
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
      val contentView = if (state.view.now.isContent) state.view.now else View.default
      state.viewConfig() = state.viewConfig.now.copy(page = page, view = contentView)
    }
}
