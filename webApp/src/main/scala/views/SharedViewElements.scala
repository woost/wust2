package wust.webApp.views

import dateFns.DateFns
import fontAwesome._
import googleAnalytics.Analytics
import monix.eval.Task
import monix.execution.Ack
import monix.reactive.Observable
import monix.reactive.subjects.{BehaviorSubject, PublishSubject}
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.{ApiEvent, AuthUser}
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.dragdrop.DragItem.DisableDrag
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.Topbar.{login, logout}
import wust.webApp.views.UI.ModalConfig
import wust.webApp.{BrowserDetect, Client, Icons}

import scala.collection.breakOut
import scala.concurrent.Future
import scala.scalajs.js
import scala.util.Try

object SharedViewElements {

  @inline def noiseFutureDeleteDate = EpochMilli(EpochMilli.now + EpochMilli.week)

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

  def uploadFileAndCreateNode(state: GlobalState, str: String, replyNodes: Iterable[NodeId], uploadFile: AWS.UploadableFile): Future[Ack] = {
    val fileNodeData = NodeData.File(key = "", fileName = uploadFile.file.name, contentType = uploadFile.file.`type`, description = str) // TODO: empty string for signaling pending fileupload
    val fileNode = Node.Content(fileNodeData, NodeRole.Message)

    val ack = state.eventProcessor.localEvents.onNext(ApiEvent.NewGraphChanges.forPrivate(state.user.now.toNode, GraphChanges.addNodeWithParent(fileNode, replyNodes).withAuthor(state.user.now.id)))

    var uploadTask: Task[Unit] = null
    uploadTask = Task.defer{
      state.uploadingFiles.update(_ ++ Map(fileNode.id -> UploadingFile.Waiting(uploadFile.dataUrl)))

      uploadFile.uploadKey.map {
        case Some(key) =>
          state.uploadingFiles.update(_ - fileNode.id)
          state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(fileNode.copy(data = fileNodeData.copy(key = key)), replyNodes))
          ()
        case None      =>
          state.uploadingFiles.update(_ ++ Map(fileNode.id -> UploadingFile.Error(uploadFile.dataUrl, uploadTask)))
          ()
      }
    }

    uploadTask.runAsyncAndForget

