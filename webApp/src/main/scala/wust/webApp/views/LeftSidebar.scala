package wust.webApp.views

import wust.webApp.StagingOnly
import wust.webApp.Icons
import monix.reactive.Observer
import wust.facades.googleanalytics.Analytics
import fontAwesome.{freeSolid, _}
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{Ownable, UI}
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, Colors, NodeColor}
import wust.webApp.{Client, Permission}
import wust.webApp.dragdrop.{DragItem, _}
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, registerDragContainer}
import wust.webApp.views.SharedViewElements._
import wust.webUtil.Elements._
import NewProjectPrompt._
import wust.ids.Feature
import scala.concurrent.duration._

import scala.concurrent.duration.DurationInt
import scala.scalajs.js

object LeftSidebar {
  val minWidthSidebar = 40

  def apply: VNode = {

    def authStatus(implicit ctx: Ctx.Owner) = AuthControls.authStatusOnLightBackground.map(_(marginBottom := "15px", alignSelf.center))

    GenericSidebar.left(
      GlobalState.leftSidebarOpen,
      Var(false),
      config = Ownable { implicit ctx =>
        val invites = invitesRx
        val sidebarWithProjects = Client.storage.sidebarWithProjects.imap(_.getOrElse(true))(Some(_))
        val sidebarFilter = Var[String]("")
        val filteredToplevelChannels = Rx { toplevelChannelsRx(filterStringPredicate(sidebarFilter()), sidebarWithProjects()) }
        val toplevelChannels = Rx { toplevelChannelsRx(_ => true, true) }

        GenericSidebar.Config(
          mainModifier = VDomModifier(
            registerDragContainer( DragContainer.Sidebar),
            drag(target = DragItem.Sidebar)
          ),
          openModifier = VDomModifier(
            header.apply(Styles.flexStatic),
            div(
              Styles.flex,
              flexDirection.column,
              minHeight := "100px",
              channels(filteredToplevelChannels, sidebarWithProjects, sidebarFilter).append(),
              invitations( invites).apply(Styles.flexStatic),
              newProjectButton().apply(
                Styles.flexStatic,
                cls := "newChannelButton-large " + buttonStyles,
                onClick foreach { 
                  FeatureState.use(Feature.CreateProjectFromExpandedLeftSidebar)
                },
                marginBottom := "15px"
              ),
            ),

            UI.accordion(
              content = Seq(
                accordionEntry(
                  "Tags",
                  TagList.body(ViewRender),
                  active = false
                ),
                accordionEntry(
                  "Filters & Deleted Items",
                  FilterWindow.body(Styles.flexStatic),
                  active = false
                )
              ) ++ StagingOnly(
                accordionEntry(
                  FeatureExplorer.toggleButton.apply(marginBottom := "10px"),
                  FeatureExplorer(),
                  active = false
                )).toSeq,
              styles = "styled fluid",
              exclusive = true,
            ).apply(
                Styles.flexStatic,
                marginTop.auto,
                maxHeight := "70%",
                Styles.flex,
                flexDirection.column,
                justifyContent.flexStart,
                fontSize := "12px",
                boxShadow := "none", //explicitly overwrite boxshadow from accordion.
                onMouseDown.stopPropagation.discard, // prevent rightsidebar from closing
              ),
            beforeInstallPrompt(buttonModifier = VDomModifier(
              marginBottom := "15px"
            )).apply(Styles.flexStatic, alignSelf.center),
            div(
              Styles.flexStatic,
              justifyContent.spaceBetween,
              margin := "0 10px 10px 10px",
              Styles.flex,
              AnnouncekitWidget.widget.apply(Styles.flexStatic),
              FeedbackForm(ctx)(Styles.flexStatic)
            )


          ),
          overlayOpenModifier = VDomModifier(
            authStatus,
            Components.reloadButton(fontSize.small, margin := "15px auto 0px auto"),
            onClick(false) --> GlobalState.leftSidebarOpen
          ),
          expandedOpenModifier = VDomModifier.empty,
          closedModifier = Some(VDomModifier(
            minWidth := s"${minWidthSidebar}px", // this is needed when the hamburger is not rendered inside the sidebar
            hamburger,
            channelIcons(toplevelChannels, minWidthSidebar),
            newProjectButton( "+").apply(
              cls := "newChannelButton-small " + buttonStyles,
              UI.tooltip("right center") := "New Project",
              onClick foreach { 
                FeatureState.use(Feature.CreateProjectFromCollapsedLeftSidebar)
              }
            )
          ))
        )
      }
    )
  }

