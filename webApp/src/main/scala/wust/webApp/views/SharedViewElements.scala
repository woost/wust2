package wust.webApp.views

import fontAwesome._
import monix.execution.Ack
import monix.reactive.Observer
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl.{label, _}
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop.DragItem.DisableDrag
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.DragComponents.{drag, dragWithHandle}
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, ModalConfig, Ownable, UI}
import wust.webApp.views.DragComponents.{drag, registerDragContainer}

import scala.collection.breakOut
import scala.scalajs.js

object SharedViewElements {

  trait SelectedNodeBase {
    def nodeId: NodeId
    def directParentIds: Iterable[NodeId]
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

  @inline def sortByDeepCreated(nodes: js.Array[Int], graph: Graph): Unit = {
    nodes.sort { (a, b) =>
      val createdA = graph.nodeDeepCreated(a)
      val createdB = graph.nodeDeepCreated(b)
      val result = createdA.compare(createdB)
      if(result == 0) graph.nodeIds(a) compare graph.nodeIds(b)
      else result
    }
  }

  val dragHandle:VNode = div(
    Styles.flex,
    alignItems.center,
    cls := "draghandle",
    paddingLeft := "12px",
    freeSolid.faBars,
    paddingRight := "12px",
    color := "#b3bfca",
    alignSelf.stretch,
    marginLeft.auto,
    onMouseDown.stopPropagation foreach {},
  )

  val replyButton: VNode = {
    div(
      div(cls := "fa-fw", UI.tooltip("bottom right") := "Reply to message", freeSolid.faReply),
      cursor.pointer,
    )
  }

