package wust.webApp.views

import cats.effect.IO

import collection.breakOut
import fontAwesome._
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.{BaseColors, NodeColor}
import wust.util._
import wust.util.collection._
import wust.webApp.Icons
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.jsdom.dateFns
import wust.webApp.BrowserDetect
import wust.sdk.NodeColor._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.window
import wust.webApp.dragdrop.DragItem

import scala.collection.mutable
import scala.scalajs.js

object SharedViewElements {

  trait SelectedNodeBase {
    def nodeId: NodeId
    def directParentIds: Iterable[NodeId]
  }

  final case class ScrollHandler(scrollableHistoryElem: Var[Option[HTMLElement]], isScrolledToBottom: Var[Boolean]) {

    val scrollToBottomInAnimationFrame = requestSingleAnimationFrame {
      scrollableHistoryElem.now.foreach { elem =>
        scrollToBottom(elem)
      }
    }

    def isScrolledToBottomNow = scrollableHistoryElem.now.fold(true){ elem =>
      elem.scrollHeight - elem.clientHeight <= elem.scrollTop + 11
    } // at bottom + 10 px tolerance

    def scrollOptions(state: GlobalState)(implicit ctx: Ctx.Owner) = VDomModifier(
      onDomPreUpdate foreach {
        isScrolledToBottom() = isScrolledToBottomNow
      },
      onDomUpdate foreach {
        if (isScrolledToBottom.now) scrollToBottomInAnimationFrame()
      },
      onDomMount.asHtml foreach { elem =>
        scrollableHistoryElem() = Some(elem)
        scrollToBottomInAnimationFrame()
      },
      managed(
        IO {
          // on page change, always scroll down
          state.page.foreach { _ =>
            isScrolledToBottom() = true
            scrollToBottomInAnimationFrame()
          }
        },
      ),
    )

  }

  @inline def sortByCreated(nodes: js.Array[Int], graph: Graph): Unit = {
    nodes.sort { (a, b) =>
      val createdA = graph.lookup.nodeCreated(a)
      val createdB = graph.lookup.nodeCreated(b)
      val result = createdA.compare(createdB)
      if(result == 0) graph.lookup.nodeIds(a) compare graph.lookup.nodeIds(b)
      else result
    }
  }

