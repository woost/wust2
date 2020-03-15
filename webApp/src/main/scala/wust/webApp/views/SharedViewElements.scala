package wust.webApp.views

import fontAwesome._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl.{label, _}
import outwatch.dom.helpers.EmitterBuilder
import outwatch.ext.monix._
import outwatch.reactive._
import outwatch.reactive.handler._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem.DisableDrag
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.parsers.UrlConfigWriter
import wust.webApp.state.GlobalState.SelectedNode
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, dragWithHandle, registerDragContainer}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil._

import scala.collection.breakOut
import scala.scalajs.js

object SharedViewElements {

  def sortByCreated(nodes: js.Array[Int], graph: Graph): Unit = {
    nodes.sort { (a, b) =>
      val createdA = graph.nodeCreated(a)
      val createdB = graph.nodeCreated(b)
      val result = createdA.compare(createdB)
      if(result == 0) graph.nodeIds(b) compare graph.nodeIds(a) // deterministic tie break
      else result
    }
  }

  def sortByDeepCreated(nodes: js.Array[Int], graph: Graph): Unit = {
    nodes.sort { (a, b) =>
      val createdA = graph.nodeDeepCreated(a)
      val createdB = graph.nodeDeepCreated(b)
      val result = createdA.compare(createdB)
      if(result == 0) graph.nodeIds(b) compare graph.nodeIds(a) // deterministic tie break
      else result
    }
  }

  def sortByDeepCreated[T](nodes: js.Array[T], index:T => Int, graph: Graph): Unit = {
    nodes.sort { (aRaw, bRaw) =>
      val a = index(aRaw)
      val b = index(bRaw)
      val createdA = graph.nodeDeepCreated(a)
      val createdB = graph.nodeDeepCreated(b)
      val result = createdA.compare(createdB)
      if(result == 0) graph.nodeIds(b) compare graph.nodeIds(a) // deterministic tie break
      else result
    }
  }

  val replyButton: VNode = {
    div(
      div(cls := "fa-fw", UI.tooltip := "Reply", freeSolid.faReply),
      cursor.pointer,
    )
  }