    def accordionEntry(title: VDomModifier, content: VDomModifier, active: Boolean): UI.AccordionEntry = {
      UI.AccordionEntry(
        title = VDomModifier(
          b(title),
          marginTop := "5px",
          padding := "3px",
          Styles.flexStatic
        ),
        content = VDomModifier(
          margin := "5px",
          padding := "0px",
          overflowY.auto,
          content
        ),
        active = active
      )
    }

  private val buttonStyles = "tiny basic compact"

  private def toplevelChannelsRx(filter: Node => Boolean, sidebarWithProjects: Boolean)(implicit ctx: Ctx.Data): Seq[NodeId] = {
    val graph = GlobalState.rawGraph()
    val userId = GlobalState.userId()
    if (sidebarWithProjects) ChannelTreeData.toplevelChannelsOrProjects(graph, userId, filter) else ChannelTreeData.toplevelChannels(graph, userId, filter)
  }

  private def invitesRx(implicit ctx: Ctx.Owner): Rx[Seq[NodeId]] = Rx {
    val graph = GlobalState.rawGraph()
    val userId = GlobalState.userId()
    ChannelTreeData.invites(graph, userId)
  }

  def banner(implicit ctx: Ctx.Owner) = div(
    Styles.flexStatic,
    Styles.flex,
    alignItems.center,
    fontSize := "16px",
    fontWeight.bold,
    textDecoration := "none",
    div(
      WoostLogoComponents.woostIcon,
      fontSize := "28px",
      color := Colors.woost,
      marginRight := "3px"
    ),
    div("Woost", marginRight := "5px"),
    onClick(UrlConfig.default) --> GlobalState.urlConfig,
    onClick foreach {
      FeatureState.use(Feature.ClickLogo)
    },
    cursor.pointer
  )

  def header(implicit ctx: Ctx.Owner): VNode = {
    div(
      div(
        Styles.flex,
        alignItems.center,

        hamburger,
        banner,
        Components.betaSign.apply(fontSize := "12px"),
        syncStatus(ctx)(fontSize := "20px", marginLeft.auto, marginRight := "10px")
      )
    )
  }

  def hamburger(implicit ctx: Ctx.Owner): VNode = {
    import GlobalState.leftSidebarOpen
    div(
      Styles.flexStatic,
      padding := "10px",
      fontSize := "20px",
      width := "40px",
      textAlign.center,
      Icons.hamburger,
      cursor.pointer,
      // TODO: stoppropagation is needed because of https://github.com/OutWatch/outwatch/pull/193
      onClick.stopPropagation foreach {
        FeatureState.use(if (leftSidebarOpen.now) Feature.CloseLeftSidebar else Feature.OpenLeftSidebar)
        leftSidebarOpen() = !leftSidebarOpen.now
      }
    )
  }

