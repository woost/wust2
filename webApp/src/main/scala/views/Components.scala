package wust.webApp.views

import cats.effect.IO
import fomanticui.{SearchOptions, SearchSourceEntry, ToastOptions}
import fontAwesome._
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.HTMLElement
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.{AttributeBuilder, EmitterBuilder}
import rx._
import jquery.JQuerySelection
import wust.api.UploadedFile
import wust.css.{Styles, ZIndex}
import wust.graph._
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import wust.util.StringOps._
import wust.util._
import wust.webApp.{BrowserDetect, Client, Ownable, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem, DragPayload, DragTarget}
import wust.webApp.jsdom.{FileReaderOps, IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, PageChange, UploadingFile, View}
import wust.webApp.views.Elements._
import wust.webApp.views.UI.ModalConfig

import scala.collection.breakOut
import scala.scalajs.js

// This file contains woost-related UI helpers.

object Placeholders {
  val newNode: Attr = placeholder := "Create new post. Press Enter to submit."
  val newTag: Attr = placeholder := "Create new tag. Press Enter to submit."
}

object Components {
  private val implicitUserName = "Unregistered User"

  val woostTeamEmailLink = a(href := "mailto:team@woost.space", "team@woost.space", cursor.pointer)

  def displayUserName(user: NodeData.User): String = {
    if(user.isImplicit) {
      if (user.name.contains(" ")) { //hack for showing invite user by email with their email address
        val email = user.name.split(" ")(0)
        s"$email (unregistered)"
      } else implicitUserName
    } else user.name
  }

  val htmlNodeData: NodeData => String = {
    case NodeData.Markdown(content)  => markdownString(content)
    case NodeData.PlainText(content) => escapeHtml(content)
    case user: NodeData.User         => s"User: ${ escapeHtml(displayUserName(user)) }"
    case file: NodeData.File         => s"File: ${ escapeHtml(file.key) }"
  }

