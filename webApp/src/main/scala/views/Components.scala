package wust.webApp.views

import cats.effect.IO
import fomanticui.{SearchOptions, SearchSourceEntry, ToastOptions}
import fontAwesome._
import googleAnalytics.Analytics
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
import wust.ids.NodeData.EditableText
import wust.ids.{NodeData, _}
import wust.sdk.NodeColor._
import wust.util.StringOps._
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop._
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

  val woostTeamEmailLink = 
    VDomModifier(
      cls := "enable-text-selection",
      a(href := "mailto:team@woost.space", "team@woost.space", target := "_blank")
    )

  def displayUserName(user: NodeData.User): String = {
    if(user.isImplicit) {
      //hack for showing invite user by email with their email address. new implicit user do not have a name, just if they are invited. but old implicit users are named "unregisted-user-$id"
      if (user.name.nonEmpty && !user.name.startsWith("unregistered-user-")) {
        val prefixName = user.name.split(" ").head // old invite users have a space with an id as postfix behind their name, remove it.
        s"${prefixName} (unregistered)"
      } else implicitUserName
    } else user.name
  }

  val htmlNodeData: NodeData => String = {
    case NodeData.Markdown(content)  => markdownString(content)
    case NodeData.PlainText(content) => escapeHtml(content)
    case user: NodeData.User         => s"User: ${ escapeHtml(displayUserName(user)) }"
    case file: NodeData.File         => s"File: ${ escapeHtml(file.key) }"
    case d: NodeData.Integer         => d.str
    case d: NodeData.Decimal         => d.str
    case d: NodeData.Date            => d.str
  }

  def renderNodeData(nodeData: NodeData, maxLength: Option[Int] = None): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => div(trimToMaxLength(file.str, maxLength))
    case d: NodeData.Integer         => div(trimToMaxLength(d.str, maxLength))
    case d: NodeData.Decimal         => div(trimToMaxLength(d.str, maxLength))
    case d: NodeData.Date            => div(trimToMaxLength(d.str, maxLength))
  }

  def renderNodeDataWithFile(state: GlobalState, nodeId: NodeId, nodeData: NodeData, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => renderUploadedFile(state, nodeId,file)
    case d: NodeData.Integer         => div(trimToMaxLength(d.str, maxLength))
    case d: NodeData.Decimal         => div(trimToMaxLength(d.str, maxLength))
    case d: NodeData.Date            => div(trimToMaxLength(d.str, maxLength))
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

    def downloadLink = a(downloadUrl(href), s"Download ${StringOps.trimToMaxLength(file.fileName, 20)}")

    div(
      file.str,
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
    div(
      {
        import svg._
        svg(
          width := "100px", height := "100px", viewBox := "0 0 10 10",
          g(transform := "matrix(.096584 0 0 .096584 -.0071925 -18.66)",
            path(cls := "woost-loading-animation-logo", d := woostPathCurve, fill := "none", stroke := "#6636b7", strokeLineCap := "round", strokeWidth := "3.5865", pathLength := "100")
            )
          )
      },
      p("LOADING", marginTop := "16px", dsl.color := "rgba(0,0,0,0.6)", textAlign.center, letterSpacing := "0.05em", fontWeight := 700, fontSize := "15px")
    )
  }

  // FIXME: Ensure unique DM node that may be renamed.
  def onClickDirectMessage(state: GlobalState, dmUser: Node.User): VDomModifier = {
    val user = state.user.now
    val userId = user.id
    val dmUserId = dmUser.id
    (userId != dmUserId).ifTrue[VDomModifier]({
      val dmName = IndexedSeq[String](displayUserName(user.toNode.data), displayUserName(dmUser.data)).sorted.mkString(", ")
      VDomModifier(
        onClick.foreach{
          val graph = state.graph.now
          val previousDmNode: Option[Node] = {
            val userIdx = graph.idToIdx(userId)
            graph.chronologicalNodesAscending.find{ n =>
              n.str == dmName && graph.isPinned(graph.idToIdx(n.id), userIdx)
            }
          } // Max 1 dm node with this name
          previousDmNode match {
            case Some(dmNode) if graph.can_access_node(user.id, dmNode.id) =>
              state.viewConfig() = state.viewConfig.now.focusView(Page(dmNode.id), View.Conversation, needsGet = false)
            case _ => // create a new channel, add user as member
              val nodeId = NodeId.fresh
              state.eventProcessor.changes.onNext(GraphChanges.newProject(nodeId, userId, title = dmName) merge GraphChanges.notify(nodeId, userId)) // TODO: noderole message
              state.viewConfig() = state.viewConfig.now.focusView(Page(nodeId), View.Conversation, needsGet = false)
              //TODO: this is a hack. Actually we need to wait until the new channel was added successfully
              dom.window.setTimeout({() =>
                Client.api.addMember(nodeId, dmUserId, AccessLevel.ReadWrite)
                val change:GraphChanges = GraphChanges.from(addEdges = Set(Edge.Invite(dmUserId, nodeId)))
                state.eventProcessor.changes.onNext(change)
              }, 3000)
              ()
          }
        },
        cursor.pointer,
        UI.popup := s"Start Conversation with ${displayUserName(dmUser.data)}"
      )
    })
  }

  def woostLoadingAnimationWithFadeIn = woostLoadingAnimation(cls := "animated-fadein")

  def spaceFillingLoadingAnimation(state: GlobalState)(implicit data: Ctx.Data): VNode = {
    div(Styles.flex, alignItems.center, justifyContent.center, Styles.growFull, woostLoadingAnimationWithFadeIn)
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
        state.viewConfig.update(_.focus(Page(tag.id)))
        e.stopPropagation()
      } else cursor.default,
    )
  }

  def propertyTag(
    state: GlobalState,
    key: Edge.LabeledProperty,
    property: Node,
    pageOnClick: Boolean = false,
    dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId)),
  ): VNode = {

    val icon = ItemProperties.iconByNodeData(property.data)
    val contentString = VDomModifier(
      Elements.icon(icon)(marginRight := "5px"),
      s"${key.data.key}: ${property.data.str}"
    )

    span(
      cls := "node tag",
      contentString,
      backgroundColor := tagColor(property.id).toHex,
    )
  }

  def removablePropertyTagCustom(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, action: () => Unit, pageOnClick: Boolean = false): VNode = {
    propertyTag(state, key, propertyNode, pageOnClick)(
      span(
        "×",
        cls := "actionbutton",
        onClick.stopPropagation foreach {
          action()
        },
      )
    )
  }

  def removablePropertyTag(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, pageOnClick:Boolean = false): VNode = {
    removablePropertyTagCustom(state, key, propertyNode, () => {
      state.eventProcessor.changes.onNext(
        GraphChanges(delEdges = Set(key))
      )
    }, pageOnClick)
  }


    def nodeTagDot(state: GlobalState, tag: Node, pageOnClick:Boolean = false): VNode = {
      span(
        cls := "node tagdot",
        backgroundColor := tagColor(tag.id).toHex,
        UI.tooltip := tag.data.str,
        if(pageOnClick) onClick foreach { e =>
          state.viewConfig.update(_.focus(Page(tag.id)))
          e.stopPropagation()
        } else cursor.default,
        drag(DragItem.Tag(tag.id)),
      )
    }

    def checkboxNodeTag(
      state: GlobalState,
      tagNode: Node,
      pageOnClick: Boolean = false,
      dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId)),
    )(implicit ctx: Ctx.Owner): VNode = {

      div( // checkbox and nodetag are both inline elements because of fomanticui
        div(
          verticalAlign.middle,
          cls := "ui checkbox",
          ViewFilter.addFilterCheckbox(
            state,
            tagNode.str, // TODO: renderNodeData
            GraphOperation.OnlyTaggedWith(tagNode.id)
          ),
          label(), // needed for fomanticui
        ),
        nodeTag(state, tagNode, pageOnClick, dragOptions)(verticalAlign.middle),
      )
    }

    def nodeTag(
      state: GlobalState,
      tag: Node,
      pageOnClick: Boolean = false,
      dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId)),
    ): VNode = {
      val contentString = renderNodeData(tag.data, maxLength = Some(20))
      renderNodeTag(state, tag, VDomModifier(contentString, dragOptions(tag.id)), pageOnClick)
    }

    def removableNodeTagCustom(state: GlobalState, tag: Node, action: () => Unit, pageOnClick:Boolean = false): VNode = {
      nodeTag(state, tag, pageOnClick)(
        span(
          "×",
          cls := "actionbutton",
          onClick.stopPropagation foreach {
            action()
          },
        )
      )
    }

    def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId, pageOnClick:Boolean = false): VNode = {
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
          GraphChanges.disconnect(Edge.Parent)(taggedNodeId, Set(tag.id))
        )
      }, pageOnClick)
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
    def nodeCardEditable(state: GlobalState, node: Node, editMode: Var[Boolean], contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = VDomModifier(
          prependInject,
          editableNode(state, node, editMode, maxLength),
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
        val parentIdsWithDone = directParentIds.filter{ parentId =>
          val role = graph.nodesById(parentId).role
          role != NodeRole.Stage && role != NodeRole.Tag
        }
        parentIdsWithDone.nonEmpty && parentIdsWithDone.forall(nodeIsDoneInParent)
      }

      div(
        cls := "ui checkbox fitted",
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

    def readDragTarget(elem: dom.html.Element): js.UndefOr[DragTarget] = {
      readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
    }

    def writeDragTarget(elem: dom.html.Element, dragTarget: => DragTarget): Unit = {
      writePropertyIntoElement(elem, DragItem.targetPropName, dragTarget)
    }

    def readDragPayload(elem: dom.html.Element): js.UndefOr[DragPayload] = {
      readPropertyFromElement[DragPayload](elem, DragItem.payloadPropName)
    }

    def writeDragPayload(elem: dom.html.Element, dragPayload: => DragPayload): Unit = {
      writePropertyIntoElement(elem, DragItem.payloadPropName, dragPayload)
    }

    def readDragContainer(elem: dom.html.Element): js.UndefOr[DragContainer] = {
      readPropertyFromElement[DragContainer](elem, DragContainer.propName)
    }

    def writeDragContainer(elem: dom.html.Element, dragContainer: => DragContainer): Unit = {
      writePropertyIntoElement(elem, DragContainer.propName, dragContainer)
    }

    def readDraggableDraggedAction(elem: dom.html.Element): js.UndefOr[() => Unit] = {
      readPropertyFromElement[() => Unit](elem, DragItem.draggedActionPropName)
    }

    def writeDraggableDraggedAction(elem: dom.html.Element, action: => () => Unit): Unit = {
      writePropertyIntoElement(elem, DragItem.draggedActionPropName, action)
    }

    def dragWithHandle(item: DragPayloadAndTarget):VDomModifier = dragWithHandle(item,item)
    def dragWithHandle(
      payload: => DragPayload = DragItem.DisableDrag,
      target: DragTarget = DragItem.DisableDrag,
    ): VDomModifier = {
      VDomModifier(
        cls := "draggable", // makes this element discoverable for the Draggable library
        cls := "drag-feedback", // visual feedback for drag-start
        VDomModifier.ifTrue(payload.isInstanceOf[DragItem.DisableDrag.type])(cursor.auto), // overwrites cursor set by .draggable class
        onDomMount.asHtml foreach { elem =>
          writeDragPayload(elem, payload)
          writeDragTarget(elem, target)
        }
      )
    }
    def drag(item: DragPayloadAndTarget):VDomModifier = drag(item,item)
    def drag(
      payload: => DragPayload = DragItem.DisableDrag,
      target: DragTarget = DragItem.DisableDrag,
    ): VDomModifier = {
      VDomModifier(dragWithHandle(payload, target), cls := "draghandle")
    }

    def registerDragContainer(state: GlobalState, container: DragContainer = DragContainer.Default): VDomModifier = {
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

    def onAfterPayloadWasDragged: EmitterBuilder[Unit, VDomModifier] =
      EmitterBuilder.ofModifier[Unit] { sink =>
        IO {
          VDomModifier(
            onDomMount.asHtml foreach { elem =>
              writeDraggableDraggedAction(elem, () => sink.onNext(Unit))
            }
          )
        }
      }

    def nodeAvatar(node: Node, size: Int): VNode = {
      Avatar(node)(
        width := s"${ size }px",
        height := s"${ size }px"
      )
    }

    def editableNodeOnClick(state: GlobalState, node: Node, maxLength: Option[Int] = None)(
      implicit ctx: Ctx.Owner
    ): VNode = {
      val editMode = Var(false)
      editableNode(state, node, editMode, maxLength)(ctx)(
        onClick.stopPropagation.stopImmediatePropagation foreach {
          if(!editMode.now) {
            editMode() = true
          }
        }
      )
    }

    def editableNode(state: GlobalState, node: Node, editMode: Var[Boolean], maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
      //TODO: this validation code whether a node is writeable should be shared with the backend.

      val initialRender: Var[VNode] = Var(renderNodeDataWithFile(state, node.id, node.data, maxLength))

      node match {

        case node@Node.Content(_, textData: NodeData.EditableText, _, _) =>
          val editRender = editableTextNode(state, editMode, initialRender, maxLength, node.data.str, str => textData.updateStr(str).map(data => node.copy(data = data)))
          editableNodeContent(state, node, editMode, initialRender, editRender)

        //TODO: integer, floating point, etc.

        case user: Node.User if !user.data.isImplicit && user.id == state.user.now.id =>
          val editRender = editableTextNode(state, editMode, initialRender, maxLength, user.data.name, str => user.data.updateName(str).map(data => user.copy(data = data)))
          editableNodeContent(state, user, editMode, initialRender, editRender)

        case _ => initialRender.now
      }
    }

    def editableTextNode(state: GlobalState, editMode: Var[Boolean], initialRender: Var[VNode], maxLength: Option[Int], nodeStr: String, applyStringToNode: String => Option[Node])(implicit ctx: Ctx.Owner): VDomModifier = {
      import scala.concurrent.duration._

      def save(contentEditable:HTMLElement): Unit = {
        if(editMode.now) {
          val text = contentEditable.asInstanceOf[js.Dynamic].innerText.asInstanceOf[String] // textContent would remove line-breaks in firefox
          if (text.nonEmpty) {
            applyStringToNode(text) match {
              case Some(updatedNode) =>
                Var.set(
                  initialRender -> renderNodeDataWithFile(state, updatedNode.id, updatedNode.data, maxLength),
                  editMode -> false
                )

                val changes = GraphChanges.addNode(updatedNode)
                state.eventProcessor.changes.onNext(changes)
              case None =>
                editMode() = false
            }
          }
        }
      }

      VDomModifier(
        onDomMount.asHtml --> inNextAnimationFrame { elem => elem.focus() },
        onDomUpdate.asHtml --> inNextAnimationFrame { elem => elem.focus() },
        nodeStr, // Markdown source code
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
      )
    }

    def editableNodeContent(state: GlobalState, node: Node, editMode: Var[Boolean], initialRender: Rx[VNode], editRender: VDomModifier)(
      implicit ctx: Ctx.Owner
    ): VNode = {

      p( // has different line-height than div and is used for text by markdown
        outline := "none", // hides contenteditable outline
        keyed, // when updates come in, don't disturb current editing session
        Rx {
          if(editMode()) editRender else initialRender()
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