  def syncStatus(implicit ctx: Ctx.Owner): VNode = {
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
        styles = scalajs.js.Dictionary[String]("color" -> "#858d94")
      }),
      fontawesome.icon(freeSolid.faBolt, new Params {
        transform = new Transform { size = 10.0 }
        styles = scalajs.js.Dictionary[String]("color" -> "white")
      })
    )

    val status = {
      val rx = Rx {
        (GlobalState.isClientOnline(), GlobalState.isSynced() && !GlobalState.isLoading())
      }
      //TODO: scala.rx debounce is not working correctly
      ValueObservable(rx.toTailObservable.debounce(300 milliseconds), rx.now)
    }

    val syncStatusIcon = status.map { status =>
      status match {
        case (true, true)  => dsl.span(syncedIcon, UI.tooltip("right center") := "Everything is up to date")
        case (true, false) => dsl.span(syncingIcon, UI.tooltip("right center") := "Syncing changes...")
        case (false, _)    => dsl.span(offlineIcon, color := "tomato", UI.tooltip("right center") := "Disconnected")
      }
    }

    div(syncStatusIcon)
  }

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

  private def expandToggleButton(nodeId: NodeId, userId: UserId, expanded: Rx[Boolean])(implicit ctx: Ctx.Owner) = {

    div(
      padding := "1px 3px",
      cursor.pointer,
      cls := "fa-fw", // same width for expand and collapse icon
      Rx {
        if (expanded())
          freeSolid.faAngleDown:VDomModifier
        else
          VDomModifier(freeSolid.faAngleRight, color := "#a9a9a9")
      },
      onClick.stopPropagation.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(!expanded.now), userId)) --> GlobalState.eventProcessor.changes
    )
  }

  @inline def fontSizeByDepth(depth:Int) = s"${math.max(8, 14 - depth)}px"

  def channelLine(traverseState: TraverseState, userId: UserId, expanded: Rx[Boolean], hasChildren: Rx[Boolean], depth:Int = 0, channelModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val nodeId = traverseState.parentId
    val selected = Rx { (GlobalState.page().parentId contains nodeId) && GlobalState.view().isContent }
    val nodeIdx = Rx { GlobalState.rawGraph().idToIdx(nodeId) }
    val isPinned = Rx { nodeIdx().exists(nodeIdx => GlobalState.rawGraph().isPinned(nodeIdx, userIdx = GlobalState.rawGraph().idToIdxOrThrow(userId))) }
    val node = Rx { nodeIdx().map(nodeIdx => GlobalState.rawGraph().nodes(nodeIdx)) }

    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), nodeId)
    }

    div(
      Styles.flex,
      alignItems.center,
      expandToggleButton( nodeId, userId, expanded).apply(
        Rx {
          VDomModifier.ifNot(hasChildren())(visibility.hidden)
        }
      ),
      a(
        isPinned map {
          case true => VDomModifier.empty
          case false => VDomModifier(opacity := 0.5)
        },

        href <-- nodeUrl(nodeId),
        cls := "channel-line",
        Rx {
          VDomModifier(
            VDomModifier.ifTrue(selected())(
              color := Colors.sidebarBg,
              backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(nodeId)).toHex
            ),
          node().map(node => renderProject(node, renderNode = node => renderAsOneLineText(node).apply(cls := "channel-name"), withIcon = true, openFolder = selected()))
          )
        },

        onClick foreach { 
          // needs to be before onChannelClick, because else GlobalState.page is already at the new page
          GlobalState.page.now.parentId match {
            case Some(parentId) if parentId == nodeId => // no switch happening...
            case _ => FeatureState.use(Feature.SwitchPageFromExpandedLeftSidebar)
          }
        },
        onChannelClick( nodeId),
        cls := "node",
        DragComponents.drag(DragItem.Channel(nodeId, traverseState.tail.headOption)),
        permissionLevel.map(Permission.permissionIndicatorIfPublic(_, VDomModifier(fontSize := "0.7em", color.gray, marginLeft.auto, marginRight := "5px"))),
        channelModifier
      ),

      // isPinned map {
      //   case false => button(
      //     marginLeft := "auto",
      //     freeSolid.faBookmark,
      //     cls := "ui button mini compact basic",
      //     onClickDefault.mapTo(GraphChanges.pin(nodeId, GlobalState.userId.now)) --> GlobalState.eventProcessor.changes
      //   )
      //   case true => VDomModifier.empty
      // },
    )
  }

  private def filterStringPredicate(filterString: String): Node => Boolean = {
    if (filterString.isEmpty) _ => true else _.str.toLowerCase.contains(filterString.toLowerCase)
  }

  private def channels(toplevelChannels: Rx[Seq[NodeId]], sidebarWithProjects: Var[Boolean], sidebarFilter: Var[String]): VNode = div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>

    def channelList(traverseState: TraverseState, userId: UserId, depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div.thunk(traverseState.parentId.toStringFast)(depth)(Ownable { implicit ctx =>
        val children = Rx {
          val graph = GlobalState.rawGraph()
          val findChildren = if (sidebarWithProjects()) ChannelTreeData.childrenChannelsOrProjects _ else ChannelTreeData.childrenChannels _
          findChildren(graph, traverseState, userId, filterStringPredicate(sidebarFilter()))
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          GlobalState.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        VDomModifier(
          channelLine( traverseState, userId, expanded = expanded, hasChildren = hasChildren, depth = depth, channelModifier = VDomModifier(flexGrow := 1, flexShrink := 0)),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              paddingLeft := "14px",
              fontSize := fontSizeByDepth(depth),
              children().map { child => channelList(traverseState.step(child), userId, depth = depth + 1) }
            ))
          }
        )
      })
    }

    VDomModifier(
      cls := "channels tiny-scrollbar",

      Rx {
        val userId = GlobalState.userId()

        VDomModifier(
          VDomModifier.ifTrue(toplevelChannels().nonEmpty || sidebarFilter().nonEmpty)(
            div(
              Styles.flex,
              filterInput(sidebarFilter).apply(width := "100%"),
              toggleSidebarWithProjects(sidebarWithProjects).apply(Styles.flexStatic),
              marginBottom := "5px",
            )
          ),
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), userId)),
        )
      },
    )
  })

  private def toggleSidebarWithProjects(sidebarWithProjects: Var[Boolean])(implicit ctx: Ctx.Owner): VNode = button(
    cls := "ui mini compact basic button",
    div(
      cls := "fa-fw",
      sidebarWithProjects map {
        case false => VDomModifier(
          UI.popup("bottom center") := "Show non-bookmarked subprojects",
          freeSolid.faBookmark:VDomModifier
        )
        case true => VDomModifier(
          UI.popup("bottom center") := "Show only bookmarked subprojects",
          freeSolid.faBookOpen:VDomModifier
        )
      }
    ),
    onClick.stopPropagation.foreach(sidebarWithProjects.update(!_))
  )

  private def filterInput(sidebarFilter: Var[String])(implicit ctx: Ctx.Owner): VNode = {
    div(
      onClick.stopPropagation.discard,
      cls := "ui mini fluid icon input",
      input(
        tpe := "text",
        placeholder := "Filter",
        value <-- sidebarFilter,
        onInput.value.debounce(200 millis) --> sidebarFilter
      ),
      i(cls := "search icon")
    )
  }

  private def invitations(invites: Rx[Seq[NodeId]])(implicit ctx: Ctx.Owner) = {
    div(
      Rx {
        val userId = GlobalState.userId()

        VDomModifier.ifTrue(invites().nonEmpty)(
          UI.horizontalDivider("invitations"),
          invites().map{ nodeId =>
            channelLine( TraverseState(nodeId), userId, expanded = Var(false), hasChildren = Var(false),
              channelModifier = VDomModifier(
                flexGrow := 1, // push buttons to the right
                div(
                  cls := "ui icon buttons",
                  margin := "2px 3px 2px auto",
                  button(
                    cls := "mini compact green ui button",
                    padding := "0.5em",
                    i(cls := "icon fa-fw", freeSolid.faCheck),
                    onClick.stopPropagation.foreach {
                      val changes = GraphChanges(
                        addEdges = Array(Edge.Pinned(nodeId, userId), Edge.Notify(nodeId, userId)),
                        delEdges = Array(Edge.Invite(nodeId, userId))
                      )
                      GlobalState.submitChanges(changes)
                      FeatureState.use(Feature.AcceptInvite)
                    }
                  ),
                  button(
                    cls := "mini compact ui button",
                    padding := "0.5em",
                    i(cls := "icon fa-fw", freeSolid.faTimes),
                    onClick.stopPropagation.foreach { 
                      //TODO: sadly a click here still triggers a channel-focus
                      confirm("Ignore and delete invitation?") {
                        val changes = GraphChanges(delEdges = Array(Edge.Invite(nodeId, userId)))
                        GlobalState.submitChanges(changes)
                        FeatureState.use(Feature.IgnoreInvite)
                      }
                    }
                  )
                )
              )
            ).apply(
              paddingBottom := "1px",
              paddingTop := "1px"
            )
          }
        )
      }
    )
  }

  private def channelIcons(toplevelChannels: Rx[Seq[NodeId]], size: Int): VDomModifier = div.thunkStatic(uniqueKey)(Ownable { implicit ctx =>
    val indentFactor = 3
    val maxDepth = 6
    val defaultPadding = CommonStyles.channelIconDefaultPadding

    def renderChannel(traverseState: TraverseState, userId: UserId, depth: Int, expanded: Rx[Boolean], hasChildren: Rx[Boolean])(implicit ctx: Ctx.Owner) = {
      val nodeId = traverseState.parentId
      val selected = Rx { (GlobalState.page().parentId contains nodeId) && GlobalState.view().isContent }
      val node = Rx {
        GlobalState.rawGraph().nodesByIdOrThrow(nodeId)
      }

      val sanitizedDepth = depth.min(maxDepth)

      VDomModifier(
        Rx {
          channelIcon( node(), selected, size)(ctx)(
            UI.popup("right center") <-- node.map(_.str),
            onClick foreach {
              // needs to be before onChannelClick, because else GlobalState.page is already at the new page
              GlobalState.page.now.parentId match {
                case Some(parentId) if parentId == nodeId => // no switch happening...
                case _ => FeatureState.use(Feature.SwitchPageFromCollapsedLeftSidebar)
              }
            },
            onChannelClick( nodeId),
            drag(target = DragItem.Channel(nodeId, traverseState.tail.headOption)),
            cls := "node",

            // for each indent, steal padding on left and right
            // and reduce the width, so that the icon keeps its size
            width := s"${size - (sanitizedDepth * indentFactor)}px",
            padding := s"${defaultPadding}px ${defaultPadding - (sanitizedDepth * indentFactor / 2.0)}px"
          )
        }
      )
    }

    def channelList(traverseState: TraverseState, userId: UserId, findChildren: (Graph, TraverseState) => Seq[NodeId], depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div.thunkStatic(traverseState.parentId.toStringFast)(Ownable { implicit ctx =>
        val children = Rx {
          val graph = GlobalState.rawGraph()
          findChildren(graph, traverseState)
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          GlobalState.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        VDomModifier(
          backgroundColor := "#666", // color for indentation space
          renderChannel(traverseState, userId, depth, expanded = expanded, hasChildren = hasChildren),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              VDomModifier.ifTrue(depth < maxDepth)(paddingLeft := s"${indentFactor}px"),
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
        val userId = GlobalState.userId()

        VDomModifier(
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), userId, ChannelTreeData.childrenChannels(_, _, userId, _ => true)))
        )
      }
    )
  })
  private def channelIcon(node: Node, isSelected: Rx[Boolean], size: Int)(implicit ctx: Ctx.Owner): VNode = {
    def iconText(str:String):String = {
      str match {
        case EmojiReplacer.emojiAtBeginningRegex(emoji) => emoji
        case _ => str.trim.take(2)
      }
    }

    div(
      cls := "channelicon",
      width := s"${size}px",
      height := s"${size}px",
      Rx {
        if (isSelected()) VDomModifier(
          backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(node.id)).toHex,
          color := "white"
        ) else color := BaseColors.pageBg.copy(h = NodeColor.hue(node.id)).toHex,
      },
      replaceEmoji(iconText(node.str))
    )
  }

  private def onChannelClick(nodeId: NodeId) = VDomModifier(
    onClickPreventDefaultExceptCtrl {
      GlobalState.focus(nodeId)
      ()
    }
  )
}
