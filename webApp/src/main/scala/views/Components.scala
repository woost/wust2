package wust.webApp.views

import wust.sdk.{BaseColors, NodeColor}
import cats.effect.IO
import emojijs.EmojiConvertor
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
import wust.util.macros.InlineList
import wust.util.StringOps._
import wust.util._
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.jsdom.{FileReaderOps, IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{GlobalState, PageChange, UploadingFile, FocusPreference}
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
  val implicitUserName = "Unregistered User"

  val woostTeamEmailLink = 
    VDomModifier(
      cls := "enable-text-selection",
      a(href := "mailto:team@woost.space", "team@woost.space", Elements.safeTargetBlank)
    )

  def displayUserName(user: NodeData.User): String = {
    if(user.isImplicit) {
      //hack for showing invite user by email with their email address. new implicit user do not have a name, just if they are invited. but old implicit users are named "unregisted-user-$id"
      if (user.name.nonEmpty && !user.name.startsWith("unregistered-user-")) s"${user.name} (unregistered)" else implicitUserName
    } else user.name
  }

  val htmlNodeData: NodeData => String = {
    case NodeData.Markdown(content)  => markdownString(content)
    case NodeData.PlainText(content) => escapeHtml(content)
    case user: NodeData.User         => s"User: ${ escapeHtml(displayUserName(user)) }"
    case d                           => d.str
  }

  def displayRelativeDate(data: NodeData.RelativeDate) = VDomModifier(span(color := "lightgray", "X + "), span(StringJsOps.durationToString(data.content)))

  def renderNodeData(nodeData: NodeData, maxLength: Option[Int] = None): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case data: NodeData.RelativeDate => div(displayRelativeDate(data))
    case d                           => div(trimToMaxLength(d.str, maxLength))
  }

  def renderNodeDataWithFile(state: GlobalState, nodeId: NodeId, nodeData: NodeData, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => renderUploadedFile(state, nodeId,file)
    case data: NodeData.RelativeDate => div(displayRelativeDate(data))
    case d                           => div(trimToMaxLength(d.str, maxLength))
  }

  def renderText(str: String): VNode = {
    p(
      overflow.hidden,
      textOverflow.ellipsis,
      whiteSpace.nowrap,
      Elements.innerHTML := Elements.UnsafeHTML(EmojiConvertor.replace_colons(escapeHtml(str)))
    )
  }
  def renderText(node: Node): VNode = renderText(node.str)

  def renderAsOneLineText(node: Node): VNode = {
    val textContent = {
      val lines = node.str.lines
      if (lines.hasNext) lines.next else ""
    }

    renderText(textContent)
  }

  def nodeCardAsOneLineText(node: Node): VNode = {
    val textNode = renderAsOneLineText(node)
    renderNodeCard(node, contentInject = textNode),
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

    val trimmedFileName = StringOps.trimToMaxLength(file.fileName, 20)
    def downloadLink = a(downloadUrl(href), s"Download ${trimmedFileName}", onClick.stopPropagation --> Observer.empty)

    div(
      if (file.key.isEmpty) { // this only happens for currently-uploading files
        VDomModifier(
          trimmedFileName,
          Rx {
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
          }
        )
      } else VDomModifier(
        p(downloadLink),
        contentType match {
          case t if t.startsWith("image/") =>
            val image = img(alt := fileName, downloadUrl(src), cls := "ui image")
            image(maxHeight := maxImageHeight, cursor.pointer, onClick.stopPropagation.foreach {
              state.uiModalConfig.onNext(Ownable(_ => ModalConfig(StringOps.trimToMaxLength(file.fileName, 20), image(cls := "fluid"), modalModifier = cls := "basic"))) //TODO: better size settings
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
              val change:GraphChanges =
                GraphChanges.newProject(nodeId, userId, title = dmName) merge
                GraphChanges.from(addEdges = Set(
                  Edge.Invite(nodeId = nodeId, userId = dmUserId),
                  Edge.Notify(nodeId = nodeId, userId = dmUserId),
                  Edge.Member(nodeId = nodeId, EdgeData.Member(AccessLevel.ReadWrite), userId = dmUserId)
                ))

              state.eventProcessor.changes.onNext(change)
              state.urlConfig.update(_.focus(Page(nodeId), View.Conversation, needsGet = false))
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
      cls := "node colorful tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      if(pageOnClick) onClick foreach { e =>
        state.urlConfig.update(_.focus(Page(tag.id)))
        e.stopPropagation()
      } else cursor.default,
    )
  }

  def unremovableTagMod(action: () => Unit):VDomModifier = {
    VDomModifier(
      Styles.inlineFlex,
      span(
        Styles.flexStatic,
        Icons.undelete,
        cls := "actionbutton",
        onClick.stopPropagation foreach {
          action()
        },
      )
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
      width := "100%",
      Styles.wordWrap,
      Styles.flex,
      flexWrap.wrap,
      alignItems.flexStart,
      b(
        editKey.map {
          case true => VDomModifier.empty
          case false => textDecoration.underline
        },
        color.gray,
        Styles.flex,
        EditableContent.inputInlineOrRender[String](key, editKey, key => span(key + ":")).editValue.map { key =>
          GraphChanges(addEdges = properties.map(p => p.edge.copy(data = p.edge.data.copy(key = key)))(breakOut)),
        } --> state.eventProcessor.changes,
        cursor.pointer,
        onClick.stopPropagation(true) --> editKey,
      ),
      div(
        Styles.flex,
        marginLeft := "auto",
        flexDirection.column,
        alignItems.flexEnd,

        properties.map { property =>
          val editValue = Var(false)
          div(
            Styles.flex,
            justifyContent.flexEnd,
            Elements.icon(ItemProperties.iconByNodeData(property.node.data))(marginRight := "5px"),
            editablePropertyNode(state, property.node, property.edge, editMode = editValue,
              nonPropertyModifier = VDomModifier(writeHoveredNode(state, property.node.id), cursor.pointer, onClick.stopPropagation(Some(FocusPreference(property.node.id))) --> state.rightSidebarNode),
              maxLength = Some(100), config = EditableContent.Config.default,
            ),
            div(
              marginLeft := "5px",
              cursor.pointer,
              editValue.map {
                case true => VDomModifier(
                  Icons.delete,
                  onClick(GraphChanges(delEdges = Set(property.edge))) --> state.eventProcessor.changes
                )
                case false => VDomModifier(
                  Icons.edit,
                  onClick.stopPropagation(true) --> editValue
                )
              },
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
  )(implicit ctx: Ctx.Owner): VNode = {

    val icon = ItemProperties.iconByNodeData(property.data)
    val contentString = VDomModifier(
      Styles.flex,
      alignItems.flexStart,
      div(
        Styles.flex,
        alignItems.center,
        icon.map(_(marginRight := "4px")),
        u(s"${key.data.key}:", marginRight := "4px", fontSize.xSmall),
      ),
      renderNodeDataWithFile(state, property.id, property.data, maxLength = Some(50))
    )

    span(
      cls := "node tag",
      contentString,
      boxShadow := "inset 0 0 1px 1px lightgray",
      color.gray,
      drag(DragItem.Property(key), target = DragItem.DisableDrag),
      property.role match {
        case NodeRole.Neutral => VDomModifier.empty
        case _ => writeHoveredNode(state, property.id)
      }
    )
  }

  def removableUserAvatar(state: GlobalState, userNode: Node.User, targetNodeId: NodeId): VNode = {
    div(
      Styles.flexStatic,
      Avatar.user(userNode.id)(
        marginRight := "2px",
        width := "22px",
        height := "22px",
        cls := "avatar",
        ),
      keyed(userNode.id),
      UI.popup := s"Assigned to ${displayUserName(userNode.data)}. Click to remove.",
      cursor.pointer,
      onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(targetNodeId, userNode.id)) --> state.eventProcessor.changes,
    )
  }

  def removablePropertyTagCustom(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, action: () => Unit, pageOnClick: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    propertyTag(state, key, propertyNode, pageOnClick).apply(removableTagMod(action))
  }

  def removablePropertyTag(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, pageOnClick:Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
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
        cls := "nodecard",
        node.role match {
          case NodeRole.Project => VDomModifier(
            backgroundColor := BaseColors.pageBg.copy(h = NodeColor.hue(node.id)).toHex,
            borderColor := BaseColors.pageBorder.copy(h = NodeColor.hue(node.id)).toHex,
            cls := "project"
          )
          case _ => VDomModifier(
            cls := "node"
          )
        },
        div(
          cls := "nodecard-content",
          contentInject
        ),
      )
    }
    def nodeCard(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
      renderNodeCard(
        node,
        contentInject = VDomModifier(renderNodeData(node.data, maxLength).apply(nodeInject), contentInject)
      )
    }
    def nodeCardWithFile(state: GlobalState, node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = VDomModifier(renderNodeDataWithFile(state, node.id, node.data, maxLength).apply(nodeInject), contentInject)
      )
    }
    def nodeCardWithoutRender(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
      renderNodeCard(
        node,
        contentInject = VDomModifier(p(StringOps.trimToMaxLength(node.str, maxLength), nodeInject), contentInject)
      )
    }
    def nodeCardEditable(state: GlobalState, node: Node, editMode: Var[Boolean], contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = VDomModifier(
          prependInject,
          editableNode(state, node, editMode, maxLength).apply(nodeInject),
          contentInject
        ),
      ).apply(
        Rx { editMode().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },
      )
    }

  def nodeCardEditableOnClick(state: GlobalState, node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val editMode = Var(false)
    renderNodeCard(
      node,
      contentInject = VDomModifier(
        prependInject,
        editableNodeOnClick(state, node, maxLength).apply(nodeInject),
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
                    (freshDoneNode.id, GraphChanges.addNodeWithParent(freshDoneNode, ParentId(graph.nodeIds(workspaceIdx))) merge expand)
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

    def editableNodeOnClick(state: GlobalState, node: Node, maxLength: Option[Int] = None, editMode: Var[Boolean] = Var(false), config: EditableContent.Config = EditableContent.Config.cancelOnError)(
      implicit ctx: Ctx.Owner
    ): VNode = {
      editableNode(state, node, editMode, maxLength, config)(ctx)(
        onClick.stopPropagation foreach {
          if(!editMode.now) {
            editMode() = true
          }
        },
        minWidth := "20px", minHeight := "20px", // minimal clicking area
      )
    }

    def editablePropertyNodeOnClick(state: GlobalState, node: Node, edge: Edge.LabeledProperty, nonPropertyModifier: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, editMode: Var[Boolean] = Var(false), config: EditableContent.Config = EditableContent.Config.cancelOnError)(
      implicit ctx: Ctx.Owner
    ): VNode = {
      editablePropertyNode(state, node, edge, editMode, nonPropertyModifier, maxLength, config)(ctx)(
        onClick.stopPropagation foreach {
          if(!editMode.now) {
            editMode() = true
          }
        },
        minWidth := "20px", minHeight := "20px", // minimal clicking area
      )
    }

    def editableNode(state: GlobalState, node: Node, editMode: Var[Boolean], maxLength: Option[Int] = None, config: EditableContent.Config = EditableContent.Config.cancelOnError)(implicit ctx: Ctx.Owner): VNode = {
      div(
        EditableContent.ofNodeOrRender(state, node, editMode, node => renderNodeDataWithFile(state, node.id, node.data, maxLength), config).editValue.map(GraphChanges.addNode) --> state.eventProcessor.changes,
      )
    }

    def editablePropertyNode(state: GlobalState, node: Node, edge: Edge.LabeledProperty, editMode: Var[Boolean], nonPropertyModifier: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, config: EditableContent.Config = EditableContent.Config.cancelOnError)(implicit ctx: Ctx.Owner): VNode = {
      div(
        node.role match {
          case NodeRole.Neutral => VDomModifier(
            EditableContent.ofNodeOrRender(state, node, editMode, node => renderNodeDataWithFile(state, node.id, node.data, maxLength), config).editValue.map(GraphChanges.addNode) --> state.eventProcessor.changes
          )
          case _ => VDomModifier(
            EditableContent.customOrRender[Node](node, editMode, node => nodeCardWithFile(state, node, maxLength = maxLength).apply(Styles.wordWrap, nonPropertyModifier), handler => searchAndSelectNode(state, handler.collectHandler[Option[NodeId]] { case id => EditInteraction.fromOption(id.map(state.rawGraph.now.nodesById(_))) } { case EditInteraction.Input(v) => Some(v.id) }.transformObservable(_.prepend(Some(node.id)))), config).editValue.map { newNode =>

              GraphChanges(delEdges = Set(edge), addEdges = Set(edge.copy(propertyId = PropertyId(newNode.id))))
            } --> state.eventProcessor.changes,
          )
        }
      )
    }

    def searchAndSelectNode(state: GlobalState, current: Handler[Option[NodeId]])(implicit ctx: Ctx.Owner): VNode = searchAndSelectNode(state, current, current.onNext(_))
    def searchAndSelectNode(state: GlobalState, observable: Observable[Option[NodeId]], observer: Option[NodeId] => Unit)(implicit ctx: Ctx.Owner): VNode = {
      div(
        Components.searchInGraph(state.rawGraph, "Search", filter = {
          case n: Node.Content => InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Project)(n.role)
          case _ => false
        }, innerElementModifier = width := "100%", inputModifiers = width := "100%").map(Some(_)).foreach(observer),

        observable.map[VDomModifier] {
          case Some(nodeId) => div(
            marginTop := "4px",
            Styles.flex,
            alignItems.flexStart,
            justifyContent.spaceBetween,
            span("Selected:", color.gray, margin := "0px 5px 0px 5px"),
            state.graph.map { g =>
              val node = g.nodesById(nodeId)
              Components.nodeCardWithFile(state, node, maxLength = Some(100)).apply(Styles.wordWrap)
            }
          )
          case None => VDomModifier.empty
        }
      )
    }

    def searchInGraph(graph: Rx[Graph], placeholder: String, valid: Rx[Boolean] = Var(true), filter: Node => Boolean = _ => true, showParents: Boolean = true, completeOnInit: Boolean = true, showNotFound: Boolean = true, elementModifier: VDomModifier = VDomModifier.empty, innerElementModifier: VDomModifier = VDomModifier.empty, inputModifiers: VDomModifier = VDomModifier.empty, resultsModifier: VDomModifier = VDomModifier.empty, createNew: String => Boolean = _ => false)(implicit ctx: Ctx.Owner): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => IO {
      var inputElem: dom.html.Element = null
      var elem: JQuerySelection = null

      def initSearch(): Unit = {
        elem.search(arg = new SearchOptions {
          `type` = if (showParents) "category" else js.undefined

          cache = false
          searchOnFocus = true
          minCharacters = 0
          showNoResults = showNotFound

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
              title = trimToMaxLength(str, 36)
              category = cat
              data = js.Dynamic.literal(id = node.id.asInstanceOf[js.Any])
            }
          }(breakOut): js.Array[SearchSourceEntry]

          searchFields = js.Array("title")

          onSelect = { (selected, results) =>
            submitResult(selected)
            true
          }: js.Function2[SearchSourceEntry, js.Array[SearchSourceEntry], Boolean]
        })
      }

      def resetSearch(): Unit = {
        //TODO only reliable way to make search work again after manual submission with enter
        inputElem.blur()
        dom.window.setTimeout(() => inputElem.focus, timeout = 20)
      }

      def submitResult(value: js.Any, forceClose: Boolean = false): Unit = {
        val id = value.asInstanceOf[js.Dynamic].data.id.asInstanceOf[NodeId]
        sink.onNext(id)
        elem.search("set value", "")
        if (forceClose) resetSearch()
      }
      def submitNew(value: String, forceClose: Boolean = false): Unit = {
        if (createNew(value)) {
          elem.search("set value", "")
          if (forceClose) resetSearch()
        }
      }

      div(
        keyed,
        elementModifier,
        cls := "ui category search",
        div(
          cls := "ui icon input",
          innerElementModifier,
          input(
            borderRadius := "4px",
            inputModifiers,
            cls := "prompt",
            tpe := "text",
            dsl.placeholder := placeholder,

            onDomMount.asHtml.foreach { e => inputElem = e },

            onEnter.foreach { e =>
              val inputElem = e.target.asInstanceOf[dom.html.Input]
              val searchString = inputElem.value
              if (searchString.trim.nonEmpty) {
                elem.search("get result", searchString) match {
                  case v if v == false || v == js.undefined || v == null => submitNew(searchString, forceClose = true)
                  case obj => submitResult(obj, forceClose = true)
                }
              }
            },

            onFocus.foreach { _ =>
              initSearch()
              if (completeOnInit) elem.search("search local", "")
            },

            valid.map(_.ifFalse[VDomModifier](borderColor := "tomato"))
          ),
          i(cls := "search icon"),
        ),
        div(cls := "results", resultsModifier),

        managedElement.asJquery { e =>
          elem = e
          Cancelable(() => e.search("destroy"))
        }
      )
    })

    def defaultFileUploadHandler(state: GlobalState, focusedId: NodeId)(implicit ctx: Ctx.Owner): Var[Option[AWS.UploadableFile]] = {
      val fileUploadHandler = Var[Option[AWS.UploadableFile]](None)

      fileUploadHandler.foreach(_.foreach { uploadFile =>
        AWS.uploadFileAndCreateNode(state, uploadFile, nodeId => GraphChanges.addToParent(ChildId(nodeId), ParentId(focusedId)) merge GraphChanges.connect(Edge.LabeledProperty)(focusedId, EdgeData.LabeledProperty.attachment, PropertyId(nodeId))).foreach { _ =>
          fileUploadHandler() = None
        }
      })

      fileUploadHandler
    }

    def uploadFieldModifier(selected: Observable[Option[dom.File]], fileInputId: String, tooltipDirection: String = "top left")(implicit ctx: Ctx.Owner): VDomModifier = {

      val iconAndPopup = selected.map {
        case None =>
          (fontawesome.icon(Icons.fileUpload), None)
        case Some(file) =>
          val popupNode = file.`type` match {
            case t if t.startsWith("image/") =>
              val dataUrl = dom.URL.createObjectURL(file)
              img(src := dataUrl, height := "100px", maxWidth := "400px") //TODO: proper scaling and size restriction
            case _ => div(file.name)
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

          (icon, Some(popupNode))
      }

      val onDragOverModifier = Handler.unsafe[VDomModifier]

      VDomModifier(
        label(
          forId := fileInputId, // label for input will trigger input element on click.

          iconAndPopup.map { case (icon, popup) =>
            VDomModifier(
              popup.map(UI.popupHtml(tooltipDirection) := _),
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

    def uploadField(state: GlobalState, selected: Var[Option[AWS.UploadableFile]])(implicit ctx: Ctx.Owner): VNode = {
      implicit val context = EditContext(state)

      EditableContent.inputFieldRx[AWS.UploadableFile](selected, config = EditableContent.Config(
        errorMode = EditableContent.ErrorMode.ShowToast,
        submitMode = EditableContent.SubmitMode.Off
      )).apply(marginLeft := "3px")
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
            cls := "ui mini compact negative basic button",
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

  def sidebarNodeFocusMod(sidebarNode: Var[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNodeFocusClickMod(sidebarNode, nodeId),
    sidebarNodeFocusVisualizeMod(sidebarNode, nodeId)
  )

  def sidebarNodeFocusClickMod(sidebarNode: Var[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    cursor.pointer,
    onClick.stopPropagation.foreach {
      val nextNode = if (sidebarNode.now.exists(_.nodeId == nodeId)) None else Some(FocusPreference(nodeId))
      sidebarNode() = nextNode
    },
  )

  def sidebarNodeFocusVisualizeMod(sidebarNode: Rx[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNode.map(_.exists(_.nodeId == nodeId)).map { isFocused =>
      VDomModifier.ifTrue(isFocused)(boxShadow := s"inset 0 0 2px 2px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )
  def sidebarNodeFocusVisualizeRightMod(sidebarNode: Rx[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNode.map(_.exists(_.nodeId == nodeId)).map { isFocused =>
      VDomModifier.ifTrue(isFocused)(boxShadow := s"4px 0px 2px -2px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )

  def showHoveredNode(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    state.hoverNodeId.map {
      case Some(`nodeId`) => boxShadow := s"inset 0 0 2px 2px gray"
      case _ => VDomModifier.empty
    }
  )
  def writeHoveredNode(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    onMouseOver(Some(nodeId)) --> state.hoverNodeId,
    onMouseOut(None) --> state.hoverNodeId,
  )
}

