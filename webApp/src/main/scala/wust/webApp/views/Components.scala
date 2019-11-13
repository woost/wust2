package wust.webApp.views

//import acyclic.file
import fontAwesome._
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import outwatch.ext.monix._
import outwatch.reactive.{SinkObserver, _}
import outwatch.reactive.handler._
import rx._
import wust.css.{CommonStyles, Styles}
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.{SearchOptions, SearchSourceEntry}
import wust.facades.jquery.JQuerySelection
import wust.graph._
import wust.ids.{Feature, _}
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, NodeColor}
import wust.util.StringOps._
import wust.util._
import wust.util.macros.InlineList
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.state._
import wust.webApp.views.UploadComponents._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, UI}
import wust.facades.segment.Segment

import scala.collection.breakOut
import scala.scalajs.js

// This file contains woost-related UI helpers.

object Components {
  val implicitUserName = "Unregistered User"

  def woostEmailLink(prefix:String = "team") =
    VDomModifier(
      cls := "enable-text-selection",
      a(href := s"mailto:$prefix@woost.space", s"$prefix@woost.space", Elements.safeTargetBlank)
    )
  val woostTeamEmailLink = woostEmailLink("team")

  def displayUserName(user: NodeData.User): String = {
    if(user.isImplicit) {
      //hack for showing invite user by email with their email address. new implicit user do not have a name, just if they are invited. but old implicit users are named "unregisted-user-$id"
      if (user.name.nonEmpty && !user.name.startsWith("unregistered-user-")) s"${user.name} (unregistered)" else implicitUserName
    } else user.name
  }

  val htmlNodeData: NodeData => String = {
    case NodeData.Markdown(content)  => Elements.markdownString(content)
    case NodeData.PlainText(content) => escapeHtml(content)
    case user: NodeData.User         => s"User: ${ escapeHtml(displayUserName(user)) }"
    case d                           => d.str
  }

  def displayDuration(data: NodeData.Duration) = span(StringJsOps.durationToString(data.content))
  def displayRelativeDate(data: NodeData.RelativeDate) = VDomModifier(span(color := "lightgray", "X + "), span(StringJsOps.durationToString(data.content)))
  def displayDate(data: NodeData.Date) = span(StringJsOps.dateToString(data.content))
  def displayDateTime(data: NodeData.DateTime) = span(StringJsOps.dateTimeToString(data.content))
  def displayPlaceholder(data: NodeData.Placeholder) = span(
    color := "#CD5C5C	",
    freeSolid.faExclamation,
    i(marginLeft := "0.25em", "missing")
  )