  def renderNodeData(nodeData: NodeData, maxLength: Option[Int] = None): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => div(trimToMaxLength(file.str, maxLength))
  }

  def renderNodeDataWithFile(state: GlobalState, nodeId: NodeId, nodeData: NodeData, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => renderUploadedFile(state, nodeId,file)
  }

  def renderUploadedFile(state: GlobalState, nodeId: NodeId, file: NodeData.File)(implicit ctx: Ctx.Owner): VNode = {
    import file._

    val maxImageHeight = "250px"

    def downloadUrl(attr: AttributeBuilder[String, VDomModifier]): VDomModifier = state.fileDownloadBaseUrl.map(_.map(baseUrl => attr := baseUrl + "/" + key))
    def preview(dataUrl: String): VDomModifier = {
      file.contentType match {
        case t if t.startsWith("image/") => img(height := maxImageHeight, src := dataUrl)
        case _                           => VDomModifier(height := "150px", width := "300px")
      }
    }
    def centerStyle = VDomModifier(
      Styles.flex,
      Styles.flexStatic,
      alignItems.center,
      flexDirection.column,
      justifyContent.spaceEvenly
    )
    def overlay = VDomModifier(
      background := "rgba(255, 255, 255, 0.8)",
      position.absolute,
      Styles.growFull
    )

    def downloadLink = a(downloadUrl(href), "Download")

    div(
      file.description.nonEmpty.ifTrue[VDomModifier](b(file.description)),
      if (file.key.isEmpty) { // this only happens for currently-uploading files
        VDomModifier(Rx {
          val uploadingFiles = state.uploadingFiles()
          uploadingFiles.get(nodeId) match {
            case Some(UploadingFile.Error(dataUrl, retry)) => div(
              preview(dataUrl),
              position.relative,
              centerStyle,
              div(
                overlay,
                centerStyle,
                div(freeSolid.faExclamationTriangle, " Error Uploading File"),
                button(cls := "ui button", "Retry upload", onClick.stopPropagation.foreach { retry.runAsyncAndForget }, cursor.pointer)
              )
            )
            case Some(UploadingFile.Waiting(dataUrl)) => div(
              preview(dataUrl),
              position.relative,
              centerStyle,
              woostLoadingAnimation.apply(overlay, centerStyle)
            )
            case None => VDomModifier.empty
          }
        })
      } else VDomModifier(
        p(downloadLink),
        contentType match {
          case t if t.startsWith("image/") =>
            val image = img(alt := fileName, downloadUrl(src), cls := "ui image")
            image(maxHeight := maxImageHeight, cursor.pointer, onClick.stopPropagation.foreach {
              state.modalConfig.onNext(Ownable(_ => ModalConfig(description, image(cls := "fluid"), modalModifier = cls := "basic"))) //TODO: better size settings
              ()
            })
          case "application/pdf"           =>
            val embeddedPdf = htmlTag("object")(downloadUrl(data), dsl.tpe := "application/pdf")
            embeddedPdf(maxHeight := maxImageHeight, width := "100%")
          case _                           => VDomModifier.empty
        }
      )
    )
  }

  private val woostPathCurve = "m51.843 221.96c81.204 0-6.6913-63.86 18.402 13.37 25.093 77.23 58.666-26.098-7.029 21.633-65.695 47.73 42.949 47.73-22.746 0-65.695-47.731-32.122 55.597-7.029-21.633 25.093-77.23-62.802-13.37 18.402-13.37z"
  val woostIcon = {
    import svg._
    svg.static(keyValue)(VDomModifier(
      cls := "svg-inline--fa fa-w-14",
      viewBox := "0 0 10 10",
      g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
        path(d := woostPathCurve, fill := "currentColor")
      )
    ))
  }


  val woostLoadingAnimation: VNode = {
    import svg._
    div(
      svg(
        width := "100px", height := "100px", viewBox := "0 0 10 10",
        g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
          path(cls := "woost-loading-animation-logo", d := woostPathCurve, fill := "none", stroke := "#6636b7", strokeLineCap := "round", strokeWidth := "3.5865", pathLength := "100")
        )
      ),
      p("Loading", dsl.color := "rgba(0,0,0,0.4)", textAlign.center)
    )
  }

  // FIXME: Ensure unique DM node that may be renamed.
  def onClickDirectMessage(state:GlobalState, user:Node.User): VDomModifier = {
    (state.user.now.id != user.id).ifTrue[VDomModifier]({
      val dmName = IndexedSeq[String](displayUserName(state.user.now.toNode.data), displayUserName(user.data)).sorted.mkString(", ")
      VDomModifier(
        onClick.foreach{
          val graph = state.graph.now
          val previousDmNode = graph.chronologicalNodesAscending.find(n => n.str == dmName && graph.pinnedNodeIdx.contains(graph.idToIdx(user.id))(graph.idToIdx(n.id))) // Max 1 dm node with this name
          previousDmNode match {
            case Some(dmNode) if graph.can_access_node(user.id, dmNode.id) =>
              state.viewConfig() = state.viewConfig.now.focusView(Page(dmNode.id), View.Chat, needsGet = false)
            case _ => // create a new channel, add user as member
              val nodeId = NodeId.fresh
              state.eventProcessor.changes.onNext(GraphChanges.newChannel(nodeId, state.user.now.id, title = dmName))
              state.viewConfig() = state.viewConfig.now.focusView(Page(nodeId), View.Chat, needsGet = false)
              //TODO: this is a hack. Actually we need to wait until the new channel was added successfully
              dom.window.setTimeout({() =>
                Client.api.addMember(nodeId, user.id, AccessLevel.ReadWrite)
                val change:GraphChanges = GraphChanges.from(addEdges = Set(Edge.Invite(user.id, nodeId)))
                state.eventProcessor.changes.onNext(change)
              }, 3000)
              ()
          }
        },
        cursor.pointer,
        UI.popup := s"Start Conversation with ${displayUserName(user.data)}"
      )
    })
  }

  def woostLoadingAnimationWithFadeIn = woostLoadingAnimation(cls := "animated-fadein")

  def customLoadingAnimation(state: GlobalState)(implicit data: Ctx.Data): VNode = {
    div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, cls := "animated-fadein", woostLoadingAnimation)
  }

  def withLoadingAnimation(state: GlobalState)(renderFn: => VDomModifier)(implicit data: Ctx.Data): VDomModifier = {
    if(state.isLoading()) div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, woostLoadingAnimationWithFadeIn)
    else renderFn
  }

  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier, pageOnClick: Boolean): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      if(pageOnClick) onClick foreach { e =>
        state.page() = Page(tag.id)
        e.stopPropagation()
      } else cursor.default,
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTagDot(state: GlobalState, tag: Node): VNode = {
    span(
      cls := "node tagdot",
      backgroundColor := tagColor(tag.id).toHex,
      UI.tooltip := tag.data.str,
      onClick foreach { e =>
        state.page() = Page(tag.id)
        e.stopPropagation()
      },
      draggableAs(DragItem.Tag(tag.id)),
      dragTarget(DragItem.Tag(tag.id)),
      cls := "drag-feedback"
    )
  }

  def nodeTag(state: GlobalState, tag: Node, pageOnClick: Boolean = true): VNode = {
    val contentString = trimToMaxLength(tag.data.str, 20)
    renderNodeTag(state, tag, contentString, pageOnClick)
  }

  def editableNodeTag(state: GlobalState, tag: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = Some(20))(implicit ctx: Ctx.Owner): VNode = {
    renderNodeTag(state, tag, editableNode(state, tag, editMode, submit, maxLength), pageOnClick = false)
  }

  def removableNodeTagCustom(state: GlobalState, tag: Node, action: () => Unit): VNode = {
    nodeTag(state, tag, pageOnClick = false)(
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation foreach {
          action()
        },
      )
    )
  }

  def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId): VNode = {
    removableNodeTagCustom(state, tag, () => {
      // when removing last parent, fall one level lower into the still existing grandparents
      //TODO: move to GraphChange factory
      // val removingLastParent = graph.parents(taggedNodeId).size == 1
      // val addedGrandParents: scala.collection.Set[Edge] =
      //   if (removingLastParent)
      //     graph.parents(tag.id).map(Edge.Parent(taggedNodeId, _))
      //   else
      //     Set.empty
      state.eventProcessor.changes.onNext(
        GraphChanges.delete(taggedNodeId, Set(tag.id))
      )
    })
  }

  def renderNodeCard(node: Node, contentInject: VDomModifier): VNode = {
    div(
      keyed(node.id),
      cls := "node nodecard",
      div(
        cls := "nodecard-content",
        contentInject
      ),
    )
  }
  def nodeCard(node: Node, contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(renderNodeData(node.data, maxLength), contentInject)
    )
  }
  def nodeCardWithFile(state: GlobalState, node: Node, contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(renderNodeDataWithFile(state, node.id, node.data, maxLength), contentInject)
    )
  }
  def nodeCardWithoutRender(node: Node, contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(p(StringOps.trimToMaxLength(node.str, maxLength)), contentInject)
    )
  }
  def nodeCardEditable(state: GlobalState, node: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(
      node,
      contentInject = VDomModifier(
        prependInject,
        editableNode(state, node, editMode, submit, maxLength),
        contentInject
      ),
    ).apply(
      Rx { editMode().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },
    )
  }

  def taskCheckbox(state:GlobalState, node:Node, directParentIds:Iterable[NodeId])(implicit ctx: Ctx.Owner):VNode = {
    val isChecked:Rx[Boolean] = Rx {
      val graph = state.graph()
      val nodeIdx = graph.idToIdx(node.id)
      @inline def nodeIsDoneInParent(parentId:NodeId) = {
        val parentIdx = graph.idToIdx(parentId)
        val workspaces = graph.workspacesForParent(parentIdx)
        graph.isDoneInAllWorkspaces(nodeIdx, workspaces)
      }
      val parentIdsWithDone = directParentIds.filter(parentId => graph.nodesById(parentId).role != NodeRole.Stage)
      parentIdsWithDone.nonEmpty && parentIdsWithDone.forall(nodeIsDoneInParent)
    }

    div(
      cls := "ui checkbox fitted",
      marginTop := "5px",
      marginLeft := "5px",
      marginRight := "3px",
      input(
        tpe := "checkbox",
        checked <-- isChecked,
        onChange.checked foreach { checking =>
          val graph = state.graph.now
          directParentIds.flatMap(id => graph.workspacesForParent(graph.idToIdx(id))).foreach { workspaceIdx =>
            val doneIdx = graph.doneNodeForWorkspace(workspaceIdx)

            if(checking) {
              val (doneNodeId, doneNodeAddChange) = doneIdx match {
                case None                   =>
                  val freshDoneNode = Node.MarkdownStage(Graph.doneText)
                  val expand = GraphChanges.connect(Edge.Expanded)(state.user.now.id, freshDoneNode.id)
                  (freshDoneNode.id, GraphChanges.addNodeWithParent(freshDoneNode, graph.nodeIds(workspaceIdx)) merge expand)
                case Some(existingDoneNode) => (graph.nodeIds(existingDoneNode), GraphChanges.empty)
              }
              val stageParents = graph.notDeletedParentsIdx(graph.idToIdx(node.id)).collect{case idx if graph.nodes(idx).role == NodeRole.Stage => graph.nodeIds(idx)}
              val changes = doneNodeAddChange merge GraphChanges.changeTarget(Edge.Parent)(node.id::Nil, stageParents,doneNodeId::Nil)
              state.eventProcessor.changes.onNext(changes)
            } else { // unchecking
              // since it was checked, we know for sure, that a done-node for every workspace exists
              val changes = GraphChanges.disconnect(Edge.Parent)(node.id, doneIdx.map(graph.nodeIds))
              state.eventProcessor.changes.onNext(changes)
            }
          }

        }
      ),
      label()
    )
  }

  def nodeCardWithCheckbox(state:GlobalState, node: Node, directParentIds:Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode = {
    nodeCard(node).prepend(
      Styles.flex,
      alignItems.flexStart,
      taskCheckbox(state, node, directParentIds)
    )
  }

  def readDragDisableSort(elem: dom.html.Element): Option[Boolean] = {
    readPropertyFromElement[Boolean](elem, DragItem.disableSortPropName)
  }

  def writeDragDisableSort(elem: dom.html.Element, disableSort: => Boolean): Unit = {
    writePropertyIntoElement(elem, DragItem.disableSortPropName, disableSort)
  }

  def readDragTarget(elem: dom.html.Element): Option[DragTarget] = {
    readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
  }

  def writeDragTarget(elem: dom.html.Element, dragTarget: => DragTarget): Unit = {
    writePropertyIntoElement(elem, DragItem.targetPropName, dragTarget)
  }

  def readDragPayload(elem: dom.html.Element): Option[DragPayload] = {
    readPropertyFromElement[DragPayload](elem, DragItem.payloadPropName)
  }

  def writeDragPayload(elem: dom.html.Element, dragPayload: => DragPayload): Unit = {
    writePropertyIntoElement(elem, DragItem.payloadPropName, dragPayload)
  }

  def readDragContainer(elem: dom.html.Element): Option[DragContainer] = {
    readPropertyFromElement[DragContainer](elem, DragContainer.propName)
  }

  def writeDragContainer(elem: dom.html.Element, dragContainer: => DragContainer): Unit = {
    writePropertyIntoElement(elem, DragContainer.propName, dragContainer)
  }

  def readDraggableDraggedAction(elem: dom.html.Element): Option[() => Unit] = {
    readPropertyFromElement[() => Unit](elem, DragItem.draggedActionPropName)
  }

  def writeDraggableDraggedAction(elem: dom.html.Element, action: => () => Unit): Unit = {
    writePropertyIntoElement(elem, DragItem.draggedActionPropName, action)
  }


  def sortableAs(payload: => DragPayload): VDomModifier = {
    VDomModifier(
      cls := "draggable", // makes this element discoverable for the Draggable library
      onDomMount.asHtml foreach { elem =>
        writeDragPayload(elem, payload)
      }
    )
  }

  def draggableAs(payload: => DragPayload): VDomModifier = {
    VDomModifier(
      sortableAs(payload),
      onDomMount.asHtml foreach { elem =>
        writeDragDisableSort(elem, true)
      }
    )
  }

  def dragTarget(dragTarget: DragTarget): VDomModifier = {
    onDomMount.asHtml foreach { elem =>
      writeDragTarget(elem, dragTarget)
    }
  }

  val dragDisabled: VDomModifier = {
    VDomModifier(
      cls := "draggable", // makes this element discoverable for the Draggable library
      onDomMount.asHtml foreach { elem =>
        writeDragPayload(elem, DragItem.DisableDrag)
      }
    )
  }

  def dragTargetOnly(dragTarget: DragTarget): VDomModifier = {
    VDomModifier(
      cursor.auto, // overwrites cursor set by .draggable class
      dragDisabled,
      onDomMount.asHtml foreach { elem =>
        writeDragTarget(elem, dragTarget)
      }
    )
  }

  def onDraggableDragged: EmitterBuilder[Unit, VDomModifier] =
    EmitterBuilder.ofModifier[Unit] { sink =>
      IO {
        VDomModifier(
          onDomMount.asHtml foreach { elem =>
            writeDraggableDraggedAction(elem, () => sink.onNext(Unit))
          }
        )
      }
    }


  def registerDraggableContainer(state: GlobalState): VDomModifier = {
    VDomModifier(
      //    border := "2px solid blue",
      outline := "none", // hides focus outline
      cls := "sortable-container",
      managedElement.asHtml { elem =>
        state.sortable.addContainer(elem)
        Cancelable { () => state.sortable.removeContainer(elem) }
      }
    )
  }

  def registerSortableContainer(state: GlobalState, container: DragContainer): VDomModifier = {
    VDomModifier(
      //          border := "2px solid violet",
      outline := "none", // hides focus outline
      cls := "sortable-container",
      managedElement.asHtml { elem =>
        writeDragContainer(elem, container)
        state.sortable.addContainer(elem)
        Cancelable { () => state.sortable.removeContainer(elem) }
      }
    )
  }

  def editableNodeOnClick(state: GlobalState, node: Node, submit: Observer[GraphChanges])(
    implicit ctx: Ctx.Owner
  ): VNode = {
    val editMode = Var(false)
    editableNode(state, node, editMode, submit)(ctx)(
      onClick.stopPropagation.stopImmediatePropagation foreach {
        if(!editMode.now) {
          editMode() = true
        }
      }
    )
  }


  def editableNode(state: GlobalState, node: Node, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int] = None)(
    implicit ctx: Ctx.Owner
  ): VNode = {
    node match {
      case contentNode: Node.Content => editableNodeContent(state, contentNode, editMode, submit, maxLength)
      case _                         => renderNodeDataWithFile(state, node.id, node.data, maxLength)
    }
  }

  def editableNodeContent(state: GlobalState, node: Node.Content, editMode: Var[Boolean], submit: Observer[GraphChanges], maxLength: Option[Int])(
    implicit ctx: Ctx.Owner
  ): VNode = {

    val initialRender: Var[VDomModifier] = Var(renderNodeDataWithFile(state, node.id, node.data, maxLength))

    def save(contentEditable:HTMLElement): Unit = {
      if(editMode.now) {
        val text = contentEditable.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String] // textContent would remove line-breaks in firefox
        if (text.nonEmpty) {
          node.data.updateStr(text) match {
            case Some(newData) =>
              val updatedNode = node.copy(data = newData)

              Var.set(
                initialRender -> renderNodeDataWithFile(state, updatedNode.id, updatedNode.data, maxLength),
                editMode -> false
              )

              val changes = GraphChanges.addNode(updatedNode)
              submit.onNext(changes)
            case None =>
              editMode() = false
          }
        }
      }
    }

    import scala.concurrent.duration._

    p( // has different line-height than div and is used for text by markdown
      outline := "none", // hides contenteditable outline
      keyed, // when updates come in, don't disturb current editing session
      Rx {
        if(editMode()) VDomModifier(
          node.data.str, // Markdown source code
          contentEditable := true,
          cls := "enable-text-selection", // fix for macos safari (contenteditable should already be selectable, but safari seems to have troube with interpreting `:not(input):not(textarea):not([contenteditable=true])`)
          whiteSpace.preWrap, // preserve white space in Markdown code
          backgroundColor := "#FFF",
          color := "#000",
          cursor.auto,

          onFocus foreach { e => document.execCommand("selectAll", false, null) },
          onBlur.transform(_.delayOnNext(200 millis)) foreach { e => save(e.target.asInstanceOf[HTMLElement]) }, // we delay the blur event, because otherwise in chrome it will trigger Before the onEscape, and we want onEscape to trigger frist.
          BrowserDetect.isMobile.ifFalse[VDomModifier](VDomModifier(
            onEnter foreach { e => save(e.target.asInstanceOf[HTMLElement]) },
            onEscape foreach { editMode() = false }
            //TODO how to revert back if you wrongly edited something on mobile?
          )),
          onClick.stopPropagation foreach {} // prevent e.g. selecting node, but only when editing
        ) else initialRender()
      },
      onDomUpdate.asHtml --> inNextAnimationFrame { node =>
        if(editMode.now) node.focus()
      },
    )
  }

  def searchInGraph(graph: Rx[Graph], placeholder: String, valid: Rx[Boolean] = Var(true), filter: Node => Boolean = _ => true, showParents: Boolean = true, completeOnInit: Boolean = true, inputModifiers: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => IO {
    var elem: JQuerySelection = null
    div(
      keyed,
      cls := "ui category search",
      div(
        cls := "ui icon input",
        input(
          inputModifiers,
          cls := "prompt",
          tpe := "text",
          dsl.placeholder := placeholder,

          onFocus.foreach { _ =>
            elem.search(arg = new SearchOptions {
              `type` = if (showParents) "category" else js.undefined

              cache = false
              searchOnFocus = true
              minCharacters = 0

              source = graph.now.nodes.collect { case node: Node if filter(node) =>
                val cat: js.UndefOr[String] = if (showParents) {
                  val parents = graph.now.parentsIdx(graph.now.idToIdx(node.id))
                  if(parents.isEmpty) "-" else trimToMaxLength(parents.map(i => graph.now.nodes(i).str).mkString(","), 18)
                } else js.undefined

                val str = node match {
                  case user: Node.User => Components.displayUserName(user.data)
                  case _ => node.str
                }

                new SearchSourceEntry {
                  title = str
                  category = cat
                  data = js.Dynamic.literal(id = node.id.asInstanceOf[js.Any])
                }
              }(breakOut): js.Array[SearchSourceEntry]

              searchFields = js.Array("title")

              onSelect = { (selected, results) =>
                val id = selected.asInstanceOf[js.Dynamic].data.id.asInstanceOf[NodeId]
                sink.onNext(id)
                elem.search("set value", "")
                true
              }: js.Function2[SearchSourceEntry, js.Array[SearchSourceEntry], Boolean]
            })


            if (completeOnInit) elem.search("search local", "")
          },

          valid.map(_.ifFalse[VDomModifier](borderColor := "tomato"))
        ),
        i(cls := "search icon"),
      ),
      div(cls := "results"),

      onDomMount.asJquery.foreach { e =>
        elem = e
      }
    )
  })

  def uploadField(state: GlobalState, selected: Var[Option[AWS.UploadableFile]])(implicit ctx: Ctx.Owner): VDomModifier = {

    val iconAndPopup = selected.map {
      case None =>
        (fontawesome.icon(Icons.fileUpload), div("Upload your own file!"))
      case Some(selected) =>
        val popupNode = selected.file.`type` match {
          case t if t.startsWith("image/") => img(src := selected.dataUrl, height := "100px", maxWidth := "400px") //TODO: proper scaling and size restriction
          case _ => div(selected.file.name)
        }
        val icon = fontawesome.layered(
          fontawesome.icon(Icons.fileUpload),
          fontawesome.icon(
            freeSolid.faPaperclip,
            new Params {
              transform = new Transform {size = 20.0; x = 7.0; y = 7.0; }
              styles = scalajs.js.Dictionary[String]("color" -> "orange")
            }
          )
        )

        (icon, popupNode)
    }

    div(
      padding := "3px",
      input(display.none, tpe := "file", id := "upload-file-field",
        onChange.foreach { e =>
          val inputElement = e.currentTarget.asInstanceOf[dom.html.Input]
          if (inputElement.files.length > 0) selected() = AWS.upload(state, inputElement.files(0))
          else selected() = None
        }
      ),
      label(
        forId := "upload-file-field", // label for input will trigger input element on click.
        iconAndPopup.map { case (icon, popup) =>
          VDomModifier(
            UI.popupHtml("top left") := popup,
            icon
          )
        },
        margin := "0px",
        Styles.flexStatic,
        cls := "ui circular icon button",
        fontSize := "1.1rem",
        backgroundColor := "steelblue",
        color := "white",
      )
    )
  }
}

