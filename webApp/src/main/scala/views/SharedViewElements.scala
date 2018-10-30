package wust.webApp.views

import cats.effect.IO
import fontAwesome._
import monix.reactive.Observable
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._

import scala.collection.{breakOut, mutable}
import scala.scalajs.js

object SharedViewElements {

  @inline def noiseFutureDeleteDate = EpochMilli(EpochMilli.now + EpochMilli.week)

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
      val createdA = graph.nodeCreated(a)
      val createdB = graph.nodeCreated(b)
      val result = createdA.compare(createdB)
      if(result == 0) graph.nodeIds(a) compare graph.nodeIds(b)
      else result
    }
  }

  def inputField(state: GlobalState, parentIds: => Iterable[NodeId], scrollHandler:ScrollHandler, triggerFocus:Observable[Unit] = Observable.empty)(implicit ctx: Ctx.Owner): VNode = {
    val initialValue = Rx {
      state.viewConfig().shareOptions.map { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }.toObservable.collect { case Some(s) => s }

    val isDisabled = Rx {
      val graph = state.graph()
      !parentIds.forall(graph.contains)
    }

    var currentTextArea: dom.html.TextArea = null
    def handleInput(str: String): Unit = if (str.nonEmpty) {
      // we treat new chat messages as noise per default, so we set a future deletion date
      val changes = GraphChanges.addNodeWithDeletedParent(Node.MarkdownMessage(str), parentIds, deletedAt = noiseFutureDeleteDate)

      state.eventProcessor.changes.onNext(changes)
      if(BrowserDetect.isMobile) currentTextArea.focus() // re-gain focus on mobile. Focus gets lost and closes the on-screen keyboard after pressing the button.
    }

    if(!BrowserDetect.isMobile) {
      state.page.triggerLater {
        if(currentTextArea != null) currentTextArea.focus() // re-gain focus on page-change
      }
    }



    div(
      managed(IO(triggerFocus.foreach { _ => currentTextArea.focus()})),
      Styles.flex,
      alignItems.center,
      justifyContent.stretch,
      div(
        margin := "3px",
        BrowserDetect.isMobile.ifTrue[VDomModifier](marginRight := "0"),
        width := "100%",
        cls := "ui form",
        textArea(
          disabled <-- isDisabled,
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

  val activeStarButton: VNode = {
    div(
      div(cls := "fa-fw", freeSolid.faStar, color := "#fbbd08"),
      cursor.pointer,
    )
  }

  val inactiveStarButton: VNode = {
    div(
      div(cls := "fa-fw", freeRegular.faStar, color := "#fbbd08"),
      cursor.pointer,
    )
  }

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

  val taskButton: VNode =
    div(
      div(cls := "fa-fw", Icons.task),
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
    div(Components.displayUserName(author.data), cls := "chatmsg-author")
  }

  def creationDate(created: EpochMilli): VDomModifier = {
    (created != EpochMilli.min).ifTrue[VDomModifier](
      div(
        dateFns.formatDistance(new js.Date(created), new js.Date), " ago",
        cls := "chatmsg-date"
      )
    )
  }

  def renderMessage(state: GlobalState, nodeId: NodeId, isDeletedNow: Rx[Boolean], isDeletedInFuture: Rx[Boolean], editMode: Var[Boolean], renderedMessageModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): Rx[Option[VNode]] = {

    val node = Rx {
      // we need to get the latest node content from the graph
      val graph = state.graph()
      graph.nodesByIdGet(nodeId)
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


    def render(node: Node)(implicit ctx: Ctx.Owner) = {
      val importanceIndicator = Rx {
        val unimportant = editMode() || isDeletedNow() || isDeletedInFuture()
        unimportant.ifFalse[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px #fbbd08"))
      }
      nodeCardEditable(state, node, editMode = editMode, state.eventProcessor.changes).apply(
        Styles.flex,
        alignItems.flexEnd,
        Rx { isDeletedNow().ifTrue[VDomModifier](cls := "node-deleted") },
        importanceIndicator,
        cls := "drag-feedback",

        syncedIcon,
        dragHandle,
        renderedMessageModifier
      )
    }

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

  def msgCheckbox[T <: SelectedNodeBase](state:GlobalState, nodeId:NodeId, selectedNodes:Var[Set[T]], newSelectedNode: NodeId => T, isSelected:Rx[Boolean])(implicit ctx: Ctx.Owner): VDomModifier =
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

  def msgControls[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodes: Var[Set[T]], isDeletedNow:Rx[Boolean], isDeletedInFuture:Rx[Boolean], editMode: Var[Boolean], replyAction: => Unit)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flexStatic,
      cls := "chatmsg-controls",
      BrowserDetect.isMobile.ifFalse[VDomModifier] {
        Rx {
          if(isDeletedNow()) {
            undeleteButton(
              onClick(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
            )
          }
          else VDomModifier(
            if(isDeletedInFuture()) {
              inactiveStarButton(
                onClick(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
              )
            } else {
              activeStarButton(
                onClick foreach {
                  state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds, noiseFutureDeleteDate))
                  ()
                }
              )
            },
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
            ),
            taskButton(
              onClick foreach {
                val change = GraphChanges.addNode(state.graph.now.nodesById(nodeId).asInstanceOf[Node.Content].copy(role = NodeRole.Task))
                state.eventProcessor.changes.onNext(change)
                ()
              }
            )
          )
        }
      }
    )
  }

  def messageTags(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId])(implicit ctx: Ctx.Owner): Rx[VNode] = {
    val directNodeTags:Rx[Seq[Node]] = Rx {
      val graph = state.graph()
      graph.directNodeTags(graph.idToIdx(nodeId), graph.createBitSet(directParentIds))
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
      val author: Int = graph.authorsIdx(message, 0)

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

  def starActionButton[T <: SelectedNodeBase](state: GlobalState, selected: List[T], selectedNodes: Var[Set[T]], anySelectedNodeIsDeleted: Rx[Boolean], anySelectedNodeIsDeletedInFuture: Rx[Boolean])(implicit ctx:Ctx.Owner): BasicVNode = {
    div(
      Rx {
        if(anySelectedNodeIsDeleted() || anySelectedNodeIsDeletedInFuture())
          VDomModifier(
            div(cls := "fa-fw", freeRegular.faStar, color := "#fbbd08"),
            onClick foreach {
              val changes = selected.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.undelete(t.nodeId, t.directParentIds))
              state.eventProcessor.changes.onNext(changes)
              selectedNodes() = Set.empty[T]
            }
          )
        else
          VDomModifier(
            div(cls := "fa-fw", freeSolid.faStar, color := "#fbbd08"),
            onClick foreach {
              val changes = selected.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.delete(t.nodeId, t.directParentIds, noiseFutureDeleteDate))
              state.eventProcessor.changes.onNext(changes)
              selectedNodes() = Set.empty[T]
            }
          )
      },
      cursor.pointer,
    )
  }

  def selectedNodeActions[T <: SelectedNodeBase](state: GlobalState, selectedNodes: Var[Set[T]], additional:List[VNode] = Nil)(implicit ctx: Ctx.Owner): List[T] => List[VNode] = selected => {
    val nodeIdSet:List[NodeId] = selected.map(_.nodeId)(breakOut)
    val allSelectedNodesAreDeleted = Rx {
      val graph = state.graph()
      selected.forall(t => graph.isDeletedNow(t.nodeId, t.directParentIds))
    }

    val anySelectedNodeIsDeleted = Rx {
      val graph = state.graph()
      selected.forall(t => graph.isDeletedNow(t.nodeId, t.directParentIds))
    }

    val anySelectedNodeIsDeletedInFuture = Rx {
      val graph = state.graph()
      selected.exists(t => graph.isDeletedInFuture(t.nodeId, t.directParentIds))
    }

    List(
      starActionButton(state, selected, selectedNodes, anySelectedNodeIsDeleted = anySelectedNodeIsDeleted, anySelectedNodeIsDeletedInFuture = anySelectedNodeIsDeletedInFuture),
      taskButton(
        onClick foreach {
          val change = GraphChanges(addNodes = nodeIdSet.map(nodeId => state.graph.now.nodesById(nodeId).asInstanceOf[Node.Content].copy(role = NodeRole.Task))(breakOut))
          state.eventProcessor.changes.onNext(change)
          ()
        }
      ),
      zoomButton(onClick foreach {
        state.viewConfig.onNext(state.viewConfig.now.copy(page = Page(nodeIdSet)))
        selectedNodes() = Set.empty[T]
      }),
      SelectedNodes.deleteAllButton[T](state, selected, selectedNodes, allSelectedNodesAreDeleted),
    ) ::: additional
  }
}
