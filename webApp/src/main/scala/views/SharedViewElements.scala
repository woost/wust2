package wust.webApp.views

import cats.effect.IO
import fontAwesome._
import googleAnalytics.Analytics
import monix.eval.Task
import monix.execution.Ack
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.window
import outwatch.dom.{helpers, _}
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.api.{ApiEvent, AuthUser}
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.sdk.NodeColor
import wust.webApp.dragdrop.DragItem
import wust.webApp.jsdom.dateFns
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.Topbar.{login, logout}
import wust.webApp.{BrowserDetect, Client, Icons}

import scala.collection.{breakOut, mutable}
import scala.concurrent.Future
import scala.scalajs.js

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

  val dragHandle = div(
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

  def creationDate(created: EpochMilli): VDomModifier = {
    (created != EpochMilli.min).ifTrue[VDomModifier](
      div(
        cls := "chatmsg-date",
        Styles.flexStatic,
        {
          val createdDate = new js.Date(created)
          if(dateFns.differenceInCalendarDays(new js.Date, createdDate) > 0)
            dateFns.format(new js.Date(created), "Pp") // localized date and time
          else
            dateFns.format(new js.Date(created), "p") // localized only time
        }
//        dateFns.formatDistance(new js.Date(created), new js.Date), " ago",
      )
    )
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

              dragHandle(Styles.flexStatic),
              renderedMessageModifier,
            )
          case _ =>
            nodeCardEditable(state, node, editMode = editMode, state.eventProcessor.changes, contentInject = BrowserDetect.isMobile.ifFalse[VDomModifier](cls := "enable-text-selection")).apply(
              Styles.flex,
              alignItems.flexEnd, // keeps syncIcon at bottom
              importanceIndicator,
              cls := "drag-feedback",

              syncedIcon,
              dragHandle(Styles.flexStatic),
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

  def chatMessageHeader(state:GlobalState, author: Option[Node.User], creationEpochMillis: EpochMilli, avatar: VDomModifier) = div(
    cls := "chatmsg-header",
    Styles.flex,
    avatar,
    author.map(author => VDomModifier(authorName(state, author))),
    creationDate(creationEpochMillis),
  )

  def messageRowDragOptions[T <: SelectedNodeBase](nodeId: NodeId, selectedNodes: Var[Set[T]], editMode: Var[Boolean])(implicit ctx: Ctx.Owner) = VDomModifier(
    // The whole line is draggable, so that it can also be a drag-target.
    // This is currently a limit in the draggable library
    editMode.map { editMode =>
      if(editMode)
        dragDisabled // prevents dragging when selecting text
      else {
        def payload = {
          val selection = selectedNodes.now
          if(selection exists (_.nodeId == nodeId))
            DragItem.Chat.Messages(selection.map(_.nodeId)(breakOut))
          else
            DragItem.Chat.Message(nodeId)
        }
        // payload is call by name, so it's always the current selectedNodeIds
        VDomModifier(
          draggableAs(payload),
          onDraggableDragged.foreach{ selectedNodes() = Set.empty[T] }
        )
      }
    },
    dragTarget(DragItem.Chat.Message(nodeId)),
    cursor.auto, // else draggableAs sets class .draggable, which sets cursor.move
  )

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

  def msgControls[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId], selectedNodes: Var[Set[T]], isDeletedNow:Rx[Boolean], isDeletedInFuture:Rx[Boolean], editMode: Var[Boolean], replyAction: => Unit)(implicit ctx: Ctx.Owner): VNode = {
    val nodeRole = Rx {
      val g = state.graph()
      g.nodesById(nodeId).role
    }

    val canWrite = NodePermission.canWrite(state, nodeId)

    div(
      Styles.flexStatic,
      cls := "chatmsg-controls",
      BrowserDetect.isMobile.ifFalse[VDomModifier] {
        Rx {
          def ifCanWrite(mod: => VDomModifier): VDomModifier = if (canWrite()) mod else VDomModifier.empty

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
        }
      }
    )
  }

  def messageTags(state: GlobalState, nodeId: NodeId, directParentIds: Iterable[NodeId])(implicit ctx: Ctx.Owner): Rx[VNode] = {
    val directNodeTags:Rx[Seq[Node]] = Rx {
      val graph = state.graph()
      graph.directNodeTags(graph.idToIdx(nodeId), graph.createImmutableBitSet(directParentIds))
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

  def newChannelButton(state: GlobalState, label: String = "New Workspace", view: View = View.default): VNode = {
    button(
      cls := "ui button",
      label,
      onClick foreach { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()

        val nodeId = NodeId.fresh
        state.eventProcessor.changes.onNext(GraphChanges.newChannel(nodeId, state.user.now.id))
        state.viewConfig() = state.viewConfig.now.focusView(Page(nodeId), view, needsGet = false)
      }
    )
  }

  def createNewButton(state: GlobalState, label: String = "New", defaultView: View = View.default, addToChannels: Boolean = false, nodeRole: NodeRole = NodeRole.Task)(implicit ctx: Ctx.Owner): VNode = {
    val show = PublishSubject[Boolean]()

    button(
      cls := "ui tiny compact inverted button",
      label,
      onClick foreach { ev =>
        ev.target.asInstanceOf[dom.html.Element].blur()
        show.onNext(true)
      },

      CreateNewPrompt(state, show, defaultView, addToChannels, nodeRole)
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
