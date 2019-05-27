package wust.webApp.views

import wust.sdk.Colors
import concurrent.duration.DurationInt
import fontAwesome._
import org.scalajs.dom
import scala.scalajs.js
import org.scalajs.dom.window
import colorado._
import wust.webApp.dragdrop._
import fontAwesome.freeSolid
import googleAnalytics.Analytics
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import rx.async._
import rx.async.Platform._
import views.ChannelTreeData
import wust.css.{ CommonStyles, Styles }
import wust.graph._
import wust.ids._
import wust.sdk.{ BaseColors, NodeColor }
import wust.util.RichBoolean
import wust.webApp.{ BrowserDetect, Icons, Ownable }
import wust.webApp.dragdrop.DragItem
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.SharedViewElements._

import scala.collection.breakOut

object LeftSidebar {
  val minWidthSidebar = 40

  def apply(state: GlobalState): VNode = {

    def authStatus(implicit ctx: Ctx.Owner) = AuthControls.authStatus(state, buttonStyle = "basic").map(_(alignSelf.center, marginTop := "30px", marginBottom := "10px"))

    GenericSidebar.left(
      state.leftSidebarOpen,
      config = Ownable { implicit ctx =>
        val toplevelChannels = toplevelChannelsRx(state)
        val invites = invitesRx(state)

        GenericSidebar.Config(
          mainModifier = VDomModifier(
            registerDragContainer(state, DragContainer.Sidebar),
            drag(target = DragItem.Sidebar),
          ),
          openModifier = VDomModifier(
            header(state).apply(Styles.flexStatic),
            channels(state, toplevelChannels, invites),
            newProjectButton(state).apply(
              cls := "newChannelButton-large " + buttonStyles,
              onClick foreach { Analytics.sendEvent("sidebar_open", "newchannel") },
              marginBottom := "10px",
            ),
            // appUpdatePrompt(state).apply(Styles.flexStatic, alignSelf.center, marginTop.auto),
            beforeInstallPrompt(buttonModifier = VDomModifier(
              marginTop := "10px",
              marginBottom := "10px"
            )).apply(Styles.flexStatic, alignSelf.center),
          ),
          overlayOpenModifier = VDomModifier(
            authStatus,
            onClick(false) --> state.leftSidebarOpen
          ),
          expandedOpenModifier = VDomModifier.empty,
          closedModifier = Some(VDomModifier(
            minWidth := s"${minWidthSidebar}px", // this is needed when the hamburger is not rendered inside the sidebar
            hamburger(state),
            channelIcons(state, toplevelChannels, minWidthSidebar),
            newProjectButton(state, "+").apply(
              cls := "newChannelButton-small " + buttonStyles,
              UI.tooltip("right center") := "New Project",
              onClick foreach { Analytics.sendEvent("sidebar_closed", "newchannel") }
            )
          ))
        )
      }
    )
  }

  private val buttonStyles = "tiny basic compact"

  private def toplevelChannelsRx(state: GlobalState)(implicit ctx: Ctx.Owner): Rx[Seq[NodeId]] = Rx {
    val graph = state.rawGraph()
    val userId = state.userId()
    ChannelTreeData.toplevelChannels(graph, userId)
  }

  private def invitesRx(state: GlobalState)(implicit ctx: Ctx.Owner): Rx[Seq[NodeId]] = Rx {
    val graph = state.rawGraph()
    val userId = state.userId()
    ChannelTreeData.invites(graph, userId)
  }

  def banner(state: GlobalState)(implicit ctx: Ctx.Owner) = div(
    Styles.flexStatic,
    Styles.flex,
    alignItems.center,
    fontSize := "16px",
    fontWeight.bold,
    textDecoration := "none",
    div(
      Components.woostIcon,
      fontSize := "28px",
      color := Colors.woost,
      marginRight := "3px",
    ),
    div("Woost", marginRight := "5px"),
    onClick(UrlConfig.default) --> state.urlConfig,
    onClick foreach {
      Analytics.sendEvent("logo", "clicked")
    },
    cursor.pointer
  )

