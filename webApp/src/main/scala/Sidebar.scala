package wust.webApp

import wust.css.Styles
import org.scalajs.dom.experimental.permissions.PermissionState
import outwatch.AsVDomModifier
import outwatch.dom._
import outwatch.dom.dsl._
import org.scalajs.dom.{Event, window}
import org.scalajs.dom
import cats.effect.IO

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
import wust.sdk.{BaseColors, ChangesHistory, NodeColor}
import MainViewParts._
import scala.concurrent.duration._

object Sidebar {

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state.sidebarOpen

    div(
      cls := "sidebar",
      backgroundColor <-- state.pageStyle.map(_.sidebarBgColor),
//      flexBasis <-- sidebarOpen.map { case true => "175px"; case false => "30px" },
      sidebarOpen.map {
        case true => VDomModifier(
          channels(state)(ctx),
          newGroupButton(state)(ctx)(
            cls := "newGroupButton-large " + buttonStyles,
          ),
          state.screenSize.map {
            case ScreenSize.Small => VDomModifier(
              width := "100%",
              height := "100%",
              position.absolute,
              zIndex := 10,
              onClick(false) --> state.sidebarOpen
            )
            case _ => VDomModifier(
              minWidth := "40px",
              maxWidth := "250px"
            )
          }
        )
        case false => VDomModifier(
          channelIcons(state, 40)(ctx),
          newGroupButton(state, "+")(ctx)(
            cls := "newGroupButton-small " + buttonStyles,
          )
        )
      },
      registerDraggableContainer(state)
    )
  }

  val buttonStyles = Seq("tiny", "compact", "inverted", "grey").mkString(" ")

  def channels(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    def channelDiv(selected: Boolean, pageStyle: PageStyle) = div(
      cls := "channel",
      selected.ifTrueSeq(
        Seq(
          color := pageStyle.sidebarBgColor,
          backgroundColor := pageStyle.sidebarBgHighlightColor
        )
      )
    )

    div(
      cls := "channels",
      Rx {
        val allChannels = state.graph().nodesById.get(state.user().channelNodeId).toSeq ++ state.channels()
        val page = state.page()
        VDomModifier(
          allChannels.map {
            p =>
              val selected = page.parentIds.contains(p.id)
              channelDiv(selected, state.pageStyle())(
                cls := "node",
                draggableAs(state, DragItem.Channel(p.id)),
                dragTarget(DragItem.Channel(p.id)),
                paddingRight := "5px",
                //TODO: inner state.page obs again
                channelIcon(state, p, page.parentIds.contains(p.id), 30)(ctx)(
                  marginRight := "5px"
                ),
                p.data.str,
                onChannelClick(ChannelAction.Post(p.id))(state),
                title := p.id.toCuidString
              )
          },
          channelDiv(page.mode == PageMode.Orphans, state.pageStyle())(
            //TODO: inner state.page obs again
            noChannelIcon(page.mode == PageMode.Orphans)(ctx)(marginRight := "5px"),
            PageMode.Orphans.toString,
            onChannelClick(ChannelAction.Page(PageMode.Orphans))(state)
          )
        )
      }
    )
  }

  def noChannelIcon(selected: Boolean)(implicit ctx: Ctx.Owner) = div(
    cls := "noChannelIcon",
    backgroundColor := (selected match {
      case true  => "grey" //TODO: better
      case false => "white"
    })
  )

  def channelIcons(state: GlobalState, size: Int)(implicit ctx: Ctx.Owner): VNode = {
    div(
      cls := "channelIcons",
      Rx {
        val allChannels = state.graph().nodesById.get(state.user().channelNodeId).toSeq ++ state.channels()
        val page = state.page()
        VDomModifier(
          allChannels.map { p =>
            channelIcon(state, p, page.parentIds.contains(p.id), size)(ctx)(
              onChannelClick(ChannelAction.Post(p.id))(state),
              draggableAs(state, DragItem.Channel(p.id)),
              dragTarget(DragItem.Channel(p.id)),
              cls := "node"
            )
          },
          noChannelIcon(page.mode == PageMode.Orphans)(ctx)(
            onChannelClick(ChannelAction.Page(PageMode.Orphans))(state)
          )
        )
      }
    )
  }

  def channelIcon(state: GlobalState, post: Node, selected: Boolean, size: Int)(
      implicit ctx: Ctx.Owner
  ): VNode = {
    div(
      cls := "channelicon",
      Styles.flexStatic,
      margin := "0",
      width := s"${size}px",
      height := s"${size}px",
      title := post.data.str,
      cursor.pointer,
      backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(post.id)).toHex,
      opacity := (if (selected) 1.0 else 0.75),
      padding := (if (selected) "2px" else "4px"),
      border := (
        if (selected) s"2px solid ${BaseColors.sidebarBg.copy(h = NodeColor.hue(post.id)).toHex}"
        else "none"),
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