  def renderNodeData(node: Node, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = node.data match {
    case NodeData.Markdown(content)  => markdownVNode(node.id, trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => renderUploadedFile( node.id, file)
    case data: NodeData.RelativeDate => div(displayRelativeDate(data))
    case data: NodeData.Date         => div(displayDate(data))
    case data: NodeData.DateTime     => div(displayDateTime(data))
    case data: NodeData.Placeholder  => div(displayPlaceholder(data))
    case data: NodeData.Duration     => div(displayDuration(data))
    case d                           => div(trimToMaxLength(d.str, maxLength))
  }

  def replaceEmoji(str: String): VNode = {
    span.thunkStatic(uniqueKey(str))(VDomModifier(
      Elements.innerHTML := Elements.UnsafeHTML(EmojiConvertor.replace_colons_safe(escapeHtml(str)))
    ))
  }

  def replaceEmojiUnified(str: String): VNode = {
    span.thunkStatic(uniqueKey(str))(VDomModifier(
      Elements.innerHTML := Elements.UnsafeHTML(EmojiConvertor.replace_unified_safe(escapeHtml(str)))
    ))
  }

  def renderAsOneLineText(node: Node): VNode = {
    // 1. extract first line of string
    val firstLine = {
      //TODO: skip markdown syntax which does not display any text, like "```scala"
      val lines = node.str.linesIterator
      if (lines.hasNext) lines.next else ""
    }

    // 2. render markdown
    markdownVNode(node.id, firstLine).apply(
      // 3. overwrite font styles via css
      // 4. crop via overflow ellipsis
      cls := "oneline"
    )
  }

  def nodeCardAsOneLineText(node: Node, projectWithIcon: Boolean = true)(implicit ctx: Ctx.Owner): VNode = {
    renderNodeCard(node, contentInject = renderAsOneLineText( _), projectWithIcon = projectWithIcon)
  }

  def markdownVNode(nodeId: NodeId, rawStr: String) = {
    // highlight mentions. just use graph.now, because mentions are normally added together with the node.
    // updates are not to be expected.
    val graph = GlobalState.rawGraph.now
    val str = graph.idToIdxFold(nodeId)(rawStr) { nodeIdx =>
      graph.mentionsEdgeIdx.foldLeft(nodeIdx)(rawStr) { (str, edgeIdx) =>
        val edge = graph.edges(edgeIdx).as[Edge.Mention]
        val mentionName = "@" + InputMention.stringToMentionsString(edge.data.mentionName)
        // bold with markdown. how to inject html with popup?
        str.replace(mentionName, s"**${mentionName}**")
      }
    }

    div.thunkStatic(uniqueKey(str))(VDomModifier(
      cls := "markdown",
      div(innerHTML := UnsafeHTML(Elements.markdownString(str)))
    )) // intentionally double wrapped. Because innerHtml does not compose with other modifiers
  }


  // FIXME: Ensure unique DM node that may be renamed.
  def onClickDirectMessage(dmUser: Node.User): VDomModifier = {
    val user = GlobalState.user.now
    val userId = user.id
    val dmUserId = dmUser.id
    (userId != dmUserId).ifTrue[VDomModifier]({
      val dmName = IndexedSeq[String](displayUserName(user.toNode.data), displayUserName(dmUser.data)).sorted.mkString(", ")
      VDomModifier(
        onClick.foreach{
          val graph = GlobalState.graph.now
          val previousDmNode: Option[Node] = graph.idToIdxFold(userId)(Option.empty[Node]) { userIdx =>
            graph.chronologicalNodesAscending.find { n =>
              n.str == dmName && graph.idToIdxFold(n.id)(false)(graph.isPinned(_, userIdx))
            }
          } // Max 1 dm node with this name
          previousDmNode match {
            case Some(dmNode) if graph.can_access_node(user.id, dmNode.id) =>
              GlobalState.urlConfig.update(_.focus(Page(dmNode.id), View.Conversation))
            case _ => // create a new channel, add user as member
              val nodeId = NodeId.fresh
              val change:GraphChanges =
                GraphChanges.newProject(nodeId, userId, title = dmName) merge
                GraphChanges(addEdges = Array(
                  Edge.Invite(nodeId = nodeId, userId = dmUserId),
                  Edge.Notify(nodeId = nodeId, userId = dmUserId),
                  Edge.Member(nodeId = nodeId, EdgeData.Member(AccessLevel.ReadWrite), userId = dmUserId)
                ))

              GlobalState.submitChanges(change)
              GlobalState.urlConfig.update(_.focus(Page(nodeId), View.Chat, needsGet = false))
              ()
          }
          Segment.trackEvent("Direct Message")
        },
        cursor.pointer,
        UI.tooltip := s"Start Conversation with ${displayUserName(dmUser.data)}"
      )
    })
  }

  private def renderNodeTag(tag: Node, injected: VDomModifier, pageOnClick: Boolean): VNode = {
    span(
      cls := "node colorful tag",
      injected,
      backgroundColor := tagColor(tag.id).toHex,
      if(pageOnClick) onClick foreach { e =>
        GlobalState.focus(tag.id)
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
    key: String,
    properties: Seq[PropertyData.PropertyValue],
    focusState: FocusState,
    parentIdAction: Option[FocusPreference] => Unit, // TODO: use focusState instead
  )(implicit ctx: Ctx.Owner): VNode = {

    val editKey = Var(false)

    div(
      width := "100%",
      Styles.wordWrap,
      Styles.flex,
      flexWrap.wrap,
      alignItems.flexStart,
      justifyContent.spaceBetween,

      b(
        EditableContent.inlineEditorOrRender[String](key, editKey, _ => key => span(key + ":", cls := "propertykey")).editValue.collect { case newKey if newKey != key =>
          GraphChanges(addEdges = properties.map(p => p.edge.copy(data = p.edge.data.copy(key = newKey)))(breakOut), delEdges = properties.map(_.edge)(breakOut)),
        } --> GlobalState.eventProcessor.changes,
        cursor.pointer,
        onClick.stopPropagation.use(true) --> editKey,
      ),

      div(
        flexGrow := 1,

        properties.map { property =>
          val editValue = Var(false)
          div(
            marginLeft := "10px",
            Styles.flex,
            justifyContent.spaceBetween,

            div(
              editValue.map {
                case true => VDomModifier(
                  UI.checkboxEmitter(span(Icons.showOnCard, " Show on Card", fontSize.xSmall), isChecked = property.edge.data.showOnCard).collect { case showOnCard if property.edge.data.showOnCard != showOnCard =>
                    GraphChanges(addEdges = Array(property.edge.copy(data = property.edge.data.copy(showOnCard = showOnCard)))),
                  } --> GlobalState.eventProcessor.changes,
                )
                case false => VDomModifier.empty
              }
            ),

            div(
              Styles.flex,
              justifyContent.flexEnd,
              margin := "3px 0px",

              editablePropertyNode( property.node, property.edge, editMode = editValue,
                nonPropertyModifier = VDomModifier(
                  writeHoveredNode( property.node.id),
                  cursor.pointer,
                  //TODO: rightsidebarnodcardmod ?
                  onClick.stopPropagation.use(Some(FocusPreference(property.node.id))).foreach(parentIdAction(_))
                ),
                maxLength = Some(100), config = EditableContent.Config.default,
              ).apply(cls := "propertyvalue"),

              div(
                marginLeft := "5px",
                cursor.pointer,
                editValue.map {
                  case true => VDomModifier(
                    Icons.delete,
                    onClick.useLazy(GraphChanges(delEdges = Array(property.edge))) --> GlobalState.eventProcessor.changes
                  )
                  case false => VDomModifier(
                    Icons.edit,
                    onClick.stopPropagation.use(true) --> editValue
                  )
                },
              )
            )
          )
        }
      )
    )
  }


  def nodeCardProperty(
    focusState: FocusState,
    traverseState: TraverseState,
    key: Edge.LabeledProperty,
    property: Node,
    modifier: VDomModifier = VDomModifier.empty
  )(implicit ctx: Ctx.Owner): VNode = {

    val isReference = property.role != NodeRole.Neutral
    val fullWidthForReferences = VDomModifier.ifTrue(isReference)(width := "100%")

    val propertyRendering = property.role match {
      case NodeRole.Neutral => property.data match {
        case NodeData.Placeholder(selection) => editableNodeOnClick(property, maxLength = Some(50), config = EditableContent.Config.cancelOnError.copy(submitOnBlur = false)).apply(
          onDblClick.stopPropagation.discard, // do not propagate dbl click which would focus the nodecard.
          cursor.pointer,
          cls := "neutral"
        )
        case _ => renderNodeData( property, maxLength = Some(50)).apply(cls := "detail", cls := "neutral" ),
      }
        case _ =>
          TaskNodeCard.renderThunk(
            focusState = focusState,
            traverseState = traverseState.step(key.nodeId),
            nodeId = property.id,
            isProperty = true,
        )
    }

    span(
      cls := "ui label property",
      VDomModifier.ifTrue(isReference)(cls := "reference"),
      fullWidthForReferences,

      s"${key.data.key}:",

      modifier,
      position.relative, // for editing dialog

      div(
        cls := "detail",
        VDomModifier.ifTrue(isReference)(margin := "3px 0 0 0"),
        fullWidthForReferences,
        propertyRendering,
      )
    )
  }

  def removableUserAvatar(userNode: Node.User, targetNodeId: NodeId, size: String = "22px"): VNode = {
    div(
      Styles.flexStatic,
      Avatar.user(userNode, size = size)(
        marginRight := "2px",
      ),
      keyed(userNode.id),
      UI.tooltip("left center") := s"${displayUserName(userNode.data)} (click to unassign)",
      cursor.pointer,
      onClick.stopPropagation.useLazy(GraphChanges.disconnect(Edge.Assigned)(targetNodeId, userNode.id)) --> GlobalState.eventProcessor.changes,
    )
  }

    def removableAssignedUser(user: Node.User, assignedNodeId: NodeId): VNode = {
      renderUser(user).apply(
        removableTagMod(() => GlobalState.submitChanges(GraphChanges.disconnect(Edge.Assigned)(assignedNodeId, user.id)))
      )
    }

    def renderUser(user: Node.User, size:String = "20px", enableDrag:Boolean = true, appendName: VDomModifier = VDomModifier.empty): VNode = {
      div(
        cls := "username",
        padding := "2px",
        borderRadius := "3px",
        backgroundColor := "white",
        color.black,
        Styles.flex,
        alignItems.center,
        Avatar.user(user, size = size, enableDrag = enableDrag),
        div(marginLeft := "5px", displayUserName(user.data), appendName, Styles.wordWrap),
      )
    }

    def nodeTag(
      tag: Node,
      pageOnClick: Boolean = false,
      dragOptions: NodeId => VDomModifier = nodeId => DragComponents.drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
    ): VNode = {
      val contentString = renderAsOneLineText( tag)
      renderNodeTag( tag, VDomModifier(contentString, dragOptions(tag.id)), pageOnClick)
    }

    def removableNodeTagCustom(tag: Node, action: () => Unit, pageOnClick:Boolean = false): VNode = {
      nodeTag( tag, pageOnClick)(removableTagMod(action))
    }

    def removableNodeTag(tag: Node, taggedNodeId: NodeId, pageOnClick:Boolean = false): VNode = {
      removableNodeTagCustom(tag, () => {
        GlobalState.submitChanges(
          GraphChanges.disconnect(Edge.Child)(Array(ParentId(tag.id)), ChildId(taggedNodeId))
        )
      }, pageOnClick)
    }

    def renderProject(node: Node, renderNode: Node => VDomModifier, withIcon: Boolean = false, openFolder: Boolean = false) = {
      if(withIcon) {
        val nodeWithoutFirstEmoji = node match {
          case n@Node.Content(_, editable: NodeData.EditableText, _, _, _, _) =>
            editable.updateStr(EmojiReplacer.emojiAtBeginningRegex.replaceFirstIn(n.str, "")) match {
              case Some(dataWithoutEmoji) =>
                n.copy(data = dataWithoutEmoji)
              case None                   => n
            }
          case n                                                           => n
        }

        val iconModifier: VNode = node.str match {
          case EmojiReplacer.emojiAtBeginningRegex(emoji) =>
            replaceEmoji(emoji)
          case _ if openFolder   =>
            span(freeSolid.faFolderOpen)
          case _ =>
            span(
              freeSolid.faFolder,
              color := BaseColors.pageBg.copy(h = NodeColor.hue(node.id)).toHex
            )
        }

        VDomModifier(
          Styles.flex,
          alignItems.baseline,
          iconModifier.apply(marginRight := "0.1em"),
          renderNode(nodeWithoutFirstEmoji),
        )
      } else {
        renderNode(node),
      }
    }

    def renderNodeCardMod(node: Node, contentInject: Node => VDomModifier, projectWithIcon: Boolean = true)(implicit ctx: Ctx.Owner): VDomModifier = {
      def renderAutomatedNodes = automatedNodesOfNode(node.id)
      def contentNode(node: Node) = div(
        cls := "nodecard-content",
        contentInject(node) match {
          case node: VNode => node.apply(renderAutomatedNodes)
          case mod => VDomModifier(mod, renderAutomatedNodes)
        }
      )

      VDomModifier(
        node match {
          case user: Node.User => renderUser(user, size = "14px")
          case node if node.role == NodeRole.Project => VDomModifier(
            cls := "project",
            cls := "nodecard",
            renderProject(node, contentNode, withIcon = projectWithIcon)
          )
          case node if node.role == NodeRole.Tag => VDomModifier( //TODO merge this definition with renderNodeTag
            cls := "tag colorful",
            cls := "nodecard",
            backgroundColor := tagColor(node.id).toHex,
            contentNode(node),
          )
          case node => VDomModifier(
            cls := "node",
            cls := "nodecard",
            contentNode(node)
          )
        },
      )
    }

    def renderNodeCard(node: Node, contentInject: Node => VDomModifier, projectWithIcon: Boolean = true)(implicit ctx: Ctx.Owner): VNode = {
      div(
        keyed(node.id), //TODO WHY?
        renderNodeCardMod(node, contentInject, projectWithIcon = projectWithIcon)
      )
    }
    def nodeCardMod(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VDomModifier = {
      renderNodeCardMod(
        node,
        contentInject = node => VDomModifier(renderNodeData( node, maxLength).apply(nodeInject), contentInject)
      )
    }
    def nodeCard(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, projectWithIcon: Boolean = true)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(renderNodeData( node, maxLength).apply(nodeInject), contentInject),
        projectWithIcon = projectWithIcon
      )
    }
    def nodeCardWithoutRender(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(p(StringOps.trimToMaxLength(node.str, maxLength), nodeInject), contentInject)
      )
    }

  def editModeIndicator(editMode:Var[Boolean])(implicit ctx: Ctx.Owner) = Rx { VDomModifier.ifTrue(editMode())(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)") }

    def nodeCardEditable(node: Node, editMode: Var[Boolean], contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(
          prependInject,
          editableNode( node, editMode, maxLength).apply(nodeInject),
          contentInject
        ),
        projectWithIcon = false,
      ).apply(
        editModeIndicator(editMode),
      )
    }

  def nodeCardEditableOnClick(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
    val editMode = Var(false)
    renderNodeCard(
      node,
      contentInject = node => VDomModifier(
        prependInject,
        editableNodeOnClick( node, maxLength).apply(nodeInject),
        contentInject
      ),
    ).apply(
      editModeIndicator(editMode),
    )
  }

    def taskCheckbox( node:Node, directParentIds:Iterable[NodeId])(implicit ctx: Ctx.Owner):VNode = {
      val isChecked:Rx[Boolean] = Rx {
        if(directParentIds.isEmpty) false
        else {
          val graph = GlobalState.graph()
          val nodeIdx = graph.idToIdxOrThrow(node.id)
          @inline def nodeIsDoneInParent(parentId:NodeId) = {
            val parentIdx = graph.idToIdxOrThrow(parentId)
            val workspaces = graph.workspacesForParent(parentIdx)
            graph.isDoneInAllWorkspaces(nodeIdx, workspaces)
          }
          directParentIds.forall(nodeIsDoneInParent)
        }
      }

      div(
        cls := "ui checkbox fitted",
        input(
          tpe := "checkbox",
          checked <-- isChecked,
          VDomModifier.ifTrue(directParentIds.isEmpty)(disabled := true),
          onClick.stopPropagation.discard, // fix safari emitting extra click event onChange
          onChange.checked foreach { checking =>
            val graph = GlobalState.graph.now
            directParentIds.flatMap(id => graph.workspacesForParent(graph.idToIdxOrThrow(id))).foreach { workspaceIdx =>
              val doneIdx = graph.doneNodeForWorkspace(workspaceIdx)

              if(checking) {
                val (doneNodeId, doneNodeAddChange) = doneIdx match {
                  case None                   =>
                    val freshDoneNodeId = NodeId.fresh()
                    (freshDoneNodeId, GraphChanges.addDoneStage(freshDoneNodeId, ParentId(graph.nodeIds(workspaceIdx))))
                  case Some(existingDoneNode) => (graph.nodeIds(existingDoneNode), GraphChanges.empty)
                }
                val stageParents = graph.parentsIdx(graph.idToIdxOrThrow(node.id)).collect{case idx if graph.nodes(idx).role == NodeRole.Stage && graph.workspacesForParent(idx).contains(workspaceIdx) => graph.nodeIds(idx)}
                val changes = doneNodeAddChange merge GraphChanges.changeSource(Edge.Child)(ChildId(node.id)::Nil, ParentId(stageParents), ParentId(doneNodeId)::Nil)
                GlobalState.submitChanges(changes)
                FeatureState.use(Feature.CheckTask)
              } else { // unchecking
                // since it was checked, we know for sure, that a done-node for every workspace exists
                val changes = GraphChanges.disconnect(Edge.Child)(doneIdx.map(idx => ParentId(graph.nodeIds(idx))), ChildId(node.id))
                GlobalState.submitChanges(changes)
                FeatureState.use(Feature.UncheckTask)
              }
            }
          }
        ),
        label()
      )
    }


    def zoomButton( nodeId:NodeId) = {
      div(
        Icons.zoom,
        cursor.pointer,
        onClick.stopPropagation.foreach {
          GlobalState.focus(nodeId)
          ()
        }
      )
    }


    def nodeCardWithCheckbox( node: Node, directParentIds:Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode = {
      nodeCard( node).prepend(
        Styles.flex,
        alignItems.flexStart,
        taskCheckbox( node, directParentIds)
      )
    }

    def editableNodeOnClick(
      node: Node,
      maxLength: Option[Int] = None,
      editMode: Var[Boolean] = Var(false),
      config: EditableContent.Config = EditableContent.Config.cancelOnError
    )(
      implicit ctx: Ctx.Owner
    ): VNode = {
      editableNode( node, editMode, maxLength, config)(ctx)(
        onClick.stopPropagation foreach {
          if(!editMode.now) {
            editMode() = true
          }
        },
        minWidth := "20px", minHeight := "10px", // minimal clicking area
      )
    }

    def editablePropertyNodeOnClick(node: Node, edge: Edge.LabeledProperty, nonPropertyModifier: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, editMode: Var[Boolean] = Var(false), config: EditableContent.Config = EditableContent.Config.cancelOnError)(
      implicit ctx: Ctx.Owner
    ): VNode = {
      editablePropertyNode( node, edge, editMode, nonPropertyModifier, maxLength, config)(ctx)(
        onClick.stopPropagation foreach {
          if(!editMode.now) {
            editMode() = true
          }
        },
        minWidth := "20px", minHeight := "10px", // minimal clicking area
      )
    }

    def editableNode(node: Node, editMode: Var[Boolean], maxLength: Option[Int] = None, config: EditableContent.Config = EditableContent.Config.cancelOnError)(implicit ctx: Ctx.Owner): VNode = {
      div(
        EditableContent.ofNodeOrRender( node, editMode, implicit ctx => node => renderNodeData( node, maxLength), config).editValue.map(GraphChanges.addNode).foreach{ changes =>
          GlobalState.submitChanges(changes)
          changes.addNodes.head.role match {
            case NodeRole.Project => FeatureState.use(Feature.EditProjectInRightSidebar)
            case NodeRole.Task => FeatureState.use(Feature.EditTaskInRightSidebar)
            case NodeRole.Message => FeatureState.use(Feature.EditMessageInRightSidebar)
            // case NodeRole.Note => FeatureState.use(Feature.EditNoteInRightSidebar)
            case NodeRole.Stage => FeatureState.use(Feature.EditColumnInKanban) //TODO: why is this triggered two times?
            case _ =>
          }
        }
      )
    }

    def editablePropertyNode(node: Node, edge: Edge.LabeledProperty, editMode: Var[Boolean], nonPropertyModifier: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, config: EditableContent.Config = EditableContent.Config.cancelOnError)(implicit ctx: Ctx.Owner): VNode = {

      def contentEditor = EditableContent.ofNodeOrRender( node, editMode, implicit ctx => node => renderNodeData( node, maxLength), config).editValue.map(GraphChanges.addNode) --> GlobalState.eventProcessor.changes

      def refEditor = EditableContent.customOrRender[NodeId](
        node.id, editMode,
        implicit ctx => nodeId => GlobalState.rawGraph.map(g => nodeCard(g.nodesByIdOrThrow(nodeId), maxLength = maxLength).apply(Styles.wordWrap, nonPropertyModifier)),
        implicit ctx => handler => searchAndSelectNodeApplied[Handler](
          ProHandler(
            handler.edit.contramap[Option[NodeId]](EditInteraction.fromOption(_)),
            handler.edit.collect[Option[NodeId]] { case EditInteraction.Input(id) => Some(id) }.prepend(Some(node.id)).replayLatest
          ),
          filter = (_:Node) => true
        ),
        config
      ).editValue.collect { case newNodeId if newNodeId != edge.propertyId =>
        GraphChanges(delEdges = Array(edge), addEdges = Array(edge.copy(propertyId = PropertyId(newNodeId))))
      } --> GlobalState.eventProcessor.changes

      div(
        (node.role, node.data) match {
          case (_, NodeData.Placeholder(Some(NodeTypeSelection.Ref))) => refEditor
          case (_, NodeData.Placeholder(Some(NodeTypeSelection.Data(_)))) => contentEditor
          case (NodeRole.Neutral, _) => contentEditor
          case (_, _) => refEditor
        }
      )
    }

    def searchAndSelectNodeApplied[F[_] : Sink : Source](current: F[Option[NodeId]], filter: Node => Boolean)(implicit ctx: Ctx.Owner): VNode = searchAndSelectNode(current, filter) --> current
    def searchAndSelectNode[F[_] : Source](observable: F[Option[NodeId]], filter: Node => Boolean)(implicit ctx: Ctx.Owner): EmitterBuilder[Option[NodeId], VNode] =
      Components.searchInGraph(GlobalState.rawGraph, "Search", filter = {
            case n: Node.Content => InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Project)(n.role) && filter(n)
            case _ => false
      }, innerElementModifier = width := "100%", inputModifiers = width := "100%").mapResult[VNode] { search =>
        div(
          search,

          SourceStream.map(observable) {
            case Some(nodeId) => div(
              marginTop := "4px",
              Styles.flex,
              alignItems.flexStart,
              justifyContent.spaceBetween,
              span("Selected:", color.gray, margin := "0px 5px 0px 5px"),
              GlobalState.graph.map { g =>
                val node = g.nodesByIdOrThrow(nodeId)
                Components.nodeCard(node, maxLength = Some(100)).apply(Styles.wordWrap)
              }
            )
            case None => VDomModifier.empty
          }
        )
      }.map(Some(_))

    def searchInGraph(graph: Rx[Graph], placeholder: String, valid: Rx[Boolean] = Var(true), filter: Node => Boolean = _ => true, completeOnInit: Boolean = true, showNotFound: Boolean = true, elementModifier: VDomModifier = VDomModifier.empty, innerElementModifier: VDomModifier = VDomModifier.empty, inputModifiers: VDomModifier = VDomModifier.empty, resultsModifier: VDomModifier = VDomModifier.empty, createNew: String => Boolean = _ => false, inputDecoration: Option[VDomModifier] = None)(implicit ctx: Ctx.Owner): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => VDomModifier.delay {
      var inputElem: dom.html.Element = null
      var resultsElem: dom.html.Element = null
      var elem: JQuerySelection = null

      def initSearch(): Unit = {
        elem.search(arg = new SearchOptions {
          `type` = "node"

          cache = false
          searchOnFocus = true
          minCharacters = 0
          showNoResults = showNotFound

          source = graph.now.nodes.collect { case node: Node if filter(node) =>
            val str = node match {
              case user: Node.User => Components.displayUserName(user.data)
              case _ => node.str
            }

            new SearchSourceEntry {
              title = node.id.toCuidString
              description = trimToMaxLength(str, 36)
              data = node.asInstanceOf[js.Any]
            }
          }(breakOut): js.Array[SearchSourceEntry]

          searchFields = js.Array("description")

          onSelect = { (selected, results) =>
            submitResult(selected)
            true
          }: js.Function2[SearchSourceEntry, js.Array[SearchSourceEntry], Boolean]
        })
      }

      def resetSearch(forceClose: Boolean): Unit = {
        elem.search("set value", "")

        if (forceClose) {
          elem.search("destroy")
          //TODO only reliable way to make search work again after manual submission with enter
          inputElem.blur()
          dom.window.setTimeout(() => inputElem.focus, timeout = 20)
        }
      }

      def submitResult(value: SearchSourceEntry, forceClose: Boolean = false): Unit = {
        val id = value.data.asInstanceOf[Node].id
        sink.onNext(id)
        resetSearch(forceClose)
      }
      def submitNew(value: String, forceClose: Boolean = false): Unit = {
        if (value.nonEmpty && createNew(value)) resetSearch(forceClose)
      }

      val rawInput = input(
        borderRadius := "4px",
        cls := "prompt",
        tpe := "text",
        dsl.placeholder := placeholder,
        inputModifiers,

        onDomMount.asHtml.foreach { e => inputElem = e },

        onEnter.stopPropagation.foreach { e =>
          val inputElem = e.target.asInstanceOf[dom.html.Input]
          val searchString = inputElem.value
          if (resultsElem != null) defer {  // we defer so the new results for the current search are guaranteed to be rendered
            // ugly: get the title element from the currently active or alternatively first result
            // the titleElement contains the cuid of the result which we can map back to a search entry to select.
            val titleElem = Option(resultsElem.querySelector(".result.active > .title")) orElse Option(resultsElem.querySelector(".result > .title"))
            titleElem match {
              case Some(titleElem) => elem.search("get result", titleElem.textContent) match {
                case v if v == false || v == js.undefined || v == null => submitNew(searchString, forceClose = true)
                case obj => submitResult(obj.asInstanceOf[SearchSourceEntry], forceClose = true)
              }

              case None => submitNew(searchString, forceClose = true)
            }
          }
        },

        onFocus.foreach { _ =>
          initSearch()
          if (completeOnInit) elem.search("search local", "")
        },

        valid.map(_.ifFalse[VDomModifier](borderColor := "tomato"))
      )

      val inputAppendix = VDomModifier(
        div(
          cls := "results",
          width := "100%", // overwrite hardcoded width of result from fomantic ui
          resultsModifier,
          onDomMount.asHtml.foreach(resultsElem = _)
        ),

        managedElement.asJquery { e =>
          elem = e
          cancelable(() => e.search("destroy"))
        }
      )

      div(
        keyed, // because search does weird things to the dom element.
        elementModifier,
        cls := "ui search",
        div(
          rawInput,
          innerElementModifier,
          inputDecoration.getOrElse(VDomModifier(
            cls := "ui icon input",
            i(cls := "search icon"),
          ))
        ),
        inputAppendix
      )
    })

  def removeableList[T](elements: Seq[T], removeSink: SinkObserver[T], tooltip: Option[String] = None)(renderElement: T => VDomModifier): VNode = {
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
            onClick.stopPropagation.use(element) --> removeSink
          ),
        )
      }
    )
  }

  def automatedNodesOfNode(nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val automationInfo: Rx[(Either[Boolean, Seq[(Node, Node)]], Seq[(EdgeData.ReferencesTemplate, Node)])] = Rx {
      val graph = GlobalState.rawGraph()
      graph.idToIdxFold[(Either[Boolean, Seq[(Node, Node)]], Seq[(EdgeData.ReferencesTemplate, Node)])](nodeId)(Left(false) -> Seq.empty) { nodeIdx =>
        val references: Seq[(EdgeData.ReferencesTemplate, Node)] = graph.referencesTemplateEdgeIdx.map(nodeIdx) { edgeIdx =>
          graph.edges(edgeIdx).as[Edge.ReferencesTemplate].data -> graph.nodes(graph.edgesIdx.b(edgeIdx))
        }
        val nodes: Seq[(Node, Node)] = graph.automatedEdgeReverseIdx.flatMap(nodeIdx) { edgeIdx =>
          val automatedNodeIdx = graph.edgesIdx.a(edgeIdx)
          graph.workspacesForParent(automatedNodeIdx).map { workspaceIdx =>
            (graph.nodes(automatedNodeIdx), graph.nodes(workspaceIdx))
          }
        }
        if (nodes.isEmpty) Left(graph.selfOrParentIsAutomationTemplate(nodeIdx)) -> references
        else Right(nodes) -> references
      }
    }

    Rx {
      val autoInfo = automationInfo()

      def renderAutomatedNodes = autoInfo._1 match {
        case Left(true) => div(
          marginLeft := "2px",
          Icons.automate,
          UI.popup("bottom center") := s"This node is part of an automation template",
        )
        case Right(automatedNodes) if automatedNodes.nonEmpty => VDomModifier(
          automatedNodes.map { case (node, workspace) =>
            div(
              marginLeft := "2px",
              borderRadius := "2px",
              UI.popup("bottom center") := s"This node is an active automation template for: ${StringOps.trimToMaxLength(node.str, 30)}",
              fontSize.xSmall,
              Icons.automate,
              cursor.pointer,
              color := tagColor(node.id).toHex,
              onClick.stopPropagation.foreach {
                GlobalState.focus(workspace.id)
              }
            )
          }
        )
        case _ => VDomModifier.empty
      }

      def renderTemplateReferences = autoInfo._2 match {
        case references if references.nonEmpty => VDomModifier(
          references.map { case (edgeData, node) =>
            val referenceModifiers = edgeData.modifierStrings
            val referenceModifierString = if (referenceModifiers.isEmpty) "" else "(" + referenceModifiers.mkString(", ") + ")"

            div(
              marginLeft := "2px",
              Styles.flex,
              alignItems.center,
              fontSize.xSmall,
              Icons.templateReference,
              renderAsOneLineText(node).apply(maxWidth := "150px"),
              UI.popup("bottom center") := s"This node has a template reference$referenceModifierString",
            )
          }
        )
        case _ => VDomModifier.empty
      }

      div(
        overflow.hidden,
        Styles.flex,
        alignItems.center,
        float.right,
        maxWidth := "200px",
        flexWrap.wrap,
        marginLeft := "4px",
        renderAutomatedNodes,
        renderTemplateReferences,
      )
    }
  }

  final case class MenuItem(title: VDomModifier, description: VDomModifier, active: Rx[Boolean], clickAction: () => Unit)
  object MenuItem {
    def apply(title: VDomModifier, description: VDomModifier, active: Boolean, clickAction: () => Unit): MenuItem =
      new MenuItem(title, description, Var(active), clickAction)
  }
  def verticalMenu(items: Seq[MenuItem])(implicit ctx: Ctx.Owner): VNode = menu(
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
      justifyContent.flexStart,
      alignItems.center,
      paddingBottom := "10px"
    )
  )
  def horizontalMenu(items: Seq[MenuItem], itemWidth:String = "50px")(implicit ctx: Ctx.Owner): VNode = menu(
    items.map(item => item.copy(title = VDomModifier(item.title, marginBottom := "5px"))),
    outerModifier = VDomModifier(
      Styles.flex,
      justifyContent.spaceEvenly,
      alignItems.flexStart,
    ),
    innerModifier = VDomModifier(
      flexGrow := 0,
      width := itemWidth,
      Styles.flex,
      flexDirection.column,
      alignItems.center,
    )
  )

  def menu(items: Seq[MenuItem], innerModifier: VDomModifier, outerModifier: VDomModifier)(implicit ctx: Ctx.Owner): VNode = {
    div(
      paddingTop := "10px",
      outerModifier,

      items.map { item =>
        div(
          cls := "components-menu-item",
          Rx {
            if(item.active()) cls := "active" else cls := "inactive"
          },
          div(item.title),
          div(item.description),
          onClick.foreach { item.clickAction() },
          cursor.pointer,

          innerModifier
        )
      }
    )
  }

  def sidebarNodeFocusMod(nodeId: NodeId, focusState:FocusState)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNodeFocusClickMod(nodeId, focusState),
    sidebarNodeFocusVisualizeMod(focusState.itemIsFocused(nodeId))
  )

  def sidebarNodeFocusClickMod(nodeId: NodeId, focusState: FocusState)(implicit ctx: Ctx.Owner): VDomModifier = {
    onSingleOrDoubleClick(
      singleClickAction = { () =>
        focusState.onItemSingleClick(FocusPreference(nodeId))
      },
      doubleClickAction = { () =>
        focusState.onItemDoubleClick(nodeId)
      }
    )
  }

  def sidebarNodeFocusVisualizeMod(isFocused: Rx[Boolean])(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    Rx { 
      VDomModifier.ifTrue(isFocused())(boxShadow := s"inset 0 0 0px 2px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )


  //TODO implement like sidebarNodeFocusVisualizeMod
  def sidebarNodeFocusVisualizeRightMod(sidebarNode: Rx[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNode.map(_.exists(_.nodeId == nodeId)).map { isFocused =>
      VDomModifier.ifTrue(isFocused)(boxShadow := s"2px 0px 1px -1px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )

  def showHoveredNode(nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier.ifNot(BrowserDetect.isMobile)(
    GlobalState.hoverNodeId.map {
      case Some(`nodeId`) => boxShadow := s"inset 0 0 1px 1px gray"
      case _ => VDomModifier.empty
    }
  )
  def writeHoveredNode(nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier.ifNot(BrowserDetect.isMobile)(
    onMouseOver.use(Some(nodeId)) --> GlobalState.hoverNodeId,
    onMouseOut.use(None) --> GlobalState.hoverNodeId,
  )

  def maturityLabel(text: String, fgColor: String = "#95a90b", borderColor: String = "#d9e778") = {
    div(
      text,
      border := s"1px solid $borderColor",
      color := fgColor,
      borderRadius := "3px",
      padding := "0px 5px",
      fontWeight.bold,
      // styles.extra.transform := "rotate(-7deg)",

      marginRight := "5px",
    )
  }

  def betaSign(implicit ctx:Ctx.Owner) = maturityLabel("beta").apply (
    onMultiClickActivateDebugging
  )

  def onMultiClickActivateDebugging(implicit ctx:Ctx.Owner) = VDomModifier(
    cls := "fs-ignore-rage-clicks fs-ignore-dead-clicks", // https://help.fullstory.com/hc/en-us/articles/360020622734
    Elements.onClickN(desiredClicks = if(DevOnly.isTrue) 1 else 8).foreach {
      DebugOnly.isTrueSetting = true
      Logging.setup()
      wust.webApp.state.GlobalStateFactory.setupStateDebugLogging()
      DeployedOnly(dom.window.alert(s"Woost version: ${WoostConfig.value.versionString}\nLogging and DevOnly is now enabled"))
      ()
    }
  )

  def experimentalSign(color: String) = maturityLabel("experimental", fgColor = color, borderColor = color)

  def reloadButton = button(cls := "ui button compact mini", freeSolid.faRedo, " Reload", cursor.pointer, onClick.stopPropagation.foreach { dom.window.location.reload(flag = true) }) // true - reload without cache
}