    ack
  }

  def inputRow(
    state: GlobalState,
    submitAction: String => Future[Ack],
    fileUploadHandler: Option[Var[Option[AWS.UploadableFile]]] = None,
    blurAction: Option[String => Unit] = None,
    scrollHandler:Option[ScrollBottomHandler] = None,
    triggerFocus:Observable[Unit] = Observable.empty,
    autoFocus:Boolean = false,
    placeHolderMessage:Option[String] = None,
    preFillByShareApi:Boolean = false,
    submitIcon:VDomModifier = freeRegular.faPaperPlane,
    textAreaModifiers:VDomModifier = VDomModifier.empty,
  )(implicit ctx: Ctx.Owner): VNode = {
    val initialValue = if(preFillByShareApi) Rx {
      state.viewConfig().shareOptions.map { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }.toObservable.collect { case Some(s) => s } else Observable.empty

    val autoResizer = new TextAreaAutoResizer

    val heightOptions = VDomModifier(
      rows := 1,
      resize := "none",
      minHeight := "42px",
      autoResizer.modifiers
    )

    var currentTextArea: dom.html.TextArea = null
    def handleInput(str: String): Unit = if (str.trim.nonEmpty || fileUploadHandler.exists(_.now.isDefined)) {
      val submitted = submitAction(str)
      if(BrowserDetect.isMobile) currentTextArea.focus() // re-gain focus on mobile. Focus gets lost and closes the on-screen keyboard after pressing the button.

      // trigger autoResize
      submitted.foreach { _ =>
        autoResizer.trigger()
      }
    }

    val initialValueAndSubmitOptions = {
      if (BrowserDetect.isMobile) {
        value <-- initialValue
      } else {
        valueWithEnterWithInitial(initialValue) foreach handleInput _
      }
    }

    val placeHolderString = placeHolderMessage.getOrElse {
      if(BrowserDetect.isMobile || state.screenSize.now == ScreenSize.Small) "Write a message"
      else "Write a message and press Enter to submit (use markdown to format)."
    }

    val immediatelyFocus = {
      autoFocus.ifTrue(
        onDomMount.asHtml --> inNextAnimationFrame(_.focus())
      )
    }

    val pageScrollFixForMobileKeyboard = BrowserDetect.isMobile.ifTrue(VDomModifier(
      scrollHandler.map { scrollHandler =>
        VDomModifier(
          onFocus foreach {
            // when mobile keyboard opens, it may scroll up.
            // so we scroll down again.
            if(scrollHandler.isScrolledToBottomNow) {
              window.setTimeout(() => scrollHandler.scrollToBottomInAnimationFrame(), 500)
              // again for slower phones...
              window.setTimeout(() => scrollHandler.scrollToBottomInAnimationFrame(), 2000)
              ()
            }
          },
          eventProp("touchstart") foreach {
            // if field is already focused, but keyboard is closed:
            // we do not know if the keyboard is opened right now,
            // but we can detect if it was opened: by screen-height changes
            if(scrollHandler.isScrolledToBottomNow) {
              val screenHeight = window.screen.availHeight
              window.setTimeout({ () =>
                val keyboardWasOpened = screenHeight > window.screen.availHeight
                if(keyboardWasOpened) scrollHandler.scrollToBottomInAnimationFrame()
              }, 500)
              // and again for slower phones...
              window.setTimeout({ () =>
                val keyboardWasOpened = screenHeight > window.screen.availHeight
                if(keyboardWasOpened) scrollHandler.scrollToBottomInAnimationFrame()
              }, 2000)
              ()
            }
          }
        )
      }
    ))

    val submitButton = BrowserDetect.isMobile.ifTrue[VDomModifier](
      div( // clickable box around circular button
        padding := "3px",
        button(
          margin := "0px",
          Styles.flexStatic,
          cls := "ui circular icon button",
          submitIcon,
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

    div(
      emitter(triggerFocus).foreach { currentTextArea.focus() },
      Styles.flex,

      alignItems.center,
      fileUploadHandler.map(uploadField(state, _)),
      div(
        margin := "3px",
        BrowserDetect.isMobile.ifTrue[VDomModifier](marginRight := "0"),
        width := "100%",
        cls := "ui form",
        textArea(
          cls := "field",
          initialValueAndSubmitOptions,
          heightOptions,
          placeholder := placeHolderString,

          immediatelyFocus,
          blurAction.map(onBlur.value foreach _),
          pageScrollFixForMobileKeyboard,
          onDomMount foreach { e => currentTextArea = e.asInstanceOf[dom.html.TextArea] },
          textAreaModifiers,
        )
      ),
      submitButton
    )
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

  val activeStarButton: VNode = {
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Mark as unimportant and delete automatically after a week", freeSolid.faStar, color := "#fbbd08"),
      cursor.pointer,
    )
  }

  val inactiveStarButton: VNode = {
    div(
      div(cls := "fa-fw", UI.popup("bottom right"):= "Mark as important", freeRegular.faStar, color := "#fbbd08"),
      cursor.pointer,
    )
  }

  val replyButton: VNode = {
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Reply to message", freeSolid.faReply),
      cursor.pointer,
    )
  }

  val editButton: VNode =
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Edit message", Icons.edit),
      cursor.pointer,
    )

  val deleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Delete message", Icons.delete),
      cursor.pointer,
    )

  val undeleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Recover message", Icons.undelete),
      cursor.pointer,
    )

  val zoomButton: VNode =
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Zoom into message and focus", Icons.zoom),
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

  def authorName(state: GlobalState, author: Node.User): VNode = {
    div(
      cls := "chatmsg-author",
      Styles.flexStatic,
      Components.displayUserName(author.data),
      onClickDirectMessage(state, author),
    )
  }

  def dateString(epochMilli: EpochMilli): String = {
    val createdDate = new js.Date(epochMilli)
    if(DateFns.differenceInCalendarDays(new js.Date, createdDate) > 0)
      DateFns.format(new js.Date(epochMilli), "Pp") // localized date and time
    else
      DateFns.format(new js.Date(epochMilli), "p") // localized only time
  }

  def creationDate(created: EpochMilli): VDomModifier = {
    (created != EpochMilli.min).ifTrue[VDomModifier](
      div(
        cls := "chatmsg-date",
        Styles.flexStatic,
        dateString(created),
      )
    )
  }

  def modifications(author: Node.User, modificationData: IndexedSeq[(Node.User, EpochMilli)]): VDomModifier = {

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

  def renderMessage(state: GlobalState, nodeId: NodeId, directParentIds:Iterable[NodeId], isDeletedNow: Rx[Boolean], isDeletedInFuture: Rx[Boolean], editMode: Var[Boolean], renderedMessageModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): Rx[Option[VDomModifier]] = {

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


    def render(node: Node, isDeletedNow: Boolean)(implicit ctx: Ctx.Owner) = {
      val baseNode = if (isDeletedNow) nodeCardWithoutRender(node, maxLength = Some(25)).apply(cls := "node-deleted")
      else {
        val importanceIndicator = Rx {
          val unimportant = editMode() || isDeletedInFuture()
          unimportant.ifFalse[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px #fbbd08"))
        }
        node.role match {
          case NodeRole.Task =>
            nodeCardWithCheckbox(state, node, directParentIds).apply(
              Styles.flex,
              alignItems.flexStart,
              cls := "drag-feedback",

              // dragHandle(Styles.flexStatic),
              renderedMessageModifier,
            )
          case _ =>
            nodeCardEditable(state, node, editMode = editMode, state.eventProcessor.changes).apply(
              Styles.flex,
              alignItems.flexEnd, // keeps syncIcon at bottom
              importanceIndicator,
              cls := "drag-feedback",

              // Sadly it is not possible to FLOAT the syncedicon to the bottom right:
              // https://stackoverflow.com/questions/499829/how-can-i-wrap-text-around-a-bottom-right-div/499883#499883
              syncedIcon,
              // dragHandle(Styles.flexStatic),
              renderedMessageModifier,
            )
        }
      }

      baseNode
    }

    Rx {
      node().map(render(_, isDeletedNow()))
    }
  }

  def chatMessageHeader(state:GlobalState, author: Option[Node.User], avatar: VDomModifier) = div(
    cls := "chatmsg-header",
    Styles.flex,
    avatar,
    author.map(author => VDomModifier(authorName(state, author))),
  )

//  def chatMessageHeader(state:GlobalState, author: Option[Node.User], creationEpochMillis: EpochMilli, modificationData: IndexedSeq[(Node.User, EpochMilli)], avatar: VDomModifier) = div(
  def chatMessageHeader(state:GlobalState, author: Option[Node.User], creationEpochMillis: EpochMilli, nodeId: NodeId, avatar: VDomModifier)(implicit ctx: Ctx.Owner) = div(
    cls := "chatmsg-header",
    Styles.flex,
    avatar,
    author.map { author =>
      VDomModifier(
        authorName(state, author),
        creationDate(creationEpochMillis),
        state.graph.map { graph =>
          modifications(author, graph.nodeModifier(graph.idToIdx(nodeId)))
        },
      )
    },
  )

  def messageDragOptions[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, selectedNodes: Var[Set[T]], editMode: Var[Boolean])(implicit ctx: Ctx.Owner) = VDomModifier(
    Rx {
      val graph = state.graph()
      val node = graph.nodesById(nodeId)
      VDomModifier.ifNot(editMode()){ // prevents dragging when selecting text
        val selection = selectedNodes()
        // payload is call by name, so it's always the current selectedNodeIds
        def payloadOverride:Option[() => DragPayload] = selection.find(_.nodeId == nodeId).map(_ => () => DragItem.SelectedNodes(selection.map(_.nodeId)(breakOut)))
        VDomModifier(
          nodeDragOptions(nodeId, node.role, withHandle = false, payloadOverride = payloadOverride),
          onAfterPayloadWasDragged.foreach{ selectedNodes() = Set.empty[T] }
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
          }
        ),
        label()
      )
    }

  def msgControls[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodes: Var[Set[T]], isDeletedNow:Rx[Boolean], isDeletedInFuture:Rx[Boolean], editMode: Var[Boolean], replyAction: => Unit)(implicit ctx: Ctx.Owner): VDomModifier = {

    val canWrite = NodePermission.canWrite(state, nodeId)

    BrowserDetect.isMobile.ifFalse[VDomModifier] {
      Rx {
        def ifCanWrite(mod: => VDomModifier): VDomModifier = if (canWrite()) mod else VDomModifier.empty

        div(
          Styles.flexStatic,
          cls := "chatmsg-controls",
          if(isDeletedNow()) {
            ifCanWrite(undeleteButton(
              onClick(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
            ))
          }
          else VDomModifier(
            if(isDeletedInFuture()) {
              ifCanWrite(inactiveStarButton(
                onClick(GraphChanges.undelete(nodeId, directParentIds)) --> state.eventProcessor.changes,
              ))
            } else {
              ifCanWrite(activeStarButton(
                onClick foreach {
                  state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds, noiseFutureDeleteDate))
                  ()
                }
              ))
            },
            replyButton(
              onClick foreach { replyAction }
            ),
            ItemProperties.manageProperties(state, nodeId),
            ifCanWrite(editButton(
              onClick.mapTo(!editMode.now) --> editMode
            )),
            ifCanWrite(deleteButton(
              onClick foreach {
                state.eventProcessor.changes.onNext(GraphChanges.delete(nodeId, directParentIds))
                selectedNodes.update(_.filterNot(_.nodeId == nodeId))
              },
            )),
            zoomButton(
              onClick.mapTo(state.viewConfig.now.focus(Page(nodeId))) --> state.viewConfig,
            ),
          )
        )
      }
    }
  }

  def messageTags(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId])(implicit ctx: Ctx.Owner): Rx[VDomModifier] = {
    Rx {
      val graph = state.graph()
      val directNodeTags = graph.directNodeTags(graph.idToIdx(nodeId), graph.createImmutableBitSet(directParentIds))
      VDomModifier.ifTrue(directNodeTags.nonEmpty)(
        state.screenSize.now match {
          case ScreenSize.Small =>
            div(
              cls := "tags",
              directNodeTags.map { tag =>
                nodeTagDot(state, tag)(Styles.flexStatic)
              },
            )
          case _                =>
            div(
              cls := "tags",
              directNodeTags.map { tag =>
                removableNodeTag(state, tag, nodeId)(Styles.flexStatic)
              },
            )
        }
      )
    }
  }

  def starActionButton[T <: SelectedNodeBase](state: GlobalState, selected: List[T], selectedNodes: Var[Set[T]], anySelectedNodeIsDeleted: Rx[Boolean], anySelectedNodeIsDeletedInFuture: Rx[Boolean])(implicit ctx:Ctx.Owner): BasicVNode = {
    div(
      Rx {
        if(anySelectedNodeIsDeleted() || anySelectedNodeIsDeletedInFuture())
          VDomModifier(
            inactiveStarButton,
            onClick foreach {
              val changes = selected.foldLeft(GraphChanges.empty)((c, t) => c merge GraphChanges.undelete(t.nodeId, t.directParentIds))
              state.eventProcessor.changes.onNext(changes)
              selectedNodes() = Set.empty[T]
            }
          )
        else
          VDomModifier(
            activeStarButton,
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

  def selectedNodeActions[T <: SelectedNodeBase](state: GlobalState, selectedNodes: Var[Set[T]], prependActions: Boolean => List[VNode] = _ => Nil, appendActions: Boolean => List[VNode] = _ => Nil)(implicit ctx: Ctx.Owner): (List[T], Boolean) => List[VNode] = (selected, canWriteAll) => {
    val nodeIdSet:List[NodeId] = selected.map(_.nodeId)(breakOut)
    val allSelectedNodesAreDeleted = Rx {
      val graph = state.graph()
      selected.forall(t => graph.isInDeletedGracePeriod(t.nodeId, t.directParentIds))
    }

    val anySelectedNodeIsDeleted = Rx {
      val graph = state.graph()
      selected.forall(t => graph.isInDeletedGracePeriod(t.nodeId, t.directParentIds))
    }

    val anySelectedNodeIsDeletedInFuture = Rx {
      val graph = state.graph()
      selected.exists(t => graph.isDeletedInFuture(t.nodeId, t.directParentIds))
    }

    val middleActions =
      if (canWriteAll) List(
        starActionButton(state, selected, selectedNodes, anySelectedNodeIsDeleted = anySelectedNodeIsDeleted, anySelectedNodeIsDeletedInFuture = anySelectedNodeIsDeletedInFuture),
        SelectedNodes.deleteAllButton[T](state, selected, selectedNodes, allSelectedNodesAreDeleted)
      ) else Nil

    prependActions(canWriteAll) ::: middleActions ::: appendActions(canWriteAll)
  }

  def newChannelButton(state: GlobalState, label: String = "New Workspace", view: Option[View] = None): VNode = {
    button(
      cls := "ui button",
      label,
      onClick foreach { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()

        val nodeId = NodeId.fresh
        state.eventProcessor.changes.onNext(GraphChanges.newChannel(nodeId, state.user.now.id))
        state.viewConfig() = state.viewConfig.now.focus(Page(nodeId), view, needsGet = false)
      }
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

  def authStatus(state: GlobalState)(implicit ctx: Ctx.Owner): Rx[VNode] =
    state.user.map {
      case user: AuthUser.Assumed  => login(state).apply(Styles.flexStatic)
      case user: AuthUser.Implicit => login(state).apply(Styles.flexStatic)
      case user: AuthUser.Real     => div(
        Styles.flex,
        alignItems.center,
        div(
          Styles.flex,
          alignItems.center,
          Avatar.user(user.id)(height := "20px", cls := "avatar"),
          span(
            user.name,
            padding := "0 5px",
            Styles.wordWrap
          ),
          cursor.pointer,
          onClick[View](View.UserSettings) --> state.view,
          onClick foreach { Analytics.sendEvent("authstatus", "avatar") },
        ),
        logout(state))
    }
}





object ItemProperties {
  import wust.sdk.BaseColors
  import wust.sdk.NodeColor.hue
  import wust.webApp.outwatchHelpers.Ownable

  def manageProperties(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VNode = {

    // todo: check if node is instance of node.content ?
    val graph = state.graph.now
    val node = graph.nodesById(nodeId).asInstanceOf[Node.Content]

    val modalCloseTrigger = PublishSubject[Unit]

    val clear = Handler.unsafe[Unit].mapObservable(_ => "")

    val propertyTypeSelection = BehaviorSubject[String]("none")
    val propertyKeyInputProcess = BehaviorSubject[String]("")
    val propertyValueInputProcess = BehaviorSubject[String]("")

    clear foreach(_ => propertyTypeSelection.onNext("none"))
    clear foreach(_ => propertyKeyInputProcess.onNext(""))
    clear foreach(_ => propertyValueInputProcess.onNext(""))

    def description(implicit ctx: Ctx.Owner) = {
      var element: dom.html.Element = null
      val inputSizeMods = VDomModifier(width := "250px", marginTop := "4px")

      val inputFieldMod = (propertyType: String) => if(propertyType == NodeData.Integer.tpe) {
        VDomModifier(
          tpe := "number",
          step := "1",
          placeholder := "Integer Number",
        )
      } else if(propertyType == NodeData.Float.tpe) {
        VDomModifier(
          tpe := "number",
          step := "any",
          placeholder := "Floating Point Number",
        )
      } else if(propertyType == NodeData.Date.tpe) {
        VDomModifier(
          tpe := "date",
        )
      } else if(propertyType == NodeData.PlainText.tpe) {
        VDomModifier(
          tpe := "text",
          placeholder := "Text",
        )
      } else {
        VDomModifier(
          disabled,
          placeholder := "ERROR MATICHNG TYPE",
        )
      }

      VDomModifier(
        form(
          onDomMount.asHtml.foreach { element = _ },
          select(
            inputSizeMods,
            option(
              value := "none", "Select a property type",
              selected,
              selected <-- clear.map(_ => true),
              disabled,
            ),
            option( value := NodeData.Integer.tpe, "Integer Number" ),
            option( value := NodeData.Float.tpe, "Floating Point Number" ),
            option( value := NodeData.Date.tpe, "Date" ),
            option( value := NodeData.PlainText.tpe, "Text" ),
            onInput.value --> propertyTypeSelection,
          ),
          input(
            cls := "ui fluid action input",
            inputSizeMods,
            tpe := "text",
            placeholder := "Property Name",
            value <-- clear,
            cls <-- propertyTypeSelection.map(t => if(t == "none") "disabled" else ""),
            onInput.value --> propertyKeyInputProcess,
          ),
          VDomModifier(
            input(
              cls := "ui fluid action input",
              inputSizeMods,
              value <-- clear,
              onInput.value --> propertyValueInputProcess,
              cls <-- propertyKeyInputProcess.map(k => if(k.isEmpty) "disabled" else ""),
              propertyTypeSelection.map(inputFieldMod),
            ),
            div(
              cls := "ui primary button approve",
              cls <-- propertyValueInputProcess.map(v => if(v.isEmpty) "disabled" else ""),
              inputSizeMods,
              "Add property",
              onClick(propertyValueInputProcess.withLatestFrom2(propertyKeyInputProcess, propertyTypeSelection)((pValue, pKey, pType) => (pKey, pValue, pType))) foreach { propertyData =>
                handleAddProperty(propertyData._1, propertyData._2, propertyData._3)
              },
            ),
          )
        ),
        div(
          Rx{
            val graph = state.graph()
            val propertyEdgesIdx = graph.propertiesEdgeIdx(graph.idToIdx(nodeId))
            scribe.info(s"PROPERTIES: found ${propertyEdgesIdx.length} property edges")
            val propertyEdges = propertyEdgesIdx.map(eIdx => graph.edges(eIdx).asInstanceOf[Edge.LabeledProperty])
            val propertyData = propertyEdges.map(e => (e.data.key, graph.nodesById(e.propertyId).asInstanceOf[Node.Content]))

            propertyData.map(data => propertyRow(data._1, data._2))
          },
        ),
        a(
          href := "#",
          paddingTop := "15px",
          onClick.stopPropagation.mapTo(state.viewConfig.now.focusView(Page(node.id), View.Property)) --> state.viewConfig,
          "Show detailed property view",
        ),
      ),
    }

    def handleAddProperty(propertyKey: String, propertyValue: String, propertyType: String)(implicit ctx: Ctx.Owner): Unit = {

      val propertyOpt: Option[NodeData.Content] = propertyType match {
        case NodeData.Integer.tpe   => Try(propertyValue.toInt).toOption.map(number => NodeData.Integer(number))
        case NodeData.Float.tpe     => Try(propertyValue.toDouble).toOption.map(number => NodeData.Float(number))
        case NodeData.Date.tpe      => Try(new js.Date(propertyValue).getTime.toLong).toOption.map(datum => NodeData.Date(EpochMilli(datum)))
        case NodeData.PlainText.tpe => Some(NodeData.PlainText(propertyValue))
        case _                      => None
      }

      propertyOpt.foreach { data =>
        val propertyNode = Node.Content(data, NodeRole.Property)
        val propertyEdge = Edge.LabeledProperty(nodeId, EdgeData.LabeledProperty(propertyKey, data.tpe), propertyNode.id)
        val gc = GraphChanges(addNodes = Set(propertyNode), addEdges = Set(propertyEdge))

        state.eventProcessor.changes.onNext(gc).foreach {_ =>
          clear.onNext(())
          //          modalCloseTrigger.onNext(())
        }
      }
    }

    // TODO
    def handleRemoveProperty(propertyId: NodeId)(implicit ctx: Ctx.Owner): Unit = {
      //      state.eventProcessor.changes.onNext(
      //        GraphChanges.disconnect(Edge.LabeledProperty)(nodeId, propertyId)
      //      )
      //      modalCloseTrigger.onNext(())
    }

    def propertyRow(propertyKey: String, propertyValue: Node.Content)(implicit ctx: Ctx.Owner): VNode = {
      div(
        Styles.flex,
        Styles.flexStatic,
        marginTop := "10px",
        alignItems.center,
        div(
          span(fontWeight.bold, s"$propertyKey: "),
          span(s"${propertyValue.str}"),
        ),
      )
    }

    val propertyButton: VNode = {
      div(
        div(cls := "fa-fw", UI.popup("bottom right") := "Manage properties", Icons.property),
        cursor.pointer,

        onClick(Ownable(implicit ctx => UI.ModalConfig(
          header = ModalConfig.defaultHeader(state, node, "Manage properties", Icons.property),
          description = description,
          close = modalCloseTrigger,
          modalModifier = VDomModifier(
            cls := "mini form",
          ),
          contentModifier = VDomModifier(
            backgroundColor := BaseColors.pageBgLight.copy(h = hue(node.id)).toHex
          )
        ))) --> state.modalConfig
      )
    }

    propertyButton
  }

}
