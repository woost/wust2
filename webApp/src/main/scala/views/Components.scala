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
import wust.css.{CommonStyles, Styles, ZIndex}
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.util.StringOps._
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.jsdom.{FileReaderOps, IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, PageChange, UploadingFile}
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
      a(href := "mailto:team@woost.space", "team@woost.space", Elements.safeTargetBlank)
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
              state.uiModalConfig.onNext(Ownable(_ => ModalConfig(description, image(cls := "fluid"), modalModifier = cls := "basic"))) //TODO: better size settings
              ()
            })
            //TODO pdf preview does not work with "content-disposition: attachment"-header
//          case "application/pdf"           =>
//            val embeddedPdf = htmlTag("object")(downloadUrl(data), dsl.tpe := "application/pdf")
//            embeddedPdf(maxHeight := maxImageHeight, width := "100%")
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
              state.urlConfig.update(_.focus(Page(dmNode.id), View.Conversation, needsGet = false))
            case _ => // create a new channel, add user as member
              val nodeId = NodeId.fresh
              state.eventProcessor.changes.onNext(GraphChanges.newProject(nodeId, userId, title = dmName) merge GraphChanges.notify(nodeId, userId)) // TODO: noderole message
              state.urlConfig.update(_.focus(Page(nodeId), View.Conversation, needsGet = false))
              //TODO: this is a hack. Actually we need to wait until the new channel was added successfully
              dom.window.setTimeout({() =>
                Client.api.addMember(nodeId, dmUserId, AccessLevel.ReadWrite)
                val change:GraphChanges = GraphChanges.from(addEdges = Set(Edge.Invite(nodeId = nodeId, userId = dmUserId)))
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

  private def renderNodeTag(state: GlobalState, tag: Node, injected: VDomModifier, pageOnClick: Boolean): VNode = {
    span(
      cls := "node tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      if(pageOnClick) onClick foreach { e =>
        state.urlConfig.update(_.focus(Page(tag.id)))
        e.stopPropagation()
      } else cursor.default,
    )
  }

  def removableTagMod(action: () => Unit):VDomModifier = {
    VDomModifier(
      Styles.inlineFlex,
      span(
        Styles.flexStatic,
        "×",
        cls := "actionbutton",
        onClick.stopPropagation foreach {
          action()
        },
      )
    )
  }

  def removablePropertySection(
    state: GlobalState,
    key: String,
    properties: Array[PropertyData.PropertyValue],
  )(implicit ctx: Ctx.Owner): VNode = {

    val editKey = Var(false)

    div(
      Styles.flex,
      justifyContent.spaceBetween,
      flexWrap.wrap,
      alignItems.flexStart,
      b(
        color.gray,
        Styles.flex,
        EditableContent.textModifier(state, key, editKey, key => span(key), key => GraphChanges(addEdges = properties.map(p => p.edge.copy(data = p.edge.data.copy(key = key)))(breakOut))),
        ":",
        cursor.pointer,
        onClick.stopPropagation(true) --> editKey,
      ),
      div(
        Styles.flex,
        flexDirection.column,
        alignItems.flexEnd,
        padding := "0px 10px",

        properties.map { property =>
          div(
            Styles.flex,
            Elements.icon(ItemProperties.iconByNodeData(property.node.data))(marginRight := "5px"),
            div(
              editableNodeOnClick(state, property.node, maxLength = Some(100)),
              Components.removableTagMod(() =>
                state.eventProcessor.changes.onNext(GraphChanges(delEdges = Set(property.edge)))
              )
            )
          )
        }
      )
    )
  }


  def propertyTag(
    state: GlobalState,
    key: Edge.LabeledProperty,
    property: Node,
    pageOnClick: Boolean = false,
    dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
  ): VNode = {

    val icon = ItemProperties.iconByNodeData(property.data)
    val contentString = VDomModifier(
      Elements.icon(icon)(marginRight := "5px"),
      s"${key.data.key}: ${property.data.str}"
    )

    span(
      cls := "node tag",
      contentString,
      border := "1px solid gray",
      color.gray
    )
  }

  def removablePropertyTagCustom(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, action: () => Unit, pageOnClick: Boolean = false): VNode = {
    propertyTag(state, key, propertyNode, pageOnClick)(removableTagMod(action))
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
          state.urlConfig.update(_.focus(Page(tag.id)))
          e.stopPropagation()
        } else cursor.default,
        drag(DragItem.Tag(tag.id), target = DragItem.DisableDrag),
      )
    }

    def checkboxNodeTag(
      state: GlobalState,
      tagNode: Node,
      tagModifier: VDomModifier = VDomModifier.empty,
      pageOnClick: Boolean = false,
      dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
    )(implicit ctx: Ctx.Owner): VNode = {

      div( // checkbox and nodetag are both inline elements because of fomanticui
        Styles.flex,
        alignItems.center,
        div(
          Styles.flexStatic,
          cls := "ui checkbox",
          ViewFilter.addFilterCheckbox(
            state,
            tagNode.str, // TODO: renderNodeData
            GraphOperation.OnlyTaggedWith(tagNode.id)
          ),
          label(), // needed for fomanticui
        ),
        nodeTag(state, tagNode, pageOnClick, dragOptions).apply(tagModifier),
      )
    }

    def removableAssignedUser(state: GlobalState, user: Node.User, assignedNodeId: NodeId): VNode = {
      div(
        padding := "2px",
        borderRadius := "3px",
        backgroundColor := "white",
        color.black,
        Styles.flex,
        Avatar.user(user.id)(height := "20px"),
        div(marginLeft := "5px", displayUserName(user.data)),
        removableTagMod(() => state.eventProcessor.changes.onNext(GraphChanges.disconnect(Edge.Assigned)(assignedNodeId, user.id)))
      )
    }


    def nodeTag(
      state: GlobalState,
      tag: Node,
      pageOnClick: Boolean = false,
      dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
    ): VNode = {
      val contentString = renderNodeData(tag.data)
      renderNodeTag(state, tag, VDomModifier(contentString, dragOptions(tag.id)), pageOnClick)
    }

    def removableNodeTagCustom(state: GlobalState, tag: Node, action: () => Unit, pageOnClick:Boolean = false): VNode = {
      nodeTag(state, tag, pageOnClick)(removableTagMod(action))
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
        GraphChanges.disconnect(Edge.Child)(Set(ParentId(tag.id)), ChildId(taggedNodeId))
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

  def nodeCardEditableOnClick(state: GlobalState, node: Node, contentInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val editMode = Var(false)
    renderNodeCard(
      node,
      contentInject = VDomModifier(
        prependInject,
        editableNodeOnClick(state, node, maxLength),
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
        directParentIds.forall(nodeIsDoneInParent)
      }

      div(
        cls := "ui checkbox fitted",
        input(
          tpe := "checkbox",
          checked <-- isChecked,
          onClick.stopPropagation --> Observer.empty,
          onChange.checked foreach { checking =>
            val graph = state.graph.now
            directParentIds.flatMap(id => graph.workspacesForParent(graph.idToIdx(id))).foreach { workspaceIdx =>
              val doneIdx = graph.doneNodeForWorkspace(workspaceIdx)

              if(checking) {
                val (doneNodeId, doneNodeAddChange) = doneIdx match {
                  case None                   =>
                    val freshDoneNode = Node.MarkdownStage(Graph.doneText)
                    val expand = GraphChanges.connect(Edge.Expanded)(freshDoneNode.id, state.user.now.id)
                    (freshDoneNode.id, GraphChanges.addNodeWithParent(freshDoneNode, graph.nodeIds(workspaceIdx)) merge expand)
                  case Some(existingDoneNode) => (graph.nodeIds(existingDoneNode), GraphChanges.empty)
                }
                val stageParents = graph.notDeletedParentsIdx(graph.idToIdx(node.id)).collect{case idx if graph.nodes(idx).role == NodeRole.Stage && graph.workspacesForParent(idx).contains(workspaceIdx) => graph.nodeIds(idx)}
                val changes = doneNodeAddChange merge GraphChanges.changeSource(Edge.Child)(ChildId(node.id)::Nil, ParentId(stageParents), ParentId(doneNodeId)::Nil)
                state.eventProcessor.changes.onNext(changes)
              } else { // unchecking
                // since it was checked, we know for sure, that a done-node for every workspace exists
                val changes = GraphChanges.disconnect(Edge.Child)(doneIdx.map(idx => ParentId(graph.nodeIds(idx))), ChildId(node.id))
                state.eventProcessor.changes.onNext(changes)
              }
            }

          }
        ),
        label()
      )
    }

    def nodeCardWithCheckbox(state:GlobalState, node: Node, directParentIds:Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode = {
      nodeCardWithFile(state, node).prepend(
        Styles.flex,
        alignItems.flexStart,
        taskCheckbox(state, node, directParentIds)
      )
    }

    def readDragTarget(elem: dom.html.Element): js.UndefOr[DragTarget] = {
      readPropertyFromElement[DragTarget](elem, DragItem.targetPropName)
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

    def readDraggableDraggedAction(elem: dom.html.Element): js.UndefOr[() => Unit] = {
      readPropertyFromElement[() => Unit](elem, DragItem.draggedActionPropName)
    }

    def dragWithHandle(item: DragPayloadAndTarget):VDomModifier = dragWithHandle(item,item)
    def dragWithHandle(
      payload: => DragPayload = DragItem.DisableDrag,
      target: DragTarget = DragItem.DisableDrag,
    ): VDomModifier = {
      VDomModifier(
        //TODO: draggable bug: draggable sets display:none, then does not restore the old value https://github.com/Shopify/draggable/issues/318
        cls := "draggable", // makes this element discoverable for the Draggable library
        cls := "drag-feedback", // visual feedback for drag-start
        VDomModifier.ifTrue(payload.isInstanceOf[DragItem.DisableDrag.type])(cursor.auto), // overwrites cursor set by .draggable class
        prop(DragItem.payloadPropName) := (() => payload),
        prop(DragItem.targetPropName) := (() => target),
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
        snabbdom.VNodeProxy.repairDomBeforePatch, // draggable modifies the dom, but snabbdom assumes that the dom corresponds to its last vdom representation. So Before patch

        prop(DragContainer.propName) := (() => container),
        managedElement.asHtml { elem =>
          state.sortable.addContainer(elem)
          Cancelable { () => state.sortable.removeContainer(elem) }
        }
      )
    }

    def onAfterPayloadWasDragged: EmitterBuilder[Unit, VDomModifier] =
      EmitterBuilder.ofModifier[Unit] { sink =>
        IO {
          prop(DragItem.draggedActionPropName) := (() => () => sink.onNext(Unit))
        }
      }

    def nodeAvatar(node: Node, size: Int): VNode = {
      Avatar(node)(
        width := s"${ size }px",
        height := s"${ size }px"
      )
    }

    def editableNodeOnClick(state: GlobalState, node: Node, maxLength: Option[Int] = None, editMode: Var[Boolean] = Var(false))(
      implicit ctx: Ctx.Owner
    ): VNode = {
      editableNode(state, node, editMode, maxLength)(ctx)(
        onClick.stopPropagation.stopImmediatePropagation foreach {
          if(!editMode.now) {
            editMode() = true
          }
        },
        minWidth := "20px", minHeight := "20px", // minimal clicking area for empty content
      )
    }

    def editableNode(state: GlobalState, node: Node, editMode: Var[Boolean], maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
      EditableContent.ofNode(state, node, editMode, node => renderNodeDataWithFile(state, node.id, node.data, maxLength))
    }

    def searchInGraph(graph: Rx[Graph], placeholder: String, valid: Rx[Boolean] = Var(true), filter: Node => Boolean = _ => true, showParents: Boolean = true, completeOnInit: Boolean = true, elementModifier: VDomModifier = VDomModifier.empty, inputModifiers: VDomModifier = VDomModifier.empty, resultsModifier: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => IO {
      var elem: JQuerySelection = null
      div(
        keyed,
        elementModifier,
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
        div(cls := "results", resultsModifier),

        onDomMount.asJquery.foreach { e =>
          elem = e
        }
      )
    })

    def uploadFileInput(state: GlobalState, selected: Var[Option[AWS.UploadableFile]])(implicit ctx: Ctx.Owner): VNode = {

      input(display.none, tpe := "file",
        onChange.foreach { e =>
          val inputElement = e.currentTarget.asInstanceOf[dom.html.Input]
          if (inputElement.files.length > 0) selected() = AWS.upload(state, inputElement.files(0))
          else selected() = None
        }
      )
    }

    def defaultFileUploadHandler(state: GlobalState)(implicit ctx: Ctx.Owner): Var[Option[AWS.UploadableFile]] = {
      val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

      fileUploadHandler.foreach(_.foreach { uploadFile =>
        SharedViewElements.uploadFileAndCreateNode(state, "", state.page.now.parentId, uploadFile)
        fileUploadHandler() = None
      })

      fileUploadHandler
    }

    def uploadField(state: GlobalState, selected: Var[Option[AWS.UploadableFile]], tooltipDirection: String = "top left")(implicit ctx: Ctx.Owner): VDomModifier = {

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

      val onDragOverModifier = Handler.unsafe[VDomModifier]

      val fileInputId = "upload-file-field"
      div(
        padding := "3px",
        uploadFileInput(state, selected).apply(id := fileInputId),
        label(
          forId := fileInputId, // label for input will trigger input element on click.

          iconAndPopup.map { case (icon, popup) =>
            VDomModifier(
              UI.popupHtml(tooltipDirection) := popup,
              icon
            )
          },
          margin := "0px",
          Styles.flexStatic,
          cls := "ui circular icon button",
          fontSize := "1.1rem",
          backgroundColor := "steelblue",
          color := "white",
        ),

        onDragOverModifier,
        onDragEnter.preventDefault(opacity := 0.5) --> onDragOverModifier,
        onDragLeave.preventDefault.onlyOwnEvents(VDomModifier.empty) --> onDragOverModifier,
        onDragOver.preventDefault --> Observer.empty,

        onDrop.preventDefault.foreach { ev =>
          val elem = document.getElementById(fileInputId).asInstanceOf[dom.html.Input]
          elem.files = ev.dataTransfer.files
        },
      )
    }

  def removeableList[T](elements: Seq[T], removeSink: Observer[T], tooltip: Option[String] = None)(renderElement: T => VDomModifier): VNode = {
    div(
      width := "100%",
      elements.map { element =>
        div(
          Styles.flex,
          marginTop := "10px",
          alignItems.center,
          justifyContent.spaceBetween,

          renderElement(element),

          button(
            tooltip.map(UI.tooltip := _),
            cls := "ui tiny compact negative basic button",
            marginLeft := "10px",
            "Remove",
            onClick.stopPropagation(element) --> removeSink
          ),
        )
      }
    )
  }

  def automatedNodesOfNode(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VDomModifier = {
    val automatedNodes: Rx[Seq[Node]] = Rx {
      val graph = state.rawGraph()
      graph.automatedNodes(graph.idToIdx(node.id))
    }

    Rx {
      if (automatedNodes().isEmpty) VDomModifier.empty
      else VDomModifier(
        automatedNodes().map { node =>
          div(
            div(background := "repeating-linear-gradient(45deg, yellow, yellow 6px, black 6px, black 12px)", height := "3px"),
            UI.popup("bottom center") := "This node is an active automation template")
            Components.nodeTag(state, node, pageOnClick = false, dragOptions = _ => VDomModifier.empty).prepend(renderFontAwesomeIcon(Icons.automate).apply(marginLeft := "3px", marginRight := "3px")
          )
        }
      )
    }
  }

  case class MenuItem(title: VDomModifier, description: VDomModifier, active: Rx[Boolean], clickAction: () => Unit)
  object MenuItem {
    def apply(title: VDomModifier, description: VDomModifier, active: Boolean, clickAction: () => Unit): MenuItem =
      new MenuItem(title, description, Var(active), clickAction)
  }
  def verticalMenu(items: Seq[MenuItem]): VNode = menu(
    items,
    outerModifier = VDomModifier(
      width := "100%",
      Styles.flex,
      flexDirection.column,
      alignItems.flexStart,
    ),
    innerModifier = VDomModifier(
      width := "100%",
      flexGrow := 0,
      Styles.flex,
      alignItems.center,
      justifyContent.spaceBetween,
      paddingBottom := "10px"
    )
  )
  def horizontalMenu(items: Seq[MenuItem]): VNode = menu(
    items.map(item => item.copy(title = VDomModifier(item.title, marginBottom := "5px"))),
    outerModifier = VDomModifier(
      Styles.flex,
      justifyContent.spaceEvenly,
      alignItems.flexStart,
    ),
    innerModifier = VDomModifier(
      flexGrow := 0,
      width := "50px",
      Styles.flex,
      flexDirection.column,
      alignItems.center,
    )
  )

  def menu(items: Seq[MenuItem], innerModifier: VDomModifier, outerModifier: VDomModifier): VNode = {
    div(
      paddingTop := "10px",
      outerModifier,

      items.map { item =>
        div(
          Ownable(implicit ctx => Rx {
            if(item.active()) color.black else color := "rgba(0, 0, 0, 0.30)"
          }),
          div(item.title),
          div(item.description),
          onClick.foreach { item.clickAction() },
          cursor.pointer,

          innerModifier
        )
      }
    )
  }

  def sidebarNodeFocusMod(sidebarNode: Var[Option[NodeId]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNodeFocusClickMod(sidebarNode, nodeId),
    sidebarNodeFocusVisualizeMod(sidebarNode, nodeId)
  )

  def sidebarNodeFocusClickMod(sidebarNode: Var[Option[NodeId]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    cursor.pointer,
    onClick.stopPropagation.foreach {
      val nextNode = if (sidebarNode.now.contains(nodeId)) None else Some(nodeId)
      sidebarNode() = nextNode
    },
  )

  def sidebarNodeFocusVisualizeMod(sidebarNode: Rx[Option[NodeId]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNode.map(_ contains nodeId).map { isFocused =>
      VDomModifier.ifTrue(isFocused)(boxShadow := s"inset 0 0 2px 2px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )
  def sidebarNodeFocusVisualizeRightMod(sidebarNode: Rx[Option[NodeId]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNode.map(_ contains nodeId).map { isFocused =>
      VDomModifier.ifTrue(isFocused)(boxShadow := s"4px 0px 2px -2px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )
}