  val deleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.tooltip("bottom right") := "Archive message", Icons.delete),
      cursor.pointer,
    )

  val undeleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.tooltip("bottom right") := "Recover message", Icons.undelete),
      cursor.pointer,
    )

  def chatMessageHeader(state:GlobalState, author: Option[Node.User]): VNode = div(
    cls := "chatmsg-header",
    author.map(author => VDomModifier(smallAuthorAvatar(author), authorName(state, author))),
  )

  //  def chatMessageHeader(state:GlobalState, author: Option[Node.User], creationEpochMillis: EpochMilli, modificationData: IndexedSeq[(Node.User, EpochMilli)], avatar: VDomModifier) = div(
  def chatMessageHeader(state:GlobalState, author: Option[Node.User], creationEpochMillis: EpochMilli, nodeId: NodeId, avatar: VDomModifier)(implicit ctx: Ctx.Owner): VNode = div(
    cls := "chatmsg-header",
    avatar,
    author.map { author =>
      VDomModifier(
        authorName(state, author),
        creationDate(creationEpochMillis),
        state.graph.map { graph =>
          graph.idToIdx(nodeId).map { nodeIdx =>
            modificationDetails(author, graph.nodeModifier(nodeIdx))
          }
        },
      )
    },
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

  def authorName(state: GlobalState, author: Node.User): VNode = {
    div(
      cls := "chatmsg-author",
      Components.displayUserName(author.data),
      onClickDirectMessage(state, author),
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

    @inline def modificationsString = {
      modificationData.map(item => modificationItem(item._1, item._2)).mkString(",\n")
    }

    modificationData.nonEmpty.ifTrue[VDomModifier]{
      val lastModification = modificationData.last
      div(
        cls := "chatmsg-date",
        Styles.flexStatic,
        s"edited${if(author.id != lastModification._1.id) s" by ${lastModification._1.name}" else ""}",
        UI.popupHtml := modificationsHtml,
      )
    }
  }

  def renderMessage(state: GlobalState, nodeId: NodeId, directParentIds:Iterable[NodeId], isDeletedNow: Rx[Boolean], renderedMessageModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): Rx[Option[VDomModifier]] = {

    val node = Rx {
      // we need to get the latest node content from the graph
      val graph = state.graph()
      graph.nodesById(nodeId) //TODO: why option? shouldn't the node always exist?
    }

    val propertyData = Rx {
      val graph = state.graph()
      PropertyData.Single(graph, graph.idToIdxOrThrow(nodeId))
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


    def render(node: Node, isDeletedNow: Boolean)(implicit ctx: Ctx.Owner) = {
      if(isDeletedNow)
        nodeCardWithoutRender(node, maxLength = Some(25)).apply(cls := "node-deleted")
      else {
        node.role match {
          case NodeRole.Task =>
            nodeCardWithCheckbox(state, node, directParentIds).apply(
              Styles.flex,
              alignItems.flexStart,
              renderedMessageModifier,
            )
          case _ =>
            nodeCardWithFile(state, node,
              contentInject = div(
                Styles.flex,
                flexDirection.column,
                propertyData.map { propertySingle =>
                  propertySingle.properties.map { property =>
                    property.values.map { value =>
                      Components.nodeCardProperty(state, value.edge, value.node)
                    }
                  },
                }
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
        render(node, isDeletedNow()).apply(
          cursor.pointer,
          Components.sidebarNodeFocusMod(state.rightSidebarNode, node.id),
          Components.showHoveredNode(state, node.id),
          Components.readObserver(state, node.id)
        )
      }
    }
  }

  def messageDragOptions[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, selectedNodes: Var[Set[T]])(implicit ctx: Ctx.Owner) = VDomModifier(
    Rx {
      val graph = state.graph()
      graph.idToIdx(nodeId).map { nodeIdx =>
        val node = graph.nodes(nodeIdx)
        val selection = selectedNodes()
        // payload is call by name, so it's always the current selectedNodeIds
        def payloadOverride:Option[() => DragPayload] = selection.find(_.nodeId == nodeId).map(_ => () => DragItem.SelectedNodes(selection.map(_.nodeId)(breakOut)))
        VDomModifier(
          nodeDragOptions(nodeId, node.role, withHandle = false, payloadOverride = payloadOverride),
          DragComponents.onAfterPayloadWasDragged.foreach{ selectedNodes() = Set.empty[T] }
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

  def msgCheckbox[T <: SelectedNodeBase](state:GlobalState, nodeId:NodeId, selectedNodes:Var[Set[T]], newSelectedNode: NodeId => T, isSelected:Rx[Boolean])(implicit ctx: Ctx.Owner): VDomModifier =
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
            if(checked) selectedNodes.update(_ + newSelectedNode(nodeId))
            else selectedNodes.update(_.filterNot(_.nodeId == nodeId))
          },
          onClick.stopPropagation --> Observer.empty, // fix safari event, that automatically clicks on the message row, which again would unselect the message
        ),
        label()
      )
    }

  def msgControls[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, directParentIds: Iterable[ParentId], selectedNodes: Var[Set[T]], isDeletedNow:Rx[Boolean], replyAction: => Unit)(implicit ctx: Ctx.Owner): VDomModifier = {

    val canWrite = NodePermission.canWrite(state, nodeId)

    BrowserDetect.isMobile.ifFalse[VDomModifier] {
      Rx {
        def ifCanWrite(mod: => VDomModifier): VDomModifier = if (canWrite()) mod else VDomModifier.empty

        div(
          Styles.flexStatic,
          cls := "chatmsg-controls",
          if(isDeletedNow()) {
            ifCanWrite(undeleteButton(
              onClick(GraphChanges.undelete(ChildId(nodeId), directParentIds)) --> state.eventProcessor.changes,
            ))
          }
          else VDomModifier(
            replyButton(
              onClick foreach { replyAction }
            ),
            ifCanWrite(deleteButton(
              onClick foreach {
                state.eventProcessor.changes.onNext(GraphChanges.delete(ChildId(nodeId), directParentIds))
                selectedNodes.update(_.filterNot(_.nodeId == nodeId))
              },
            )),
          )
        )
      }
    }
  }

  def messageTags(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): Rx[VDomModifier] = {
    Rx {
      val graph = state.graph()
      graph.idToIdx(nodeId).map{ nodeIdx =>
        val directNodeTags = graph.directNodeTags(nodeIdx)
        VDomModifier.ifTrue(directNodeTags.nonEmpty)(
          div(
            cls := "tags",
            directNodeTags.map { tag =>
              removableNodeTag(state, tag, nodeId, pageOnClick = true)(Styles.flexStatic)
            },
          )
        )
      }
    }
  }

  def selectedNodeActions[T <: SelectedNodeBase](state: GlobalState, selectedNodes: Var[Set[T]], prependActions: Boolean => List[VNode] = _ => Nil, appendActions: Boolean => List[VNode] = _ => Nil)(implicit ctx: Ctx.Owner): (List[T], Boolean) => List[VNode] = (selected, canWriteAll) => {
    val allSelectedNodesAreDeleted = Rx {
      val graph = state.graph()
      selected.forall(t => graph.isDeletedNow(t.nodeId, t.directParentIds))
    }

    val middleActions =
      if (canWriteAll) List(
        SelectedNodes.deleteAllButton[T](state, selected, selectedNodes, allSelectedNodesAreDeleted)
      ) else Nil

    prependActions(canWriteAll) ::: middleActions ::: appendActions(canWriteAll)
  }

  def newProjectButton(state: GlobalState, label: String = "New Project"): VNode = {
    val selectedViews = Var[Seq[View.Visible]](Seq.empty)
    def body(implicit ctx: Ctx.Owner) = div(
      color := "#333",
      ViewSwitcher.viewCheckboxes --> selectedViews,
    )

    def newProject(name: String) = {
      val newName = if (name.trim.isEmpty) GraphChanges.newProjectName else name
      val nodeId = NodeId.fresh
      val views = if (selectedViews.now.isEmpty) None else Some(selectedViews.now.toList)
      state.eventProcessor.changes.onNext(GraphChanges.newProject(nodeId, state.user.now.id, newName, views))
      state.urlConfig.update(_.focus(Page(nodeId), needsGet = false))
      selectedViews() = Seq.empty

      Ack.Continue
    }

    button(
      cls := "ui button",
      label,
      onClickNewNamePrompt(state, header = "Create a new Project", body = Ownable { implicit ctx => body }, placeholder = Placeholder("Name of the Project")).foreach(newProject(_)),
      onClick.stopPropagation foreach { ev => ev.target.asInstanceOf[dom.html.Element].blur() },
    )
  }

  def createNewButton(state: GlobalState, label: String = "New", addToChannels: Boolean = false, nodeRole: NodeRole = NodeRole.Task)(implicit ctx: Ctx.Owner): VNode = {
    val show = PublishSubject[Boolean]()

    button(
      cls := "ui tiny compact inverted button",
      label,
      onClick foreach { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()
        show.onNext(true)
      },

      CreateNewPrompt(state, show, addToChannels, nodeRole)
    )
  }

  def searchButtonWithIcon(onClickAction: VDomModifier)(implicit ctx: Ctx.Owner) = a(
    cls := "item",
    cursor.pointer,
    Elements.icon(Icons.search),
    span("Search"),
    onClickAction,
  )

  def dataImport(state: GlobalState)(implicit owner: Ctx.Owner): VNode = {
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
      button("Connect to GitHub", width := "100%", onClick foreach(connectToGithub())),
      "One time import",
      input(tpe := "text", width := "100%", onInput.value --> urlImporter),
      button(
        "GitHub",
        width := "100%",
        onClick(urlImporter) foreach((url: String) => importGithubUrl(url))
      ),
      button(
        "Gitter",
        width := "100%",
        onClick(urlImporter) foreach((url: String) => importGitterUrl(url))
      ),
    )
  }

  def expandedNodeContentWithLeftTagColor(state: GlobalState, nodeId: NodeId): VNode = {
    div(
      Styles.flex,
      div(
        div(
          width := "3px",
          height := "100%",
          backgroundColor := NodeColor.accentColor(nodeId).toHex,
        ),
        paddingRight := "8px",
        Styles.flexStatic,

        cursor.pointer,
        UI.popup := "Click to collapse", // we use the js-popup here, since it it always spawns at a visible position
        onClick.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), state.user.now.id)) --> state.eventProcessor.changes
      )
    )
  }

  def renderExpandCollapseButton(state: GlobalState, nodeId: NodeId, isExpanded: Rx[Boolean], alwaysShow: Boolean = false)(implicit ctx: Ctx.Owner) = {
    val childrenSize = Rx {
      val graph = state.graph()
      graph.messageChildrenIdx.sliceLength(graph.idToIdxOrThrow(nodeId)) + graph.taskChildrenIdx.sliceLength(graph.idToIdxOrThrow(nodeId))
    }
    Rx {
      if(isExpanded()) {
        div(
          cls := "expand-collapsebutton",
          div(freeSolid.faAngleDown, cls := "fa-fw"),
          onClick.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), state.user.now.id)) --> state.eventProcessor.changes,
          cursor.pointer,
        )
      } else {
        div(
          cls := "expand-collapsebutton",
          div(freeSolid.faAngleRight, cls := "fa-fw"),
          VDomModifier.ifTrue(!alwaysShow && childrenSize() == 0)(visibility.hidden),
          onClick.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), state.user.now.id)) --> state.eventProcessor.changes,
          cursor.pointer,
        )
      }
    }
  }

  def newNamePromptModalConfig(state: GlobalState, newNameSink: Observer[String], header: VDomModifier, body: VDomModifier = VDomModifier.empty, placeholder: Placeholder = Placeholder.empty, onClose: () => Boolean = () => true)(implicit ctx: Ctx.Owner) = {
    ModalConfig(
      header = header,
      description = VDomModifier(
        InputRow(
          state = state,
          submitAction = { str =>
            state.uiModalClose.onNext(())
            newNameSink.onNext(str)
          },
          autoFocus = true,
          placeholder = placeholder,
          allowEmptyString = true,
          submitIcon = freeSolid.faArrowRight,
          showSubmitIcon = true,
        ),

        body
      ),
      modalModifier = VDomModifier(
        minWidth := "320px",
        maxWidth := "400px"
      ),
      onClose = onClose
    )
  }

  def onClickNewNamePrompt(state: GlobalState, header: String, body: Ownable[VDomModifier] = Ownable.value(VDomModifier.empty), placeholder: Placeholder = Placeholder.empty) = EmitterBuilder.ofModifier[String] { sink =>
    VDomModifier(
      onClick.stopPropagation(Ownable { implicit ctx => newNamePromptModalConfig(state, sink, header, body(ctx), placeholder) }) --> state.uiModalConfig,
      cursor.pointer
    )
  }

  def channelMembers(state: GlobalState, channelId: NodeId)(implicit ctx: Ctx.Owner) = {
    div(
      Styles.flex,
      cls := "tiny-scrollbar",
      overflowX.auto, // make scrollable for long member lists
      overflowY.hidden, // wtf firefox and chrome...
      registerDragContainer(state),
      Rx {
        val graph = state.graph()
        val nodeIdx = graph.idToIdxOrThrow(channelId)
        val members = graph.membersByIndex(nodeIdx)

        members.map(user => div(
          Avatar.user(user.id)(
            marginLeft := "2px",
            width := "22px",
            height := "22px",
            cls := "avatar",
            marginBottom := "2px",
          ),
          Styles.flexStatic,
          cursor.grab,
          UI.popup("bottom center") := Components.displayUserName(user.data)
        )(
            drag(payload = DragItem.User(user.id)),
          ))(breakOut): js.Array[VNode]
      }
    )
  }

}
