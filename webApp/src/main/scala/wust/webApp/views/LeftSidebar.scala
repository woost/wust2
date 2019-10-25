package wust.webApp.views

import fontAwesome.{ freeSolid, _ }
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import outwatch.reactive._
import rx._
import wust.css.{ CommonStyles, Styles }
import wust.graph._
import wust.ids.{ Feature, _ }
import wust.sdk.{ BaseColors, Colors, NodeColor }
import wust.webApp.dragdrop.{ DragItem, _ }
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{ drag, registerDragContainer }
import wust.webApp.views.NewProjectPrompt._
import wust.webApp.views.SharedViewElements._
import wust.webApp.{ Client, Icons, Permission }
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ Ownable, UI }

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
            registerDragContainer(DragContainer.Sidebar),
            drag(target = DragItem.Sidebar)
          ),
          openModifier = VDomModifier(
            header.apply(Styles.flexStatic),
            div(
              Styles.flex,
              flexDirection.column,
              minHeight := "100px",
              channels(filteredToplevelChannels, sidebarWithProjects, sidebarFilter).append(),
              invitations(invites).apply(Styles.flexStatic),
              newProjectButton().apply(
                Styles.flexStatic,
                cls := "newChannelButton-large " + buttonStyles,
                onClick foreach {
                  FeatureState.use(Feature.CreateProjectFromExpandedLeftSidebar)
                },
                marginBottom := "15px"
              ),
            ),

            Rx {
              val viewIsContent = GlobalState.viewIsContent()

              UI.accordion(
                content = Seq.empty ++
                  (if (viewIsContent) Seq(
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
                  )
                  else Seq.empty) :+
                  accordionEntry(
                    FeatureExplorer.toggleButton.apply(marginBottom := "10px"),
                    FeatureExplorer(),
                    active = false
                  ),
                styles = "styled fluid",
                exclusive = false,
              ).apply(
                  overflowY.auto,
                  Styles.flexStatic,
                  marginTop.auto,
                  maxHeight := "70%",
                  Styles.flex,
                  flexDirection.column,
                  justifyContent.flexStart,
                  fontSize := "12px",
                  boxShadow := "none", //explicitly overwrite boxshadow from accordion.
                  onClick.stopPropagation.discard, // prevent left sidebar from closing
                )
            },
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
            onClick.use(false) --> GlobalState.leftSidebarOpen,
            authStatus,
            Components.reloadButton(fontSize.small, margin := "15px auto 0px auto"),
          ),
          expandedOpenModifier = VDomModifier.empty,
          closedModifier = Some(VDomModifier(
            minWidth := s"${minWidthSidebar}px", // this is needed when the hamburger is not rendered inside the sidebar
            hamburger,
            channelIcons(toplevelChannels, minWidthSidebar),
            newProjectButton("+").apply(
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
    onClick.use(UrlConfig.default) --> GlobalState.urlConfig,
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
        div(width := "50px", height := "20px", onMultiClickActivateDebugging),
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
      val isSynced = Rx {
        GlobalState.isSynced() && !GlobalState.isLoading()
      }
      SourceStream.combineLatest(GlobalState.isClientOnline.distinctOnEquals, isSynced)
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

  val beforeInstallPromptEvents: SourceStream[dom.Event] = events.window.eventProp("beforeinstallprompt").preventDefault

  def beforeInstallPrompt(buttonModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner) = {
    div(
      beforeInstallPromptEvents.map { e =>
        button(
          cls := "tiny ui primary basic button", "Install as App", onClick foreach {
            e.asInstanceOf[js.Dynamic].prompt();
            ()
          },
          buttonModifier
        )
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
          freeSolid.faAngleDown: VDomModifier
        else
          VDomModifier(freeSolid.faAngleRight, color := "#a9a9a9")
      },
      onClick.stopPropagation.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(!expanded.now), userId)) --> GlobalState.eventProcessor.changes
    )
  }

  @inline def fontSizeByDepth(depth: Int) = s"${math.max(8, 14 - depth)}px"

  def channelLine(traverseState: TraverseState, userId: UserId, expanded: Rx[Boolean], hasChildren: Rx[Boolean], depth: Int = 0, channelModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val nodeId = traverseState.parentId
    val selected = Rx { (GlobalState.page().parentId contains nodeId) && GlobalState.viewIsContent() }
    val nodeIdx = Rx { GlobalState.rawGraph().idToIdx(nodeId) }
    val isPinned = Rx { nodeIdx().exists(nodeIdx => GlobalState.rawGraph().isPinned(nodeIdx, userIdx = GlobalState.rawGraph().idToIdxOrThrow(userId))) }
    val node = Rx { nodeIdx().map(nodeIdx => GlobalState.rawGraph().nodes(nodeIdx)) }

    val permissionLevel = Rx {
      Permission.resolveInherited(GlobalState.rawGraph(), nodeId)
    }

    div(
      Styles.flex,
      alignItems.center,
      expandToggleButton(nodeId, userId, expanded).apply(
        Rx {
          VDomModifier.ifNot(hasChildren())(visibility.hidden)
        }
      ),
      a(
        isPinned map {
          case true  => VDomModifier.empty
          case false => VDomModifier(opacity := 0.5)
        },

        href <-- nodeUrl(nodeId),
        cls := "channel-line",
        Rx {
          VDomModifier.ifTrue(selected())(
            color := Colors.sidebarBg,
            backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(nodeId)).toHex
          )
        },

        Rx {
          node().map { node =>
            renderProject(node, renderNode = node => renderAsOneLineText(node).apply(cls := "channel-name"), withIcon = true, openFolder = selected())
          }
        },

        onClick foreach {
          // needs to be before onChannelClick, because else GlobalState.page is already at the new page
          GlobalState.page.now.parentId match {
            case Some(parentId) if parentId == nodeId => // no switch happening...
            case _                                    => FeatureState.use(Feature.SwitchPageFromExpandedLeftSidebar)
          }
        },
        onChannelClick(nodeId),
        cls := "node",
        DragComponents.drag(DragItem.Channel(nodeId, traverseState.tail.headOption)),
        permissionLevel.map(Permission.permissionIndicatorIfPublic(_, VDomModifier(fontSize := "0.7em", color.gray, marginLeft.auto, marginRight := "5px"))),
        channelModifier
      ),
    )
  }

  private def filterStringPredicate(filterString: String): Node => Boolean = {
    if (filterString.isEmpty) _ => true else _.str.toLowerCase.contains(filterString.toLowerCase)
  }

  private def channels(toplevelChannels: Rx[Seq[NodeId]], sidebarWithProjects: Var[Boolean], sidebarFilter: Var[String])(implicit ctx: Ctx.Owner): VNode = {

    def channelList(traverseState: TraverseState, userId: UserId, depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div({
        val children = Rx {
          val graph = GlobalState.rawGraph()
          val findChildren = if (sidebarWithProjects()) ChannelTreeData.childrenChannelsOrProjects _ else ChannelTreeData.childrenChannels _
          findChildren(graph, traverseState, userId, filterStringPredicate(sidebarFilter()))
        }
        val hasChildren = children.map(_.nonEmpty)
        val expanded = Rx {
          GlobalState.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        val channelListModifiers = VDomModifier(
          paddingLeft := "14px",
          fontSize := fontSizeByDepth(depth),
        )

        VDomModifier(
          channelLine(traverseState, userId, expanded = expanded, hasChildren = hasChildren, depth = depth, channelModifier = VDomModifier(flexGrow := 1, flexShrink := 0)),
          Rx {
            VDomModifier.ifTrue(hasChildren() && expanded())(div(
              channelListModifiers,
              children().map { child => channelList(traverseState.step(child), userId, depth = depth + 1) }
            ))
          }
        )
      })
    }

    val filterControls = div(
      Styles.flex,
      filterInput(sidebarFilter).apply(width := "100%"),
      toggleSidebarWithProjects(sidebarWithProjects).apply(Styles.flexStatic),
      marginBottom := "5px",
    )

    div(
      cls := "channels tiny-scrollbar",

      Rx {
        val userId = GlobalState.userId()

        VDomModifier(
          VDomModifier.ifTrue(toplevelChannels().nonEmpty || sidebarFilter().nonEmpty)(
            filterControls
          ),
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), userId)),
        )
      },
    )
  }

  private def toggleSidebarWithProjects(sidebarWithProjects: Var[Boolean])(implicit ctx: Ctx.Owner): VNode = button(
    cls := "ui mini compact basic button",
    div(
      cls := "fa-fw",
      sidebarWithProjects map {
        case false => VDomModifier(
          UI.popup("bottom center") := "Show non-bookmarked subprojects",
          freeSolid.faBookmark: VDomModifier
        )
        case true => VDomModifier(
          UI.popup("bottom center") := "Show only bookmarked subprojects",
          freeSolid.faBookOpen: VDomModifier
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
            channelLine(TraverseState(nodeId), userId, expanded = Var(false), hasChildren = Var(false),
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
              )).apply(
                paddingBottom := "1px",
                paddingTop := "1px"
              )
          }
        )
      }
    )
  }

  private def channelIcons(toplevelChannels: Rx[Seq[NodeId]], size: Int)(implicit ctx: Ctx.Owner): VDomModifier = {
    val indentFactor = 3
    val maxDepth = 6
    val defaultPadding = CommonStyles.channelIconDefaultPadding

    def renderChannel(traverseState: TraverseState, userId: UserId, depth: Int)(implicit ctx: Ctx.Owner) = {
      val nodeId = traverseState.parentId
      val selected = Rx { (GlobalState.page().parentId contains nodeId) && GlobalState.viewIsContent() }
      val node = Rx {
        GlobalState.rawGraph().nodesByIdOrThrow(nodeId)
      }

      val sanitizedDepth = depth.min(maxDepth)

      val channelIconModifiers = VDomModifier(
        href <-- nodeUrl(nodeId),
        onClick foreach {
          // needs to be before onChannelClick, because else GlobalState.page is already at the new page
          GlobalState.page.now.parentId match {
            case Some(parentId) if parentId == nodeId => // no switch happening...
            case _                                    => FeatureState.use(Feature.SwitchPageFromCollapsedLeftSidebar)
          }
        },
        onChannelClick(nodeId),
        drag(target = DragItem.Channel(nodeId, traverseState.tail.headOption)),
        cls := "node",

        // for each indent, steal padding on left and right
        // and reduce the width, so that the icon keeps its size
        width := s"${size - (sanitizedDepth * indentFactor)}px",
        padding := s"${defaultPadding}px ${defaultPadding - (sanitizedDepth * indentFactor / 2.0)}px"
      )

      channelIcon(node, selected, size).apply(
        UI.popup("right center") <-- node.map(_.str),
        channelIconModifiers
      )
    }

    def channelList(traverseState: TraverseState, userId: UserId, findChildren: (Graph, TraverseState) => Seq[NodeId], depth: Int = 0)(implicit ctx: Ctx.Owner): VNode = {
      div({
        val children = Rx {
          val graph = GlobalState.rawGraph()
          findChildren(graph, traverseState)
        }
        val expanded = Rx {
          GlobalState.rawGraph().isExpanded(userId, traverseState.parentId).getOrElse(true)
        }

        val channelListModifiers = VDomModifier(
          VDomModifier.ifTrue(depth < maxDepth)(paddingLeft := s"${indentFactor}px"),
          fontSize := fontSizeByDepth(depth),
        )

        VDomModifier(
          backgroundColor := "#666", // color for indentation space
          renderChannel(traverseState, userId, depth),
          Rx {
            VDomModifier.ifTrue(children().nonEmpty && expanded())(div(
              channelListModifiers,
              children().map { child => channelList(traverseState.step(child), userId, findChildren, depth = depth + 1) }
            ))
          }
        )
      })
    }

    div(
      cls := "channelIcons tiny-scrollbar",

      Rx {
        val userId = GlobalState.userId()

        VDomModifier(
          toplevelChannels().map(nodeId => channelList(TraverseState(nodeId), userId, ChannelTreeData.childrenChannels(_, _, userId, _ => true)))
        )
      }
    )
  }

  private def channelIcon(node: Rx[Node], isSelected: Rx[Boolean], size: Int)(implicit ctx: Ctx.Owner): VNode = {
    def iconText(str: String): String = {
      str match {
        case EmojiReplacer.emojiAtBeginningRegex(emoji) => emoji
        case _ => str.trim.take(2)
      }
    }

    a(
      cls := "channelicon",
      width := s"${size}px",
      height := s"${size}px",
      Rx {
        val n = node()
        VDomModifier(
          if (isSelected()) VDomModifier(
            backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(n.id)).toHex,
            color := "white"
          )
          else color := BaseColors.pageBg.copy(h = NodeColor.hue(n.id)).toHex,

          replaceEmoji(iconText(n.str))
        )
      },
    )
  }

  private def onChannelClick(nodeId: NodeId) = VDomModifier(
    onClickPreventDefaultExceptCtrl {
      GlobalState.focus(nodeId)
      ()
    }
  )
}
