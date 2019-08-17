package wust.webApp.views

import acyclic.file
import wust.webUtil.outwatchHelpers._
import cats.effect.IO
import wust.facades.emojijs.EmojiConvertor
import wust.facades.fomanticui.{SearchOptions, SearchSourceEntry, AutoResizeConfig}
import wust.facades.jquery.JQuerySelection
import wust.facades.marked.Marked
import fontAwesome._
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import wust.webUtil.Elements._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{BrowserDetect, Elements, Ownable, UI}
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids.{Feature, _}
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, Colors, NodeColor}
import wust.util.StringOps._
import wust.util._
import wust.util.macros.InlineList
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.state.{PageStyle, EmojiReplacer, FocusPreference, GlobalState, InputMention}
import wust.webApp.views.UploadComponents._

import scala.collection.{breakOut, mutable}
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import state.FeatureState

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
    case data: NodeData.Placeholder   => div(displayPlaceholder(data))
    case d                           => div(trimToMaxLength(d.str, maxLength))
  }

  def replaceEmoji(str: String): VNode = {
    span.thunkStatic(uniqueKey(str))(VDomModifier(
      Elements.innerHTML := Elements.UnsafeHTML(EmojiConvertor.replace_colons(escapeHtml(str)))
    ))
  }

  def replaceEmojiUnified(str: String): VNode = {
    span.thunkStatic(uniqueKey(str))(VDomModifier(
      Elements.innerHTML := Elements.UnsafeHTML(EmojiConvertor.replace_unified(escapeHtml(str)))
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
              GlobalState.urlConfig.update(_.focus(Page(nodeId), View.Conversation, needsGet = false))
              ()
          }
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
    parentIdAction: Option[NodeId] => Unit,
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
        EditableContent.inlineEditorOrRender[String](key, editKey, _ => key => span(key + ":")).editValue.collect { case newKey if newKey != key =>
          GraphChanges(addEdges = properties.map(p => p.edge.copy(data = p.edge.data.copy(key = newKey)))(breakOut), delEdges = properties.map(_.edge)(breakOut)),
        } --> GlobalState.eventProcessor.changes,
        cursor.pointer,
        onClick.stopPropagation(true) --> editKey,
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
                nonPropertyModifier = VDomModifier(writeHoveredNode( property.node.id), cursor.pointer, onClick.stopPropagation(Some(property.node.id)).foreach(parentIdAction(_))),
                maxLength = Some(100), config = EditableContent.Config.default,
              ),

              div(
                marginLeft := "5px",
                cursor.pointer,
                editValue.map {
                  case true => VDomModifier(
                    Icons.delete,
                    onClick(GraphChanges(delEdges = Array(property.edge))) --> GlobalState.eventProcessor.changes
                  )
                  case false => VDomModifier(
                    Icons.edit,
                    onClick.stopPropagation(true) --> editValue
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
    
    key: Edge.LabeledProperty,
    property: Node,
    pageOnClick: Boolean = false,
  )(implicit ctx: Ctx.Owner): VNode = {

    span(
      cls := "property",
      Styles.flex,
      alignItems.center,

      DragComponents.drag(DragItem.Property(key), target = DragItem.DisableDrag),

      div(
        alignSelf.flexStart,
        s"${key.data.key}:",
        marginRight := "4px",
      ),

      property.role match {
        case NodeRole.Neutral =>
          renderNodeData( property, maxLength = Some(50))
            .apply(cls := "property-value")
        case _ =>
          VDomModifier(
            writeHoveredNode( property.id),
            nodeCard( property, maxLength = Some(50)).apply(
              cls := "property-value",
              margin := "3px 0",
              sidebarNodeFocusMod(GlobalState.rightSidebarNode, property.id),
              cursor.pointer
            ),
          )
      }
    )
  }

  def removableUserAvatar(userNode: Node.User, targetNodeId: NodeId): VNode = {
    div(
      Styles.flexStatic,
      Avatar.user(userNode.id)(
        marginRight := "2px",
        width := "22px",
        height := "22px",
        cls := "avatar",
      ),
      keyed(userNode.id),
      UI.tooltip("left center") := s"${displayUserName(userNode.data)}. Click to unassign.",
      cursor.pointer,
      onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(targetNodeId, userNode.id)) --> GlobalState.eventProcessor.changes,
      DragComponents.drag(DragItem.User(userNode.id), target = DragItem.DisableDrag),
    )
  }

  def removableNodeCardPropertyCustom(key: Edge.LabeledProperty, propertyNode: Node, action: () => Unit, pageOnClick: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    nodeCardProperty( key, propertyNode, pageOnClick).apply(removableTagMod(action))
  }

  def removableNodeCardProperty(key: Edge.LabeledProperty, propertyNode: Node, pageOnClick:Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    removableNodeCardPropertyCustom( key, propertyNode, () => {
      GlobalState.submitChanges(
        GraphChanges(delEdges = Array(key))
      )
    }, pageOnClick)
  }


    def removableAssignedUser(user: Node.User, assignedNodeId: NodeId): VNode = {
      renderUser(user).apply(
        removableTagMod(() => GlobalState.submitChanges(GraphChanges.disconnect(Edge.Assigned)(assignedNodeId, user.id)))
      )
    }

    def renderUser(user: Node.User, size:Int = 20): VNode = {
      div(
        padding := "2px",
        borderRadius := "3px",
        backgroundColor := "white",
        color.black,
        Styles.flex,
        alignItems.center,
        Avatar.user(user.id)(height := s"${size}px"),
        div(marginLeft := "5px", displayUserName(user.data), Styles.wordWrap),
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
          case n@Node.Content(_, editable: NodeData.EditableText, _, _, _) =>
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
          case user: Node.User => renderUser(user, size = 14)
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
        keyed(node.id),
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
        Rx { editMode().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },
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
      Rx { editMode().ifTrue[VDomModifier](VDomModifier(boxShadow := "0px 0px 0px 2px  rgba(65,184,255, 1)")) },
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
                    val freshDoneNode = Node.MarkdownStage(Graph.doneText)
                    (freshDoneNode.id, GraphChanges.addNodeWithParent(freshDoneNode, ParentId(graph.nodeIds(workspaceIdx))))
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

  def nodeAvatar(node: Node, size: Int): VNode = {
      Avatar(node)(
        width := s"${ size }px",
        height := s"${ size }px"
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
        minWidth := "20px", minHeight := "20px", // minimal clicking area
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
        minWidth := "20px", minHeight := "20px", // minimal clicking area
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

      def refEditor = EditableContent.customOrRender[Node](node, editMode,
        implicit ctx => node => nodeCard( node, maxLength = maxLength).apply(Styles.wordWrap, nonPropertyModifier),
        implicit ctx => handler => searchAndSelectNodeApplied( handler.edit.collectHandler[Option[NodeId]] { case id => EditInteraction.fromOption(id.map(GlobalState.rawGraph.now.nodesByIdOrThrow(_))) } { case EditInteraction.Input(v) => Some(v.id) }.transformObservable(_.prepend(Some(node.id))), filter = (_:Node) => true), config
      ).editValue.collect { case newNode if newNode.id != edge.propertyId => GraphChanges(delEdges = Array(edge), addEdges = Array(edge.copy(propertyId = PropertyId(newNode.id)))) } --> GlobalState.eventProcessor.changes

      div(
        (node.role, node.data) match {
          case (_, NodeData.Placeholder(Some(NodeTypeSelection.Ref))) => refEditor
          case (_, NodeData.Placeholder(Some(NodeTypeSelection.Data(_)))) => contentEditor
          case (NodeRole.Neutral, _) => contentEditor
          case (_, _) => refEditor
        }
      )
    }

    def searchAndSelectNodeApplied(current: Var[Option[NodeId]], filter: Node => Boolean)(implicit ctx: Ctx.Owner): VNode = searchAndSelectNode( current.toObservable, filter: Node => Boolean) --> current
    def searchAndSelectNodeApplied(current: Handler[Option[NodeId]], filter: Node => Boolean)(implicit ctx: Ctx.Owner): VNode = searchAndSelectNode( current, filter) --> current
    def searchAndSelectNode(observable: Observable[Option[NodeId]], filter: Node => Boolean)(implicit ctx: Ctx.Owner): EmitterBuilder[Option[NodeId], VNode] =
      Components.searchInGraph(GlobalState.rawGraph, "Search", filter = {
            case n: Node.Content => InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Project)(n.role) && filter(n)
            case _ => false
      }, innerElementModifier = width := "100%", inputModifiers = width := "100%").mapResult[VNode] { search =>
        div(
          search,

          observable.map[VDomModifier] {
            case Some(nodeId) => div(
              marginTop := "4px",
              Styles.flex,
              alignItems.flexStart,
              justifyContent.spaceBetween,
              span("Selected:", color.gray, margin := "0px 5px 0px 5px"),
              GlobalState.graph.map { g =>
                val node = g.nodesByIdOrThrow(nodeId)
                Components.nodeCard( node, maxLength = Some(100)).apply(Styles.wordWrap)
              }
            )
            case None => VDomModifier.empty
          }
        )
      }.map(Some(_))

    def searchInGraph(graph: Rx[Graph], placeholder: String, valid: Rx[Boolean] = Var(true), filter: Node => Boolean = _ => true, completeOnInit: Boolean = true, showNotFound: Boolean = true, elementModifier: VDomModifier = VDomModifier.empty, innerElementModifier: VDomModifier = VDomModifier.empty, inputModifiers: VDomModifier = VDomModifier.empty, resultsModifier: VDomModifier = VDomModifier.empty, createNew: String => Boolean = _ => false)(implicit ctx: Ctx.Owner): EmitterBuilder[NodeId, VDomModifier] = EmitterBuilder.ofModifier(sink => IO {
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

      div(
        keyed,
        elementModifier,
        cls := "ui search",
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
          ),
          i(cls := "search icon"),
        ),
        div(
          cls := "results",
          width := "100%", // overwrite hardcoded width of result from fomantic ui
          resultsModifier,
          onDomMount.asHtml.foreach(resultsElem = _)
        ),

        managedElement.asJquery { e =>
          elem = e
          Cancelable(() => e.search("destroy"))
        }
      )
    })

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
      justifyContent.flexStart,
      alignItems.center,
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
            if(item.active()) VDomModifier(fontWeight.bold) else opacity := 0.4
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

  def sidebarNodeFocusClickMod(sidebarNode: Var[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = sidebarNodeFocusClickMod(sidebarNode, sidebarNode() = _, nodeId)

  def sidebarNodeFocusClickMod(sidebarNode: Rx[Option[FocusPreference]], onSidebarNode: Option[FocusPreference] => Unit, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val sidebarNodeOpenDelay = {
      import concurrent.duration._
      200 milliseconds
    }
    var dblClicked = false
    VDomModifier(
      cursor.pointer,
      onMouseDown.stopPropagation.discard, // don't globally close sidebar by clicking here. Instead onClick toggles the sidebar directly
      onClick.stopPropagation.transform(_.delayOnNext(sidebarNodeOpenDelay)).foreach {
        // opening right sidebar is delayed to not interfere with double click
        if(dblClicked) dblClicked = false
        else {
          val nextNode = if (sidebarNode.now.exists(_.nodeId == nodeId)) None else Some(FocusPreference(nodeId))
          onSidebarNode(nextNode)
        }
      },
      onDblClick.stopPropagation.foreach{ _ =>
        dblClicked = true
        GlobalState.focus(nodeId)
        GlobalState.graph.now.nodesById(nodeId).foreach { node =>
          node.role match {
            case NodeRole.Task => FeatureState.use(Feature.ZoomIntoTask)
            case NodeRole.Message => FeatureState.use(Feature.ZoomIntoMessage)
            case NodeRole.Note => FeatureState.use(Feature.ZoomIntoNote)
            case _ => 
          }
        }
        
      },
    )
  }

  def sidebarNodeFocusVisualizeMod(sidebarNode: Rx[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    sidebarNode.map(_.exists(_.nodeId == nodeId)).map { isFocused =>
      VDomModifier.ifTrue(isFocused)(boxShadow := s"inset 0 0 0px 2px ${CommonStyles.selectedNodesBgColorCSS}")
    }
  )
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
    onMouseOver(Some(nodeId)) --> GlobalState.hoverNodeId,
    onMouseOut(None) --> GlobalState.hoverNodeId,
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
    Elements.onClickN(desiredClicks = if(DevOnly.isTrue) 1 else 8).foreach {
      Logging.setup(enabled = true, debugEnabled = true)
      wust.webApp.state.GlobalStateFactory.setupStateDebugLogging()
      DeployedOnly(dom.window.alert(s"Woost version: ${WoostConfig.value.versionString}\nLogging and DevOnly is now enabled"))
      ()
    }
  )

  def experimentalSign(color: String) = maturityLabel("experimental", fgColor = color, borderColor = color)

  def reloadButton = button(cls := "ui button compact mini", freeSolid.faRedo, " Reload", cursor.pointer, onClick.stopPropagation.foreach { dom.window.location.reload(flag = true) }) // true - reload without cache

  def autoresizeTextareaMod: VDomModifier = autoresizeTextareaMod()
  def autoresizeTextareaMod(maxHeight: Option[Int] = None, onResize: Option[() => Unit] = None): VDomModifier = {
    val _maxHeight = maxHeight.map(_.toDouble).orUndefined
    val _onResize = onResize.map[js.ThisFunction1[dom.html.TextArea, Double, Unit]](f => (_: dom.html.TextArea, _: Double) => f()).orUndefined

    managedElement.asJquery { e =>
      val subscription = e.autoResize(new AutoResizeConfig { maxHeight = _maxHeight; onresizeheight = _onResize })
      Cancelable(() => subscription.reset())
    }
  }
}
