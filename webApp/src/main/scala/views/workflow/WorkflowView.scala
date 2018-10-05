package wust.webApp.views.workflow

import fontAwesome._
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util._
import wust.util.collection._
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.{Placeholders}
import wust.webApp.views.ThreadView.{ChatKind}

import scala.collection.breakOut
import scala.scalajs.js

object WorkflowView {
  // -- display options --
  val grouping = false
  val lastActiveEditable : Var[Option[NodeId]] = Var(None)

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val stylesOuterDiv = Seq[VDomModifier](
      cls := "workflow",
      Styles.flex,
      flexDirection.column,
      height := "100%",
      alignItems.stretch,
      alignContent.stretch,
    )
    val stylesInnerDiv = Seq[VDomModifier](
      Styles.flex,
      flexDirection.row,
      height := "100%",
      position.relative,
    )
    div(
      stylesOuterDiv,
      activeEditableControls(state),
      div(
        stylesInnerDiv,
        chatHistory(state).apply(
          backgroundColor <-- state.pageStyle.map(_.bgLightColor),
        ),
      ),
      Rx { inputField(state, state.page().parentIdSet).apply(keyed, Styles.flexStatic, padding := "3px") },
      registerDraggableContainer(state),
    )
  }

  /// contains only simple html generating functions
  object components {
    val listWrapper = ul()
    val entryWrapper = li()

    def checkButton(nodeId : Set[NodeId], wrapper: VNode = entryWrapper) =
      wrapper("\u2713",
              cursor.pointer,
              onClick.stopPropagation --> sideEffect {println(s"checking node: ${nodeId}")})

    def indentButton(nodeId : Set[NodeId], wrapper: VNode = entryWrapper) =
      wrapper("\u2192",
              cursor.pointer,
              onClick.stopPropagation --> sideEffect {println(s"Indenting node: ${nodeId}")})

    def outdentButton(nodeId : Set[NodeId], wrapper: VNode = entryWrapper) =
      wrapper("\u2190",
              cursor.pointer,
              onClick.stopPropagation --> sideEffect {println(s"Outdenting node: ${nodeId}")})

    def deleteButton(state: GlobalState, nodeId: Set[NodeId], directParentIds: Set[NodeId],
                     wrapper: VNode = entryWrapper)
                    (implicit ctx: Ctx.Owner) =
      wrapper(
        span(cls := "fa-fw", freeRegular.faTrashAlt),
        onClick.stopPropagation --> sideEffect {
          println(s"deleting ${nodeId} with parents: ${directParentIds}")
          nodeId.foreach { nodeId =>
            state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
          }
          state.selectedNodeIds.update(_ -- nodeId)
        },
        cursor.pointer,
        )

  }

  val showDebugInfo = true

  /// Controls to e.g indent or outdent the last edited entry
  /** TODO: indent/outdent requires an order between entries. */
  def activeEditableControls(state: GlobalState)(implicit ctx: Ctx.Owner) = Rx {
    val nodes : Set[NodeId] = state.selectedNodeIds()
    (!nodes.isEmpty).ifTrueSeq[VDomModifier](
      Seq(components.listWrapper(
            cls := "activeEditableControls",
            showDebugInfo.ifTrue[VDomModifier](s"selected ${nodes.size}"),
            components.outdentButton(nodes),
            components.indentButton(nodes),
            components.deleteButton(state, nodes, state.page().parentIdSet)),
          )
    )
  }


  def chatHistory(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    val scrolledToBottom = PublishSubject[Boolean]
    val activeReplyFields = Var(Set.empty[List[NodeId]])

    div(
      cls := "workflow-wrapper",
      // this wrapping div is currently needed,
      // to allow dragging the scrollbar without triggering a drag event.
      // see https://github.com/Shopify/draggable/issues/262
      div(
        padding := "20px 0 20px 20px",
        Rx {
          val page = state.page()
          val fullGraph = state.graph()
          val graph = state.graphContent()
          val user = state.user()
          val nodes = graph.lookup.chronologicalNodesAscending.collect {
            case n: Node.Content if (fullGraph.isChildOfAny(n.id, page.parentIds)
                                       || fullGraph.isDeletedChildOfAny(n.id, page.parentIds)) =>
              n.id
          }
          if(nodes.isEmpty)
            VDomModifier(emptyChatNotice)
          else
            VDomModifier(
              groupNodes(graph, nodes, state, user.id)
                .map(kind => renderGroupedMessages(
                       state, kind.nodeIds, graph, page.parentIdSet, Nil, page.parentIdSet,
                       user.id, activeReplyFields)),
              draggableAs(state, DragItem.DisableDrag),
              cursor.default, // draggable sets cursor.move, but drag is disabled on page background
              dragTarget(DragItem.Chat.Page(page.parentIds)),
              keyed
            )
        },
        onUpdate --> sideEffect { (prev, _) =>
          scrolledToBottom
            .onNext(prev.scrollHeight - prev.clientHeight <= prev.scrollTop + 11) // at bottom + 10 px tolerance
        },
        onPostPatch.transform(_.withLatestFrom(scrolledToBottom) {
          case ((_, elem), atBottom) => (elem, atBottom)
        }) --> sideEffect { (elem, atBottom) =>
          if(atBottom) scrollToBottom(elem)
        }
      )
    )
  }

  private def emptyChatNotice: VNode =
    h3(textAlign.center, "Nothing here yet.", paddingTop := "40%", color := "rgba(0,0,0,0.5)")

  /** returns a Seq of ChatKind instances where similar successive nodes are grouped via ChatKind.Group */
  private def groupNodes(
    graph: Graph,
    nodes: Seq[NodeId],
    state: GlobalState,
    currentUserId: UserId
  ) = {
    def shouldGroup(nodes: NodeId*) = {
      grouping && // grouping enabled
        graph.authorIds(nodes.head).headOption.fold(false) { authorId =>
          nodes.forall(node => graph.authorIds(node).head == authorId)
        }
    }

    nodes.foldLeft(Seq[ChatKind]()) { (kinds, node) =>
      kinds.lastOption match {
        case Some(ChatKind.Single(lastNode)) =>
          if(shouldGroup(lastNode, node))
            kinds.dropRight(1) :+ ChatKind.Group(Seq(lastNode, node))
          else
            kinds :+ ChatKind.Single(node)

        case Some(ChatKind.Group(lastNodes)) =>
          if(shouldGroup(lastNodes.last, node))
            kinds.dropRight(1) :+ ChatKind.Group(nodeIds = lastNodes :+ node)
          else
            kinds :+ ChatKind.Single(node)

        case None => kinds :+ ChatKind.Single(node)
      }
    }
  }

  private def renderGroupedMessages(
    state: GlobalState,
    nodeIds: Seq[NodeId],
    graph: Graph,
    alreadyVisualizedParentIds: Set[NodeId],
    path: List[NodeId],
    directParentIds: Set[NodeId],
    currentUserId: UserId,
    activeReplyFields: Var[Set[List[NodeId]]]
  )(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val currNode = nodeIds.last
    val headNode = nodeIds.head
    val isMine = graph.authors(currNode).contains(currentUserId)

    div(
      cls := "chatmsg-group-outer-frame",
      // if the head-node is moved/removed, all reply-fields in this Group close. We didn't find a better key yet.
      keyed(headNode),
      div(
        keyed,
        cls := "chatmsg-group-inner-frame",
        nodeIds.map(nid =>
          renderThread(state, graph, alreadyVisualizedParentIds = alreadyVisualizedParentIds,
                       path = path, directParentIds = directParentIds, nid,
                       currentUserId, activeReplyFields)
        ),
      ),
    )
  }

  private def renderThread(state: GlobalState, graph: Graph, alreadyVisualizedParentIds: Set[NodeId],
                           path: List[NodeId], directParentIds: Set[NodeId], nodeId: NodeId,
                           currentUserId: UserId, activeReplyFields: Var[Set[List[NodeId]]])
                          (implicit ctx: Ctx.Owner): VNode = {
    val inCycle = alreadyVisualizedParentIds.contains(nodeId)
    val hasDisplayableChildren = (graph.hasChildren(nodeId) || graph.hasDeletedChildren(nodeId))
    if(!graph.isDeletedNow(nodeId, directParentIds)
         && hasDisplayableChildren
         && !inCycle) {
      val children = (graph.children(nodeId) ++ graph.deletedChildren(nodeId))
        .toSeq.sortBy(nid => graph.nodeCreated(nid): Long)
      div(
        keyed(nodeId),
        workflowEntry(state, graph, alreadyVisualizedParentIds,
                        directParentIds, nodeId)(ctx)(
          div(
            cls := "chat-thread",
            paddingLeft := s"10px",
            //borderLeft := s"3px solid ${ tagColor(nodeId).toHex }",

            groupNodes(graph, children, state, currentUserId)
              .map(kind => renderGroupedMessages(state, kind.nodeIds, graph, alreadyVisualizedParentIds + nodeId,
                                                 nodeId :: path, Set(nodeId), currentUserId,
                                                 activeReplyFields)),

            replyField(state, nodeId, directParentIds, path, activeReplyFields),

            draggableAs(state, DragItem.DisableDrag),
            dragTarget(DragItem.Chat.Thread(nodeId)),
            cursor.default, // draggable sets cursor.move, but drag is disabled on thread background
            keyed(nodeId)
          )),
      )
    }
    else
      workflowEntry(state, graph, alreadyVisualizedParentIds, directParentIds, nodeId,
                      messageCardInjected = inCycle.ifTrue[VDomModifier] {
                        VDomModifier(
                          Styles.flex,
                          alignItems.center,
                          freeSolid.faSyncAlt,
                          paddingRight := "3px",
                          backgroundColor := "#CCC",
                          color := "#666",
                          boxShadow := "0px 1px 0px 1px rgb(102, 102, 102, 0.45)",
                          )
                      })
  }

  def replyField(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId],
                 path: List[NodeId], activeReplyFields: Var[Set[List[NodeId]]])
                (implicit ctx: Ctx.Owner) = {
    val fullPath = nodeId :: path

    div(
      keyed(nodeId),
      Rx {
        val active = activeReplyFields() contains fullPath
        if(active)
          div(
            keyed(nodeId),
            Styles.flex,
            alignItems.center,
            inputField(state, directParentIds = Set(nodeId),
              blurAction = { value => if(value.isEmpty) activeReplyFields.update(_ - fullPath) }
            )(ctx)(
              keyed(nodeId),
              padding := "3px",
              width := "100%"
            ),
            div(
              keyed,
              freeSolid.faTimes,
              padding := "10px",
              Styles.flexStatic,
              cursor.pointer,
              onClick --> sideEffect { activeReplyFields.update(_ - fullPath) },
            )
          )
        else
          div(
            cls := "chat-replybutton",
            freeSolid.faReply,
            " reply",
            marginTop := "3px",
            marginLeft := "8px",
            // not onClick, because if another reply-field is already open, the click first triggers the blur-event of
            // the active field. If the field was empty it disappears, and shifts the reply-field away from the cursor
            // before the click was finished. This does not happen with onMouseDown.
            onMouseDown.stopPropagation --> sideEffect { activeReplyFields.update(_ + fullPath) }
          )
      }
    )
  }

  /// @return the actual body of a chat message
  /** Should be styled in such a way as to be repeatable so we can use this in groups */
  private def workflowEntry(state: GlobalState, graph: Graph, alreadyVisualizedParentIds: Set[NodeId],
                            directParentIds: Set[NodeId], nodeId: NodeId,
                            messageCardInjected: VDomModifier = VDomModifier.empty)
                           (implicit ctx: Ctx.Owner) = {
    val isDeleted = graph.isDeletedNow(nodeId, directParentIds)
    val isSelected = state.selectedNodeIds.map(_ contains nodeId)
    val node = graph.nodesById(nodeId)

    val editable = Var(false)

    val checkbox = div(
      cls := "ui checkbox fitted",
      isSelected.map(_.ifTrueOption(visibility.visible)),
      input(
        tpe := "checkbox",
        checked <-- isSelected,
        onChange.checked --> sideEffect { checked =>
          if(checked) state.selectedNodeIds.update(_ + nodeId)
          else state.selectedNodeIds.update(_ - nodeId)
        }
      ),
      label()
    )

    val msgControls = div(
      //cls := "chatmsg-controls",
      if(isDeleted) undeleteButton(state, nodeId, directParentIds)
      else VDomModifier(
        editButton(state, editable),
        components.deleteButton(state, Set(nodeId), directParentIds, wrapper=div())
      )
    )

    val messageCard = workflowEntryEditable(state, node, editable = editable,
                                            state.eventProcessor.changes,
                                            newTagParentIds = directParentIds)(ctx)(
      isDeleted.ifTrueOption(cls := "node-deleted"), // TODO: outwatch: switch classes on and off via Boolean or Rx[Boolean]
      cls := "drag-feedback",
      messageCardInjected,
      onDblClick.stopPropagation(state.viewConfig.now.copy(page = Page(node.id))) --> state.viewConfig,
    )


    li(
      isSelected.map(_.ifTrueOption(backgroundColor := "rgba(65,184,255, 0.5)")),
      div( // this nesting is needed to get a :hover effect on the selected background
        cls := "chatmsg-line",
        Styles.flex,
        onClick.stopPropagation(!editable.now) --> editable,
        onClick --> sideEffect { state.selectedNodeIds.update(_.toggle(nodeId)) },

        messageCard,
        Rx { (state.screenSize() != ScreenSize.Small).ifTrue[VDomModifier](msgControls(Styles.flexStatic)) }
      )
    )
  }

  private def editButton(state: GlobalState, editable: Var[Boolean])(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faEdit),
      onClick.stopPropagation(!editable.now) --> editable,
      cursor.pointer,
    )

  private def undeleteButton(state: GlobalState, nodeId: NodeId, directParentIds: Set[NodeId])
                            (implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", fontawesome.layered(
        fontawesome.icon(freeRegular.faTrashAlt),
        fontawesome.icon(freeSolid.faMinus, new Params {
          transform = new Transform {
            rotate = 45.0
          }

        })
      )),
      onClick.stopPropagation(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
      cursor.pointer,
    )

  private def zoomButton(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner) =
    div(
      div(cls := "fa-fw", freeRegular.faArrowAltCircleRight),
      onClick.stopPropagation(state.viewConfig.now.copy(page = Page(nodeId))) --> state.viewConfig,
      cursor.pointer,
    )

  private def inputField(state: GlobalState, directParentIds: Set[NodeId], blurAction: String => Unit = _ => ())
                        (implicit ctx: Ctx.Owner): VNode = {
    val disableUserInput = Rx {
      val graphNotLoaded = (state.graph().lookup.nodeIdSet intersect state.page().parentIdSet).isEmpty
      graphNotLoaded
    }

    val initialValue = Rx {
      state.viewConfig().shareOptions.map { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }

    div(
      cls := "ui form",
      keyed(directParentIds),
      textArea(
        keyed,
        cls := "field",
        valueWithEnterWithInitial(initialValue.toObservable.collect { case Some(s) => s }) handleWith { str =>
          val graph = state.graphContent.now
          val selectedNodeIds = state.selectedNodeIds.now
          val changes = {
            val newNode = Node.Content.empty
            GraphChanges.addNodeWithParent(Node.Content(NodeData.Markdown(str)), directParentIds)
          }

          state.eventProcessor.changes.onNext(changes)
          //submittedNewMessage.onNext(Unit)
        },
        onInsert.asHtml --> sideEffect { e => e.focus() },
        onBlur.value --> sideEffect { value => blurAction(value) },
        disabled <-- disableUserInput,
        rows := 1, //TODO: auto expand textarea: https://codepen.io/vsync/pen/frudD
        style("resize") := "none", //TODO: add resize style to scala-dom-types
        Placeholders.newNode
      )
    )
  }
}