  def inputField(state: GlobalState, parentIds: => Iterable[NodeId], scrollHandler:ScrollHandler)(implicit ctx: Ctx.Owner): VNode = {
    val initialValue = Rx {
      state.viewConfig().shareOptions.map { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }.toObservable.collect { case Some(s) => s }

    var currentTextArea: dom.html.TextArea = null
    def handleInput(str: String): Unit = if (str.nonEmpty) {
      val changes = GraphChanges.addNodeWithParent(Node.Content(NodeData.Markdown(str)), parentIds)

      state.eventProcessor.changes.onNext(changes)
      if(BrowserDetect.isMobile) currentTextArea.focus() // re-gain focus on mobile. Focus gets lost and closes the on-screen keyboard after pressing the button.
    }

    if(!BrowserDetect.isMobile) {
      state.page.triggerLater {
        if(currentTextArea != null) currentTextArea.focus() // re-gain focus on page-change
      }
    }

    div(
      Styles.flex,
      alignItems.center,
      justifyContent.stretch,
      div(
        margin := "3px",
        BrowserDetect.isMobile.ifTrue[VDomModifier](marginRight := "0"),
        width := "100%",
        cls := "ui form",
        textArea(
          cls := "field",
          if (BrowserDetect.isMobile) {
            value <-- initialValue
          } else {
            valueWithEnterWithInitial(initialValue) foreach handleInput _
          },
          rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
          resize := "none",
          placeholder := (if(BrowserDetect.isMobile) "Write a message" else "Write a message and press Enter to submit."),
          onDomMount foreach { e => currentTextArea = e.asInstanceOf[dom.html.TextArea] },
          BrowserDetect.isMobile.ifFalse(onDomMount.asHtml --> inNextAnimationFrame(_.focus())), // immediately focus
          BrowserDetect.isMobile.ifTrue(onFocus foreach {
            // when mobile keyboard opens, it may scroll up.
            // so we scroll down again.
            if(scrollHandler.isScrolledToBottomNow) {
              window.setTimeout(() => scrollHandler.scrollToBottomInAnimationFrame(), 500)
              // again for slower phones...
              window.setTimeout(() => scrollHandler.scrollToBottomInAnimationFrame(), 2000)
              ()
            }
          }),
          BrowserDetect.isMobile.ifTrue(eventProp("touchstart") foreach {
          // if field is already focused, but keyboard is closed:
          // we do not know if the keyboard is opened right now,
          // but we can detect if it was opened: by screen-height changes
          if(scrollHandler.isScrolledToBottomNow) {
            val screenHeight = window.screen.availHeight
            window.setTimeout({() =>
              val keyboardWasOpened = screenHeight > window.screen.availHeight
              if(keyboardWasOpened) scrollHandler.scrollToBottomInAnimationFrame()
            }, 500)
            // and again for slower phones...
            window.setTimeout({() =>
              val keyboardWasOpened = screenHeight > window.screen.availHeight
              if(keyboardWasOpened) scrollHandler.scrollToBottomInAnimationFrame()
            }, 2000)
            ()
          }
        })
        )
      ),
      BrowserDetect.isMobile.ifTrue[VDomModifier](
        div( // clickable box around circular button
          padding := "3px",
          button(
            margin := "0px",
            Styles.flexStatic,
            cls := "ui circular icon button",
            freeRegular.faPaperPlane,
            fontSize := "1.1rem",
            backgroundColor := "steelblue",
            color := "white",
          ),
          onClick foreach {
            val str = currentTextArea.value
            handleInput(str)
            currentTextArea.value = ""
          },
        )
      )
    )
  }

  val dragHandle = div(
    cls := "draghandle",
    paddingLeft := "8px",
    freeSolid.faGripVertical,
    paddingRight := "8px",
    color := "#b3bfca",
    alignSelf.center,
    marginLeft.auto,
    onMouseDown.stopPropagation foreach {},
  )

  val replyButton: VNode = {
    div(
      div(cls := "fa-fw", freeSolid.faReply),
      cursor.pointer,
    )
  }

  val editButton: VNode =
    div(
      div(cls := "fa-fw", Icons.edit),
      cursor.pointer,
    )

  val deleteButton: VNode =
    div(
      div(cls := "fa-fw", Icons.delete),
      cursor.pointer,
    )

  val undeleteButton: VNode =
    div(
      div(cls := "fa-fw", Icons.undelete),
      cursor.pointer,
    )

  val zoomButton: VNode =
    div(
      div(cls := "fa-fw", Icons.zoom),
      cursor.pointer
    )

  def authorAvatar(author: Node.User, avatarSize: Int, avatarPadding: Int): VNode = {
    div(Avatar(author)(cls := "avatar",width := s"${avatarSize}px", padding := s"${avatarPadding}px"), marginRight := "5px")
  }

  @inline def smallAuthorAvatar(author: Node.User): VNode = {
    authorAvatar(author, avatarSize = 15, avatarPadding = 2)
  }

  @inline def bigAuthorAvatar(author: Node.User): VNode = {
    authorAvatar(author, avatarSize = 40, avatarPadding = 3)
  }

  def authorName(author: Node.User): VNode = {
    div(if(author.data.isImplicit) Rendered.implicitUserName else author.name, cls := "chatmsg-author")
  }

  def creationDate(created: EpochMilli): VDomModifier = {
    (created != EpochMilli.min).ifTrue[VDomModifier](
      div(
        dateFns.formatDistance(new js.Date(created), new js.Date), " ago",
        cls := "chatmsg-date"
      )
    )
  }

  def renderMessage(state: GlobalState, nodeId: NodeId, isDeleted: Rx[Boolean], editMode: Var[Boolean], renderedMessageModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): Rx[Option[VNode]] = {

    val node = Rx {
      // we need to get the latest node content from the graph
      val graph = state.graph()
      graph.lookup.nodesByIdGet(nodeId)
    }

    val isSynced: Rx[Boolean] = {
      // when a node is not in transit, avoid rx subscription
      val nodeInTransit = state.addNodesInTransit.now contains nodeId
      if(nodeInTransit) state.addNodesInTransit.map(nodeIds => !nodeIds(nodeId))
      else Rx(true)
    }

    val syncedIcon = Rx {
      val icon: VNode = if(isSynced()) freeSolid.faCheck else freeRegular.faClock
      icon(width := "10px", marginBottom := "5px", marginRight := "5px", color := "#9c9c9c")
    }

    def render(node: Node)(implicit ctx: Ctx.Owner) =
      nodeCardEditable(state, node, editMode = editMode, state.eventProcessor.changes).apply(
        Styles.flex,
        alignItems.flexEnd,
        Rx{
          // TODO: outwatch: easily switch classes on and off via Boolean or Rx[Boolean]
          isDeleted().ifTrue[VDomModifier](cls := "node-deleted")
        },
        cls := "drag-feedback",

        syncedIcon,
        dragHandle,
        renderedMessageModifier
      )

    Rx {
      node().map(render)
    }
  }

  def chatMessageHeader(author: Option[Node.User], creationEpochMillis: EpochMilli, avatar: VDomModifier) = div(
    cls := "chatmsg-header",
    Styles.flex,
    avatar,
    author.map(authorName),
    creationDate(creationEpochMillis)
  )

  def messageRowDragOptions[T <: SelectedNodeBase](nodeId: NodeId, selectedNodes: Var[Set[T]], editMode: Var[Boolean])(implicit ctx: Ctx.Owner) = VDomModifier(
    // The whole line is draggable, so that it can also be a drag-target.
    // This is currently a limit in the draggable library
    editMode.map { editMode =>
      if(editMode)
        draggableAs(DragItem.DisableDrag) // prevents dragging when selecting text
      else {
        def payload = {
          val selection = selectedNodes.now
          if(selection exists (_.nodeId == nodeId))
            DragItem.Chat.Messages(selection.map(_.nodeId)(breakOut))
          else
            DragItem.Chat.Message(nodeId)
        }
        // payload is call by name, so it's always the current selectedNodeIds
        draggableAs(payload)
      }
    },
    dragTarget(DragItem.Chat.Message(nodeId)),
    cursor.auto, // else draggableAs sets class .draggable, which sets cursor.move
  )

  def msgCheckbox[T <: SelectedNodeBase](state:GlobalState, nodeId:NodeId, selectedNodes:Var[Set[T]], newSelectedNode: NodeId => T, isSelected:Rx[Boolean])(implicit ctx: Ctx.Owner) =
    (state.screenSize.now == ScreenSize.Small).ifFalse[VDomModifier] {
      div(
        cls := "ui checkbox fitted",
        marginLeft := "5px",
        marginRight := "3px",
        isSelected.map(_.ifTrueOption(visibility.visible)),
        input(
          tpe := "checkbox",
          checked <-- isSelected,
          onChange.checked foreach { checked =>
            if(checked) selectedNodes.update(_ + newSelectedNode(nodeId))
            else selectedNodes.update(_.filterNot(_.nodeId == nodeId))
          }
        ),
        label()
      )
    }

  def msgControls[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodes: Var[Set[T]], isDeleted:Rx[Boolean], editMode: Var[Boolean], replyAction: => Unit)(implicit ctx: Ctx.Owner) =
      div(
        Styles.flexStatic,
        cls := "chatmsg-controls",
        BrowserDetect.isMobile.ifFalse[VDomModifier] {
          Rx {
            if(isDeleted()) {
              undeleteButton(
                onClick(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
              )
            }
            else VDomModifier(
              replyButton(
                onClick foreach { replyAction }
              ),
              editButton(
                onClick.mapTo(!editMode.now) --> editMode
              ),
              deleteButton(
                onClick foreach {
                  state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
                  selectedNodes.update(_.filterNot(_.nodeId == nodeId))
                },
              ),
              zoomButton(
                onClick.mapTo(state.viewConfig.now.copy(page = Page(nodeId))) --> state.viewConfig,
              )
            )
          }
        }
      )

  def messageTags(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId])(implicit ctx: Ctx.Owner): Rx[VNode] = {
    val directNodeTags:Rx[Seq[Node]] = Rx {
      val graph = state.graph()
      graph.lookup.directNodeTags(graph.lookup.idToIdx(nodeId), graph.lookup.createBitSet(directParentIds))
    }

    Rx {
      state.screenSize.now match {
        case ScreenSize.Small =>
          div(
            cls := "tags",
            directNodeTags().map { tag =>
              nodeTagDot(state, tag)(Styles.flexStatic)
            },
          )
        case _                =>
          div(
            cls := "tags",
            directNodeTags().map { tag =>
              removableNodeTag(state, tag, nodeId)(Styles.flexStatic)
            },
          )
      }
    }
  }

  def calculateMessageGrouping(messages: js.Array[Int], graph: Graph): Array[Array[Int]] = {
    if(messages.isEmpty) return Array[Array[Int]]()

    val groupsBuilder = mutable.ArrayBuilder.make[Array[Int]]
    val currentGroupBuilder = new mutable.ArrayBuilder.ofInt
    var lastAuthor: Int = -1
    messages.foreach { message =>
      val author: Int = graph.lookup.authorsIdx(message, 0)

      if(author != lastAuthor && lastAuthor != -1) {
        groupsBuilder += currentGroupBuilder.result()
        currentGroupBuilder.clear()
      }

      currentGroupBuilder += message
      lastAuthor = author
    }
    groupsBuilder += currentGroupBuilder.result()
    groupsBuilder.result()
  }

  def selectedNodeActions[T <: SelectedNodeBase](state: GlobalState, selectedNodes: Var[Set[T]])(implicit ctx: Ctx.Owner): List[T] => List[VNode] = selected => {
    val nodeIdSet:List[NodeId] = selected.map(_.nodeId)(breakOut)
    List(
      zoomButton(onClick foreach {
        state.viewConfig.onNext(state.viewConfig.now.copy(page = Page(nodeIdSet)))
        selectedNodes() = Set.empty[T]
      }),
      SelectedNodes.deleteAllButton[T](state, selected, selectedNodes, _.nodeId, _.directParentIds),
    )
  }
}
