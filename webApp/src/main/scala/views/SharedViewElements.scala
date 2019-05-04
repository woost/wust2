package wust.webApp.views

import wust.webApp._
import dateFns.DateFns
import fontAwesome._
import googleAnalytics.Analytics
import monix.eval.Task
import monix.execution.Ack
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.{PublishSubject, ReplaySubject}
import org.scalajs.dom
import org.scalajs.dom.window
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.api.{ApiEvent, AuthUser}
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor
import wust.util._
import wust.webApp.dragdrop.DragItem.DisableDrag
import wust.webApp.dragdrop.{DragItem, DragPayload, DragTarget}
import wust.webApp.outwatchHelpers._
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.webApp.views.Topbar.{login, logout}

import scala.collection.breakOut
import scala.concurrent.Future
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

  def inputRow(
    state: GlobalState,
    submitAction: String => Unit,
    fileUploadHandler: Option[Var[Option[AWS.UploadableFile]]] = None,
    blurAction: Option[String => Unit] = None,
    scrollHandler:Option[ScrollBottomHandler] = None,
    triggerFocus:Observable[Unit] = Observable.empty,
    autoFocus:Boolean = false,
    placeHolderMessage:Option[String] = None,
    preFillByShareApi:Boolean = false,
    submitIcon:VDomModifier = freeRegular.faPaperPlane,
    showSubmitIcon: Boolean = BrowserDetect.isMobile,
    textAreaModifiers:VDomModifier = VDomModifier.empty,
    allowEmptyString: Boolean = false,
    enforceUserName: Boolean = false,
    showMarkdownHelp: Boolean = false
  )(implicit ctx: Ctx.Owner): VNode = {
    val initialValue = if(preFillByShareApi) Rx {
      state.urlConfig().shareOptions.fold("") { share =>
        val elements = List(share.title, share.text, share.url).filter(_.nonEmpty)
        elements.mkString(" - ")
      }
    }.toObservable.dropWhile(_.isEmpty) else Observable.empty // drop starting sequence of empty values. only interested once share api defined.

    val autoResizer = new TextAreaAutoResizer

    val heightOptions = VDomModifier(
      rows := 1,
      resize := "none",
      minHeight := "42px",
      autoResizer.modifiers
    )

    var currentTextArea: dom.html.TextArea = null
    def handleInput(str: String): Unit = if (allowEmptyString || str.trim.nonEmpty || fileUploadHandler.exists(_.now.isDefined)) {
      def handle() = {
        submitAction(str)
        if (preFillByShareApi && state.urlConfig.now.shareOptions.isDefined) {
          state.urlConfig.update(_.copy(shareOptions = None))
        }
        if(BrowserDetect.isMobile) currentTextArea.focus() // re-gain focus on mobile. Focus gets lost and closes the on-screen keyboard after pressing the button.
      }
      if (enforceUserName && !state.askedForUnregisteredUserName.now) {
        state.askedForUnregisteredUserName() = true
        state.user.now match {
          case user: AuthUser.Implicit if user.name.isEmpty =>
            val sink = state.eventProcessor.changes.redirectMapMaybe[String] { str =>
              val userNode = user.toNode
              userNode.data.updateName(str).map(data => GraphChanges.addNode(userNode.copy(data = data)))
            }
            state.uiModalConfig.onNext(Ownable(implicit ctx => newNamePromptModalConfig(state, sink, "Give yourself a name, so others can recognize you.", placeholderMessage = Some(Components.implicitUserName), onClose = () => { handle(); true })))
          case _ => handle()
        }
      } else {
        handle()
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
      else "Write a message and press Enter to submit."
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

    val submitButton = VDomModifier.ifTrue(showSubmitIcon)(
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
          autoResizer.trigger()
        },
      )
    )

    div(
      emitter(triggerFocus).foreach { currentTextArea.focus() },
      Styles.flex,

      // show textarea above of tags-button on right hand side
      zIndex := ZIndex.overlayMiddle + 1,

      alignItems.center,
      fileUploadHandler.map(uploadField(state, _).apply(flex := "1")),
      div(
        margin := "3px",
        BrowserDetect.isMobile.ifTrue[VDomModifier](marginRight := "0"),
        width := "100%",
        cls := "ui form",

        VDomModifier.ifTrue(showMarkdownHelp)(
          position.relative,
          a(
            position.absolute,
            right := "4px",
            top := "4px",
            float.right,
            freeSolid.faQuestion,
            Elements.safeTargetBlank,
            UI.popup("left center") := "Use Markdown to format your text. Click for more details.",
            href := "https://www.markdownguide.org/basic-syntax/"
          ),
        ),

        textArea(
          onDomUpdate.foreach(autoResizer.trigger()),
          maxHeight := "400px",
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

  val replyButton: VNode = {
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Reply to message", freeSolid.faReply),
      cursor.pointer,
    )
  }

  val deleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Archive message", Icons.delete),
      cursor.pointer,
    )

  val undeleteButton: VNode =
    div(
      div(cls := "fa-fw", UI.popup("bottom right") := "Recover message", Icons.undelete),
      cursor.pointer,
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

  def renderMessage(state: GlobalState, nodeId: NodeId, directParentIds:Iterable[NodeId], isDeletedNow: Rx[Boolean], renderedMessageModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): Rx[Option[VDomModifier]] = {

    val node = Rx {
      // we need to get the latest node content from the graph
      val graph = state.graph()
      graph.nodesByIdGet(nodeId) //TODO: why option? shouldn't the node always exist?
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
                      Components.propertyTag(state, value.edge, value.node)
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

  def messageDragOptions[T <: SelectedNodeBase](state: GlobalState, nodeId: NodeId, selectedNodes: Var[Set[T]])(implicit ctx: Ctx.Owner) = VDomModifier(
    Rx {
      val graph = state.graph()
      val node = graph.nodesById(nodeId)
      val selection = selectedNodes()
      // payload is call by name, so it's always the current selectedNodeIds
      def payloadOverride:Option[() => DragPayload] = selection.find(_.nodeId == nodeId).map(_ => () => DragItem.SelectedNodes(selection.map(_.nodeId)(breakOut)))
      VDomModifier(
        nodeDragOptions(nodeId, node.role, withHandle = false, payloadOverride = payloadOverride),
        onAfterPayloadWasDragged.foreach{ selectedNodes() = Set.empty[T] }
      )
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
      val directNodeTags = graph.directNodeTags(graph.idToIdx(nodeId))
      VDomModifier.ifTrue(directNodeTags.nonEmpty)(
        state.screenSize.now match {
          case ScreenSize.Small =>
            div(
              cls := "tags",
              directNodeTags.map { tag =>
                nodeTagDot(state, tag, pageOnClick = true)(Styles.flexStatic)
              },
            )
          case _                =>
            div(
              cls := "tags",
              directNodeTags.map { tag =>
                removableNodeTag(state, tag, nodeId, pageOnClick = true)(Styles.flexStatic)
              },
            )
        }
      )
    }
  }

  def selectedNodeActions[T <: SelectedNodeBase](state: GlobalState, selectedNodes: Var[Set[T]], prependActions: Boolean => List[VNode] = _ => Nil, appendActions: Boolean => List[VNode] = _ => Nil)(implicit ctx: Ctx.Owner): (List[T], Boolean) => List[VNode] = (selected, canWriteAll) => {
    val nodeIdSet:List[NodeId] = selected.map(_.nodeId)(breakOut)
    val allSelectedNodesAreDeleted = Rx {
      val graph = state.graph()
      selected.forall(t => graph.isInDeletedGracePeriod(t.nodeId, t.directParentIds))
    }

    val middleActions =
      if (canWriteAll) List(
        SelectedNodes.deleteAllButton[T](state, selected, selectedNodes, allSelectedNodesAreDeleted)
      ) else Nil

    prependActions(canWriteAll) ::: middleActions ::: appendActions(canWriteAll)
  }

  def newProjectButton(state: GlobalState, label: String = "New Project"): VNode = {
    val selectedViews = Var[Seq[View.Visible]](Seq.empty)
    val body = div(
      Styles.flex,
      flexDirection.column,
      alignItems.center,
      color := "#333",
      ViewSwitcher.viewCheckboxes --> selectedViews,
    )

    def newProject(name: String) = {
      val newName = if (name.trim.isEmpty) GraphChanges.newProjectName else name
      val nodeId = NodeId.fresh
      val views = if (selectedViews.now.isEmpty) None else Some(selectedViews.now.toList)
      state.eventProcessor.changes.onNext(GraphChanges.newProject(nodeId, state.user.now.id, newName, views))
      state.urlConfig.update(_.focus(Page(nodeId), needsGet = false))

      Ack.Continue
    }

    button(
      cls := "ui button",
      label,
      onClickNewNamePrompt(state, header = "Create a new Project", body = body, placeholderMessage = Some("Name of the Project")).foreach(newProject(_)),
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
          onClick foreach {
            state.urlConfig.update(_.focus(View.UserSettings))
            Analytics.sendEvent("authstatus", "avatar") },
        ),
        logout(state))
    }

  def expandedNodeContentWithLeftTagColor(state: GlobalState, nodeId: NodeId): VNode = {
    div(
      Styles.flex,
      div(
        div(
          width := "3px",
          height := "100%",
          backgroundColor := NodeColor.tagColor(nodeId).toHex,
        ),
        padding := "0px 8px",
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
      graph.messageChildrenIdx.sliceLength(graph.idToIdx(nodeId)) + graph.taskChildrenIdx.sliceLength(graph.idToIdx(nodeId))
    }
    Rx {
      if(isExpanded()) {
        div(
          cls := "expand-collapsebutton",
          Icons.collapse,
          onClick.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(false), state.user.now.id)) --> state.eventProcessor.changes,
          cursor.pointer,
        )
      } else {
        div(
          cls := "expand-collapsebutton",
          VDomModifier.ifTrue(!alwaysShow && childrenSize() == 0)(visibility.hidden),
          Icons.expand,
          onClick.mapTo(GraphChanges.connect(Edge.Expanded)(nodeId, EdgeData.Expanded(true), state.user.now.id)) --> state.eventProcessor.changes,
          cursor.pointer,
        )
      }
    }
  }

  def newNamePromptModalConfig(state: GlobalState, newNameSink: Observer[String], header: VDomModifier, body: VDomModifier = VDomModifier.empty, placeholderMessage: Option[String] = None, onClose: () => Boolean = () => true)(implicit ctx: Ctx.Owner) = {
    UI.ModalConfig(
      header = header,
      description = VDomModifier(
        SharedViewElements.inputRow(
          state = state,
          submitAction = { str =>
            state.uiModalClose.onNext(())
            newNameSink.onNext(str)
          },
          autoFocus = true,
          placeHolderMessage = placeholderMessage,
          allowEmptyString = true,
          submitIcon = freeSolid.faPlus,
          showSubmitIcon = true,
          textAreaModifiers = VDomModifier(
            color.white,
            backgroundColor := "rgba(0, 0, 0, 0.6)"

          )
        ),

        body
      ),
      modalModifier = VDomModifier(
        cls := "basic",
        minWidth := "320px",
        maxWidth := "400px"
      ),
      onClose = onClose
    )
  }

  def onClickNewNamePrompt(state: GlobalState, header: VDomModifier, body: VDomModifier = VDomModifier.empty, placeholderMessage: Option[String] = None) = EmitterBuilder.ofModifier[String] { sink =>
    VDomModifier(
      onClick.stopPropagation(Ownable { implicit ctx => newNamePromptModalConfig(state, sink, header, body, placeholderMessage) }) --> state.uiModalConfig,
      cursor.pointer
    )
  }
}