  val deleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.tooltip := "Archive", Icons.delete),
      cursor.pointer,
    )

  val undeleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.tooltip := "Restore", Icons.undelete),
      cursor.pointer,
    )

  def chatMessageHeader( author: Option[Node.User]): VNode = div(
    cls := "chatmsg-header",
    author.map(author => VDomModifier(smallAuthorAvatar(author), authorName( author))),
  )

  //  def chatMessageHeader( author: Option[Node.User], creationEpochMillis: EpochMilli, modificationData: IndexedSeq[(Node.User, EpochMilli)], avatar: VDomModifier) = div(
  def chatMessageHeader( author: Option[Node.User], creationEpochMillis: EpochMilli, nodeId: NodeId, avatar: VDomModifier)(implicit ctx: Ctx.Owner): VNode = div(
    cls := "chatmsg-header",
    avatar,
    author.map { author =>
      VDomModifier(
        authorName( author),
        creationDate(creationEpochMillis),
        GlobalState.graph.map { graph =>
          graph.idToIdx(nodeId).map { nodeIdx =>
            modificationDetails(author, graph.nodeModifier(nodeIdx))
          }
        },
      )
    },
  )


  def authorAvatar(author: Node.User, avatarSize: String, avatarPadding: Int): VNode = {
    div(Avatar.user(author, size = avatarSize)(padding := s"${avatarPadding}px"), marginRight := "5px")
  }

  @inline def smallAuthorAvatar(author: Node.User): VNode = {
    authorAvatar(author, avatarSize = "15px", avatarPadding = 2)
  }

  @inline def bigAuthorAvatar(author: Node.User): VNode = {
    authorAvatar(author, avatarSize = "40px", avatarPadding = 3)
  }

  def authorName(author: Node.User): VNode = {
    div(
      cls := "chatmsg-author",
      Components.displayUserName(author.data),
    )
  }

  def modificationDetails(author: Node.User, modificationData: IndexedSeq[(Node.User, EpochMilli)]): VDomModifier = {

    @inline def modificationItem(user: Node.User, time: EpochMilli)  = li(s"${user.name} at ${dateString(time)}")

    @inline def modificationsHtml = ul(
      fontSize.xSmall,
      margin := "0",
      padding := "0 0 0 5px",
      modificationData.map(item => modificationItem(item._1, item._2))
    )

    modificationData.nonEmpty.ifTrue[VDomModifier]{
      val lastModification = modificationData.last
      div(
        cls := "chatmsg-date",
        Styles.flexStatic,
        s"edited${if(author.id != lastModification._1.id) s" by ${lastModification._1.name}" else ""}",
        UI.tooltipHtml := modificationsHtml,
      )
    }
  }

  def renderMessage(
    focusState: FocusState,
    traverseState: TraverseState,
    nodeId: NodeId,
    directParentIds:Iterable[NodeId],
    isDeletedNow: Rx[Boolean],
    renderedMessageModifier:VDomModifier = VDomModifier.empty,
  )(implicit ctx: Ctx.Owner): Rx[Option[VDomModifier]] = {

    val node = Rx {
      // we need to get the latest node content from the graph
      val graph = GlobalState.graph()
      graph.nodesById(nodeId) //TODO: why option? shouldn't the node always exist?
    }

    val isSynced: Rx[Boolean] = {
      // when a node is not in transit, avoid rx subscription
      val nodeInTransit = GlobalState.addNodesInTransit.now contains nodeId
      if(nodeInTransit) GlobalState.addNodesInTransit.map(nodeIds => !nodeIds(nodeId))
      else Rx(true)
    }

    val syncedIcon = Rx {
      val icon: VNode = if(isSynced()) freeSolid.faCheck else freeRegular.faClock
      icon(width := "10px", marginBottom := "5px", marginRight := "5px", color := "#9c9c9c")
    }


    def render(node: Node, focusState:FocusState, isDeletedNow: Boolean)(implicit ctx: Ctx.Owner) = {
      if(isDeletedNow)
        nodeCardWithoutRender(node).apply(cls := "node-deleted")
      else {
        node.role match {
          case NodeRole.Task =>
            nodeCardWithCheckbox( node, directParentIds).apply(
              Styles.flex,
              alignItems.flexStart,
              renderedMessageModifier,
            )
          case _ =>
            nodeCard( node,
              contentInject = div(
                alignItems.center,
                NodeDetails.tagsPropertiesAssignments(focusState, traverseState, nodeId)
              )
            ).apply(
              Styles.flex,
              alignItems.flexEnd, // keeps syncIcon at bottom

              // Sadly it is not possible to FLOAT the syncedicon to the bottom right:
              // https://stackoverflow.com/questions/499829/how-can-i-wrap-text-around-a-bottom-right-div/499883#499883
              syncedIcon,
              renderedMessageModifier,
            )
        }
      }
    }

    Rx {
      node().map { node =>
        render(node, focusState, isDeletedNow()).apply(
          cursor.pointer,
          VDomModifier.ifTrue(GlobalState.selectedNodes().isEmpty)(Components.sidebarNodeFocusMod(node.id, focusState)),
          Components.showHoveredNode( node.id),
          UnreadComponents.readObserver( node.id)
        )
      }
    }
  }

  def messageDragOptions(nodeId: NodeId)(implicit ctx: Ctx.Owner) = VDomModifier(
    Rx {
      val graph = GlobalState.graph()
      graph.idToIdx(nodeId).map { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        val selection = GlobalState.selectedNodes()
        // payload is call by name, so it's always the current selectedNodeIds
        def payloadOverride:Option[() => DragPayload] = selection.find(_.nodeId == nodeId).map(_ => () => DragItem.SelectedNodes(selection.map(_.nodeId)(breakOut)))
        VDomModifier(
          nodeDragOptions(nodeId, node.role, withHandle = false, payloadOverride = payloadOverride),
          DragComponents.onAfterPayloadWasDragged.foreach{ GlobalState.clearSelectedNodes() }
        )
      }
    },
  )

  def nodeDragOptions(nodeId:NodeId, role:NodeRole, withHandle:Boolean = false, payloadOverride:Option[() => DragPayload] = None): VDomModifier = {
    val dragItem = role match {
      case NodeRole.Message => DragItem.Message(nodeId)
      case NodeRole.Task => DragItem.Task(nodeId)
      case _ => DragItem.DisableDrag
    }
    val payload:() => DragPayload = payloadOverride.getOrElse(() => dragItem)
    val target = dragItem match {
      case target:DragTarget => target
      case _ => DisableDrag // e.g. DisableDrag is not a DragTarget
    }

    if(withHandle) dragWithHandle(payload = payload(), target = target)
    else drag(payload = payload(), target = target)
  }

  def msgCheckbox( nodeId:NodeId, newSelectedNode: NodeId => SelectedNode, isSelected:Rx[Boolean])(implicit ctx: Ctx.Owner): VDomModifier =
    BrowserDetect.isMobile.ifFalse[VDomModifier] {
      div(
        cls := "ui nodeselection-checkbox checkbox fitted",
        marginLeft := "5px",
        marginRight := "3px",
        isSelected.map(_.ifTrueOption(visibility.visible)),
        input(
          tpe := "checkbox",
          checked <-- isSelected,
          onChange.checked foreach { checked =>
            if(checked) GlobalState.addSelectedNode(newSelectedNode(nodeId))
            else GlobalState.removeSelectedNode(nodeId)
          },
          onClick.stopPropagation.discard, // fix safari event, that automatically clicks on the message row, which again would unselect the message
          onMouseDown.stopPropagation.discard, // prevent rightsidebar from closing
        ),
        label()
      )
    }

  def msgControls(nodeId: NodeId, directParentIds: Iterable[ParentId], isDeletedNow:Rx[Boolean], replyAction: => Unit)(implicit ctx: Ctx.Owner): VDomModifier = {

    val canWrite = NodePermission.canWrite( nodeId)

    BrowserDetect.isMobile.ifFalse[VDomModifier] {
      Rx {
        def ifCanWrite(mod: => VDomModifier): VDomModifier = if (canWrite()) mod else VDomModifier.empty

        div(
          Styles.flexStatic,
          cls := "chatmsg-controls",
          if(isDeletedNow()) {
            ifCanWrite(undeleteButton(
              onClickDefault.useLazy(GraphChanges.undelete(ChildId(nodeId), directParentIds)) --> GlobalState.eventProcessor.changes,
            ))
          }
          else VDomModifier(
            replyButton(
              onClickDefault foreach { replyAction }
            ),
            ifCanWrite(deleteButton(
              onClickDefault foreach {
                Elements.confirm("Delete this message?") {
                  GlobalState.submitChanges(GraphChanges.delete(ChildId(nodeId), directParentIds))
                  GlobalState.removeSelectedNode(nodeId)
                }
              },
            )),
          )
        )
      }
    }
  }

  def selectedNodeActions(prependActions: Boolean => List[VNode] = _ => Nil, appendActions: Boolean => List[VNode] = _ => Nil)(implicit ctx: Ctx.Owner): (Vector[SelectedNode], Boolean) => List[VNode] = (selected, canWriteAll) => {
    val allSelectedNodesAreDeleted = Rx {
      val graph = GlobalState.graph()
      selected.forall(t => graph.isDeletedNow(t.nodeId, t.directParentIds))
    }

    val middleActions =
      if (canWriteAll) List(
        SelectedNodes.deleteAllButton( selected, allSelectedNodesAreDeleted)
      ) else Nil

    prependActions(canWriteAll) ::: middleActions ::: appendActions(canWriteAll)
  }

  def createNewButton(focusState:FocusState, addToChannels: Boolean = false, nodeRole: CreateNewPrompt.SelectableNodeRole = CreateNewPrompt.SelectableNodeRole.Task)(implicit ctx: Ctx.Owner): VNode = {
    val show = SinkSourceHandler.publish[Boolean]

    div(
      div(cls := "fa-fw", UI.tooltip := "Create new...", freeSolid.faPlus),
      onClickDefault foreach { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()
        show.onNext(true)
      },

      CreateNewPrompt( show, focusState, addToChannels, nodeRole, defaultParentIds = List(ParentId(focusState.focusedId)))
    )
  }

  def searchButtonWithIcon(onClickAction: VDomModifier)(implicit ctx: Ctx.Owner) = a(
    cls := "item",
    cursor.pointer,
    Elements.icon(Icons.search),
    span("Search"),
    onClickAction,
  )

  def dataImport(implicit owner: Ctx.Owner): VNode = {
    val urlImporter = Handler.unsafe[String]

    def importGithubUrl(url: String): Unit = Client.githubApi.importContent(url)
    def importGitterUrl(url: String): Unit = Client.gitterApi.importContent(url)

    def connectToGithub(): Unit = Client.auth.issuePluginToken().foreach { auth =>
      scribe.info(s"Generated plugin token: $auth")
      val connUser = Client.githubApi.connectUser(auth.token)
      connUser foreach {
        case Some(url) =>
          org.scalajs.dom.window.location.href = url
        case None =>
          scribe.info(s"Could not connect user: $auth")
      }
    }

    div(
      fontWeight.bold,
      fontSize := "20px",
      marginBottom := "10px",
      "Constant synchronization",
      button("Connect to GitHub", width := "100%", onClickDefault foreach(connectToGithub())),
      "One time import",
      input(tpe := "text", width := "100%", onInput.value --> urlImporter),
      button(
        "GitHub",
        width := "100%",
        onClickDefault(urlImporter) foreach((url: String) => importGithubUrl(url))
      ),
      button(
        "Gitter",
        width := "100%",
        onClickDefault(urlImporter) foreach((url: String) => importGitterUrl(url))
      ),
    )
  }

  def expandedNodeContentWithLeftTagColor(nodeId: NodeId): VNode = {
    div(
      Styles.flex,
      div(
        paddingRight := "10px",
        Styles.flexStatic,

        UI.tooltip := "Click to collapse", // we use the js-popup here, since it it always spawns at a visible position
        onClickDefault.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes
      )
    )
  }

  def renderExpandCollapseButton(nodeId: NodeId, isExpanded: Rx[Boolean], alwaysShow: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val childrenSize = Rx {
      val graph = GlobalState.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdxOrThrow(nodeId)) + graph.taskChildrenIdx.sliceLength(graph.idToIdxOrThrow(nodeId))
    }
    Rx {
      if(isExpanded()) {
        div(
          cls := "expand-collapsebutton",
          div(freeSolid.faAngleDown, cls := "fa-fw"),
          onClickDefault.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes,
        )
      } else {
        div(
          cls := "expand-collapsebutton",
          div(freeSolid.faAngleRight, cls := "fa-fw"),
          VDomModifier.ifTrue(!alwaysShow && childrenSize() == 0)(visibility.hidden),
          onClickDefault.useLazy(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), GlobalState.user.now.id)) --> GlobalState.eventProcessor.changes,
        )
      }
    }
  }

  def newNamePromptModalConfig(
    newNameSink: SinkObserver[InputRow.Submission],
    header: VDomModifier,
    body: VDomModifier = VDomModifier.empty,
    placeholder: Placeholder = Placeholder.empty,
    onHide: () => Boolean = () => true,
    enableMentions: Boolean = true,
    enableEmojiPicker: Boolean = false,
    showSubmitIcon:Boolean = true,
    triggerSubmit:SourceStream[Unit] = SourceStream.empty,
    additionalChanges: NodeId => GraphChanges = _ => GraphChanges.empty,
  )(implicit ctx: Ctx.Owner) = {
    ModalConfig(
      header = header,
      description = VDomModifier(
        InputRow(
          focusState = None,
          submitAction = { sub =>
            GlobalState.uiModalClose.onNext(())
            newNameSink.onNext(sub)
          },
          autoFocus = true,
          placeholder = placeholder,
          allowEmptyString = true,
          submitIcon = freeSolid.faArrowRight,
          showSubmitIcon = showSubmitIcon,
          enableMentions = enableMentions,
          enableEmojiPicker = enableEmojiPicker,
          triggerSubmit = triggerSubmit,
          textAreaModifiers = id := "tutorial-modal-inputfield",
        ),

        body
      ),
      modalModifier = VDomModifier(
        minWidth := "320px",
        maxWidth := "400px",
        cls := "create-new-prompt",
      ),
      onHide = onHide
    )
  }

  def onClickNewNamePrompt(
    header: String,
    body: Ownable[VDomModifier] = Ownable.value(VDomModifier.empty),
    placeholder: Placeholder = Placeholder.empty,
    showSubmitIcon:Boolean = true,
    triggerSubmit:SourceStream[Unit] = SourceStream.empty,
    additionalChanges: NodeId => GraphChanges = _ => GraphChanges.empty,
    enableMentions: Boolean = true,
    enableEmojiPicker: Boolean = false,
    onHide: () => Boolean = () => true,
  ) = EmitterBuilder.ofModifier[InputRow.Submission] { sink =>
    VDomModifier(
      onClickDefault.use(Ownable { implicit ctx =>
        newNamePromptModalConfig(
          sink,
          header,
          body(ctx),
          placeholder,
          showSubmitIcon = showSubmitIcon,
          enableMentions = enableMentions,
          enableEmojiPicker = enableEmojiPicker,
          triggerSubmit = triggerSubmit,
          additionalChanges = additionalChanges,
          onHide = onHide,
        )
      }) --> GlobalState.uiModalConfig,
    )
  }

  def channelMembers(channelId: NodeId, enableClickFilter: Boolean = false, separateMembers: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val marginSidePx = 1
    val sizePx = 22
    div(
      Styles.flex,
      alignItems.center,
      minWidth := s"${(2*marginSidePx+sizePx)}px", // show at least 1 avatar
      registerDragContainer,
      Rx {
        val graph = GlobalState.graph()
        val userId = GlobalState.userId()
        val nodeIdx = graph.idToIdxOrThrow(channelId)
        val (selfMembers, otherMembers) = graph.membersByIndex(nodeIdx).partition(_.id == userId)

        def memberVNode(user: Node.User, isSelf: Boolean) = div(
          Avatar.user(user, size = s"${sizePx}px", enableClickFilter = enableClickFilter)(
            margin := "1px",
            VDomModifier.ifTrue(isSelf && separateMembers)(marginRight := s"${sizePx/2}px"),
            UI.tooltip(boundary = "window") := displayUserName(user.data)
          ),
          Styles.flexStatic,
          cursor.grab,
        )

        if(otherMembers.isEmpty)
          VDomModifier(selfMembers.map(memberVNode(_, true)))
        else
          VDomModifier(
            selfMembers.map(memberVNode(_, true)),
            div(
              Styles.flex,
              cls := "tiny-scrollbar",
              overflowX.auto, // make scrollable for long member lists
              overflowY.hidden, // wtf firefox and chrome...
              otherMembers.map(memberVNode(_, false))(breakOut): js.Array[VNode]
            )
          )
      }
    )
  }

  def nodeUrl(nodeId: NodeId)(implicit ctx: Ctx.Owner):Rx[String] = Rx {
    UrlConfigWriter.toUrlRoute(GlobalState.urlConfig().copy(
      view = None,
      pageChange = PageChange(page = Page(Some(nodeId))),
    )).hash.fold("#")("#" + _)
  }
}