  def header(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(
        Styles.flex,
        alignItems.center,

        hamburger(state),
        banner(state),
        Components.betaSign.apply(fontSize := "12px"),
        syncStatus(state)(ctx)(fontSize := "20px", marginLeft.auto, marginRight := "10px"),
      ),
    )
  }

  def hamburger(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state.leftSidebarOpen
    div(
      Styles.flexStatic,
      padding := "10px",
      fontSize := "20px",
      width := "40px",
      textAlign.center,
      freeSolid.faBars,
      cursor.pointer,
      // TODO: stoppropagation is needed because of https://github.com/OutWatch/outwatch/pull/193
      onClick.stopPropagation foreach {
        Analytics.sendEvent("hamburger", if (leftSidebarOpen.now) "close" else "open")
        leftSidebarOpen() = !leftSidebarOpen.now
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
      )
    )

    val syncedIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "#4EBA4C")
      }),
      fontawesome.icon(freeSolid.faCheck, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      })
    )

    val offlineIcon = fontawesome.layered(
      fontawesome.icon(freeSolid.faCircle, new Params {
        styles = scalajs.js.Dictionary[String]("color" -> "tomato")
      }),
      fontawesome.icon(freeSolid.faBolt, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      })
    )

    val status = {
      val rx = Rx {
        (state.isOnline(), state.isSynced() && !state.isLoading())
      }
      //TODO: scala.rx debounce is not working correctly
      ValueObservable(rx.toTailObservable.debounce(300 milliseconds), rx.now)
    }

    val syncStatusIcon = status.map { status =>
      status match {
        case (true, true)  => span(syncedIcon, UI.tooltip("right center") := "Everything is up to date")
        case (true, false) => span(syncingIcon, UI.tooltip("right center") := "Syncing changes...")
        case (false, _)    => span(offlineIcon, color := "tomato", UI.tooltip("right center") := "Disconnected")
      }
    }

    div(syncStatusIcon)
  }

  def appUpdatePrompt(state: GlobalState)(implicit ctx: Ctx.Owner) =
    div(state.appUpdateIsAvailable.map { _ =>
      button(cls := "tiny ui primary basic button", "Update App", onClick foreach {
        window.location.reload(flag = false)
      })
    })

  // TODO: https://github.com/OutWatch/outwatch/issues/227
  val beforeInstallPromptEvents: Rx[Option[dom.Event]] = Rx.create(Option.empty[dom.Event]) {
    observer: Var[Option[dom.Event]] =>
      dom.window.addEventListener(
        "beforeinstallprompt", { e: dom.Event =>
          e.preventDefault(); // Prevents immediate prompt display
          observer() = Some(e)
        }
      )
  }

  def beforeInstallPrompt(buttonModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner) = {
    div(
      Rx {
        beforeInstallPromptEvents().map { e =>
          button(
            cls := "tiny ui primary basic button", "Install as App", onClick foreach {
              e.asInstanceOf[js.Dynamic].prompt();
              ()
            },
            buttonModifier
          )
        }
      }
    )
  }

  private def expandToggleButton(state: GlobalState, nodeId: NodeId, userId: UserId, expanded: Rx[Boolean])(implicit ctx: Ctx.Owner) = {

    div(
      padding := "3px",
      cursor.pointer,
      Rx {
        if (expanded())
          freeSolid.faAngleDown:VDomModifier
        else
          VDomModifier(freeSolid.faAngleRight, color := "#a9a9a9")
      },
      onClick.stopPropagation.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(!expanded.now), userId)) --> state.eventProcessor.changes
    )
  }

  private val emojiRegex = raw"(:\w+:)".r.unanchored
  @inline def fontSizeByDepth(depth:Int) = s"${math.max(8, 14 - depth)}px"

  private def channels(state: GlobalState, toplevelChannels: Rx[Seq[NodeId]], invites: Rx[Seq[NodeId]]): VDomModifier = div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>

    def channelLine(traverseState: TraverseState, userId: UserId, expanded: Rx[Boolean], hasChildren: Rx[Boolean], depth:Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      val nodeId = traverseState.parentId
      val selected = Rx { (state.page().parentId contains nodeId) && state.view().isContent }
      val node = Rx {
        state.rawGraph().nodesByIdOrThrow(nodeId)
      }

      val nodeWithoutFirstEmoji = {
        Rx {
          node() match {
            case n@Node.Content(_, editable: NodeData.EditableText, _, _, _) =>
              editable.updateStr(emojiRegex.replaceFirstIn(n.str, "")) match {
                case Some(dataWithoutEmoji) =>
                  n.copy(data = dataWithoutEmoji)
                case None => n
              }
            case n => n
          }
        }
      }

      val iconModifier = Rx {
        node().str match {
          case emojiRegex(emoji) => replaceEmoji(emoji).apply(
            alignSelf.flexStart, // vertical align emoji, because it gets confused and sad by flexbox
          ) 
          case _ if selected() => freeSolid.faFolderOpen:VDomModifier
          case _ if !selected() => span(
            freeRegular.faFolder,
            color := BaseColors.sidebarBgHighlight.copy(h = NodeColor.hue(nodeId)).toHex
          )
        }
      }

      div(
        Styles.flex,
        expandToggleButton(state, nodeId, userId, expanded).apply(
          Rx {
            VDomModifier.ifNot(hasChildren())(visibility.hidden)
          }
        ),
        div(
          flexGrow := 1,
          cls := "channel-line",
          Rx {
            VDomModifier(

              VDomModifier.ifTrue(selected())(
                color := Colors.sidebarBg,
                backgroundColor := BaseColors.sidebarBgHighlight.copy(h = NodeColor.hue(nodeId)).toHex,
              ),
              iconModifier,
              renderAsOneLineText(nodeWithoutFirstEmoji())(cls := "channel-name"),
            )
          },

          onChannelClick(state, nodeId),
          onClick foreach { Analytics.sendEvent("sidebar_open", "clickchannel") },
          cls := "node",
          drag(DragItem.Channel(nodeId))
        )
      )
    }

    def channelList(traverseState: TraverseState, userId: UserId, findChildren: (Graph, TraverseState) => Seq[NodeId], depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div.thunkStatic(traverseState.parentId.toStringFast)(Ownable { implicit ctx =>
        val children = Rx {
          val graph = state.rawGraph()
          findChildren(graph, traverseState)
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          state.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        VDomModifier(
          channelLine(traverseState, userId, expanded = expanded, hasChildren = hasChildren, depth = depth),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              paddingLeft := "14px",
              fontSize := fontSizeByDepth(depth),
              children().map { child => channelList(traverseState.step(child), userId, findChildren, depth = depth + 1) }
            ))
          }
        )
      })
    }

    VDomModifier(
      cls := "channels tiny-scrollbar",

      Rx {
        val userId = state.userId()

        VDomModifier(
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), userId, ChannelTreeData.childrenChannels(_, _, userId)))
        )
      },

      Rx {
        val userId = state.userId()

        VDomModifier.ifTrue(invites().nonEmpty)(
          UI.horizontalDivider("invitations"),
          invites().map(nodeId => channelLine(TraverseState(nodeId), userId, expanded = Var(false), hasChildren = Var(false)).apply(
            div(
              cls := "ui icon buttons",
              height := "22px",
              marginRight := "4px",
              marginLeft := "auto",
              button(
                cls := "ui mini compact green button",
                padding := "4px",
                freeSolid.faCheck,
                onClick.mapTo(GraphChanges(addEdges = Array(Edge.Pinned(nodeId, userId), Edge.Notify(nodeId, userId)), delEdges = Array(Edge.Invite(nodeId, userId)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              ),
              button(
                cls := "ui mini compact button",
                padding := "4px",
                freeSolid.faTimes,
                onClick.mapTo(GraphChanges(delEdges = Array(Edge.Invite(nodeId, userId)))) --> state.eventProcessor.changes,
                onClick foreach { Analytics.sendEvent("pageheader", "ignore-invite") }
              )
            )
          ))
        )
      },
    )
  })

  private def channelIcons(state: GlobalState, toplevelChannels: Rx[Seq[NodeId]], size: Int): VDomModifier = div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
    val indentFactor = 3
    val defaultPadding = CommonStyles.channelIconDefaultPadding

    def renderChannel(traverseState: TraverseState, userId: UserId, depth: Int, expanded: Rx[Boolean], hasChildren: Rx[Boolean])(implicit ctx: Ctx.Owner) = {
      val nodeId = traverseState.parentId
      val selected = Rx { (state.page().parentId contains nodeId) && state.view().isContent }
      val node = Rx {
        state.rawGraph().nodesByIdOrThrow(nodeId)
      }

      VDomModifier(
        Rx {
          channelIcon(state, node(), selected, size)(ctx)(
            UI.popup("right center") <-- node.map(_.str),
            onChannelClick(state, nodeId),
            onClick foreach { Analytics.sendEvent("sidebar_closed", "clickchannel") },
            drag(target = DragItem.Channel(nodeId)),
            cls := "node",

            // for each indent, steal padding on left and right
            // and reduce the width, so that the icon keeps its size
            width := s"${size - (depth * indentFactor)}px",
            padding := s"${defaultPadding}px ${defaultPadding - (depth * indentFactor / 2.0)}px",
          )
        }
      )
    }

    def channelList(traverseState: TraverseState, userId: UserId, findChildren: (Graph, TraverseState) => Seq[NodeId], depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div.thunkStatic(traverseState.parentId.toStringFast)(Ownable { implicit ctx =>
        val children = Rx {
          val graph = state.rawGraph()
          findChildren(graph, traverseState)
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          state.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        VDomModifier(
          backgroundColor := "#bbbbbb", // color for indentation space
          renderChannel(traverseState, userId, depth, expanded = expanded, hasChildren = hasChildren),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              paddingLeft := s"${indentFactor}px",
              fontSize := fontSizeByDepth(depth),
              children().map { child => channelList(traverseState.step(child), userId, findChildren, depth = depth + 1) }
            ))
          }
        )
      })
    }

    VDomModifier(
      cls := "channelIcons tiny-scrollbar",

      Rx {
        val userId = state.userId()

        VDomModifier(
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), userId, ChannelTreeData.childrenChannels(_, _, userId)))
        )
      },
    )
  })
  private def channelIcon(state: GlobalState, node: Node, isSelected: Rx[Boolean], size: Int)(implicit ctx: Ctx.Owner): VNode = {
    def iconText(str:String):String = {
      str match {
        case emojiRegex(emoji) => emoji
        case _ => str.trim.take(2)
      }
    }

    div(
      cls := "channelicon",
      width := s"${size}px",
      height := s"${size}px",
      Rx {
        if (isSelected()) VDomModifier(
          backgroundColor := BaseColors.sidebarBgHighlight.copy(h = NodeColor.hue(node.id)).toHex,
          color := "white"
        ) else color := BaseColors.sidebarBgHighlight.copy(h = NodeColor.hue(node.id)).toHex,
      },
      replaceEmoji(iconText(node.str))
    )
  }

  private def onChannelClick(state: GlobalState, nodeId: NodeId) = VDomModifier(
    onClick foreach {
      state.urlConfig.update(_.focus(Page(nodeId)))
      ()
    }
  )
}
