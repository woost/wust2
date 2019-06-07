package wust.webApp.views

import scala.scalajs.js.JSConverters._
import cats.effect.IO
import emojijs.EmojiConvertor
import fomanticui.{SearchOptions, SearchSourceEntry}
import fontAwesome._
import jquery.JQuerySelection
import marked.Marked
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.dom.helpers.EmitterBuilder
import rx._
import webUtil.Elements._
import webUtil.outwatchHelpers._
import webUtil.{BrowserDetect, Elements, Ownable, UI}
import wust.css.{CommonStyles, Styles}
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor._
import wust.sdk.{BaseColors, Colors, NodeColor}
import wust.util.StringOps._
import wust.util._
import wust.util.macros.InlineList
import wust.webApp._
import wust.webApp.dragdrop._
import wust.webApp.jsdom.{IntersectionObserver, IntersectionObserverOptions}
import wust.webApp.state.{EmojiReplacer, FocusPreference, GlobalState}
import wust.webApp.views.UploadComponents._

import scala.collection.breakOut
import scala.scalajs.js

// This file contains woost-related UI helpers.

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

  def displayDuration(data: NodeData.Duration) = span(StringJsOps.durationToString(data.content))
  def displayRelativeDate(data: NodeData.RelativeDate) = VDomModifier(span(color := "lightgray", "X + "), span(StringJsOps.durationToString(data.content)))
  def displayDate(data: NodeData.Date) = span(StringJsOps.dateToString(data.content))
  def displayDateTime(data: NodeData.DateTime) = span(StringJsOps.dateTimeToString(data.content))

  def renderNodeData(nodeData: NodeData, maxLength: Option[Int] = None): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case data: NodeData.RelativeDate => div(displayRelativeDate(data))
    case data: NodeData.Date         => div(displayDate(data))
    case data: NodeData.DateTime     => div(displayDateTime(data))
    case data: NodeData.Duration     => div(displayDuration(data))
    case d                           => div(trimToMaxLength(d.str, maxLength))
  }

  def renderNodeDataWithFile(state: GlobalState, nodeId: NodeId, nodeData: NodeData, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = nodeData match {
    case NodeData.Markdown(content)  => markdownVNode(trimToMaxLength(content, maxLength))
    case NodeData.PlainText(content) => div(trimToMaxLength(content, maxLength))
    case user: NodeData.User         => div(displayUserName(user))
    case file: NodeData.File         => renderUploadedFile(state, nodeId,file)
    case data: NodeData.RelativeDate => div(displayRelativeDate(data))
    case data: NodeData.Date         => div(displayDate(data))
    case data: NodeData.DateTime     => div(displayDateTime(data))
    case data: NodeData.Duration     => div(displayDuration(data))
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
      val lines = node.str.lines
      if (lines.hasNext) lines.next else ""
    }

    // 2. render markdown
    markdownVNode(firstLine)(
      // 3. overwrite font styles via css
      // 4. crop via overflow ellipsis
      cls := "oneline" 
    )
  }

  def nodeCardAsOneLineText(node: Node, projectWithIcon: Boolean = false): VNode = {
    renderNodeCard(node, contentInject = renderAsOneLineText, projectWithIcon = projectWithIcon)
  }

  def markdownVNode(str: String) = {
    div.thunkStatic(uniqueKey(str))(VDomModifier(
      cls := "markdown",
      div(innerHTML := UnsafeHTML(markdownString(str)))
    )) // intentionally double wrapped. Because innerHtml does not compose with other modifiers
  }

  //TODO: move to webUtil
  def markdownString(str: String): String = {
    if(str.trim.isEmpty) "<p></p>" // add least produce an empty paragraph to preserve line-height
    else EmojiConvertor.replace_colons(Marked(EmojiConvertor.replace_emoticons_with_colons(str)))
  }

  //TODO: move to webUtil
  private def abstractTreeToVNodeRoot(key: String, tree: AbstractElement): VNode = {
    val tag = stringToTag(tree.tag)
    tag.thunkStatic(uniqueKey(key))(treeToModifiers(tree))
  }

  //TODO: move to webUtil
  implicit def renderFontAwesomeIcon(icon: IconLookup): VNode = {
    abstractTreeToVNodeRoot(key = s"${icon.prefix}${icon.iconName}", fontawesome.icon(icon).`abstract`(0))
  }

  // fontawesome uses svg for icons and span for layered icons.
  // we need to handle layers as an html tag instead of svg.
  //TODO: move to webUtil
  @inline private def stringToTag(tag: String): BasicVNode = if (tag == "span") dsl.htmlTag(tag) else dsl.svgTag(tag)
  //TODO: move to webUtil
  @inline private def treeToModifiers(tree: AbstractElement): VDomModifier = VDomModifier(
    tree.attributes.map { case (name, value) => dsl.attr(name) := value }.toJSArray,
    tree.children.fold(js.Array[VNode]()) { _.map(abstractTreeToVNode) }
  )
  //TODO: move to webUtil
  private def abstractTreeToVNode(tree: AbstractElement): VNode = {
    val tag = stringToTag(tree.tag)
    tag(treeToModifiers(tree))
  }

  //TODO: move to webUtil
  @inline implicit def renderFontAwesomeObject(icon: FontawesomeObject): VNode = {
    abstractTreeToVNode(icon.`abstract`(0))
  }

  //TODO: move to webUitl.Elements
  def closeButton: VNode = div(
    div(cls := "fa-fw", freeSolid.faTimes),
    padding := "10px",
    flexGrow := 0.0,
    flexShrink := 0.0,
    cursor.pointer,
  )

  //TODO: move to webUitl.Elements
  def iconWithIndicator(icon: IconLookup, indicator: IconLookup, color: String): VNode = fontawesome.layered(
    fontawesome.icon(icon),
    fontawesome.icon(
      indicator,
      new Params {
        transform = new Transform {size = 13.0; x = 7.0; y = -7.0; }
        styles = scalajs.js.Dictionary[String]("color" -> color)
      }
    )
  )

  //TODO: move to webUitl.Elements
  def icon(icon: VDomModifier) = i(
    cls := "icon fa-fw",
    icon
  )


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
          val previousDmNode: Option[Node] = graph.idToIdxFold(userId)(Option.empty[Node]) { userIdx =>
            graph.chronologicalNodesAscending.find { n =>
              n.str == dmName && graph.idToIdxFold(n.id)(false)(graph.isPinned(_, userIdx))
            }
          } // Max 1 dm node with this name
          previousDmNode match {
            case Some(dmNode) if graph.can_access_node(user.id, dmNode.id) =>
              state.urlConfig.update(_.focus(Page(dmNode.id), View.Conversation, needsGet = false))
            case _ => // create a new channel, add user as member
              val nodeId = NodeId.fresh
              val change:GraphChanges =
                GraphChanges.newProject(nodeId, userId, title = dmName) merge
                GraphChanges(addEdges = Array(
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
        UI.tooltip := s"Start Conversation with ${displayUserName(dmUser.data)}"
      )
    })
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
    properties: Seq[PropertyData.PropertyValue],
  )(implicit ctx: Ctx.Owner): VNode = {

    val editKey = Var(false)

    div(
      width := "100%",
      Styles.wordWrap,
      Styles.flex,
      flexWrap.wrap,
      alignItems.flexStart,
      b(
        Styles.flex,
        EditableContent.inlineEditorOrRender[String](key, editKey, _ => key => span(key + ":")).editValue.collect { case newKey if newKey != key =>
          GraphChanges(addEdges = properties.map(p => p.edge.copy(data = p.edge.data.copy(key = newKey)))(breakOut), delEdges = properties.map(_.edge)(breakOut)),
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
            margin := "3px 0px",

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
                  onClick(GraphChanges(delEdges = Array(property.edge))) --> state.eventProcessor.changes
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


  def nodeCardProperty(
    state: GlobalState,
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
          renderNodeDataWithFile(state, property.id, property.data, maxLength = Some(50))
            .apply(cls := "property-value")
        case _ =>
          VDomModifier(
            writeHoveredNode(state, property.id),
            nodeCardWithFile(state, property, maxLength = Some(50)).apply(
              cls := "property-value",
              margin := "3px 0",
              sidebarNodeFocusMod(state.rightSidebarNode, property.id),
              cursor.pointer
            ),
          )
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
      UI.tooltip("top right") := s"${displayUserName(userNode.data)}. Click to unassign.",
      cursor.pointer,
      onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(targetNodeId, userNode.id)) --> state.eventProcessor.changes,
      DragComponents.drag(DragItem.User(userNode.id), target = DragItem.DisableDrag),
    )
  }

  def removableNodeCardPropertyCustom(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, action: () => Unit, pageOnClick: Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    nodeCardProperty(state, key, propertyNode, pageOnClick).apply(removableTagMod(action))
  }

  def removableNodeCardProperty(state: GlobalState, key: Edge.LabeledProperty, propertyNode: Node, pageOnClick:Boolean = false)(implicit ctx: Ctx.Owner): VNode = {
    removableNodeCardPropertyCustom(state, key, propertyNode, () => {
      state.eventProcessor.changes.onNext(
        GraphChanges(delEdges = Array(key))
      )
    }, pageOnClick)
  }


    def removableAssignedUser(state: GlobalState, user: Node.User, assignedNodeId: NodeId): VNode = {
      renderUser(user).apply(
        removableTagMod(() => state.eventProcessor.changes.onNext(GraphChanges.disconnect(Edge.Assigned)(assignedNodeId, user.id)))
      )
    }

    def renderUser(user: Node.User): VNode = {
      div(
        padding := "2px",
        borderRadius := "3px",
        backgroundColor := "white",
        color.black,
        Styles.flex,
        alignItems.center,
        div(Avatar.user(user.id)(height := "20px")),
        div(marginLeft := "5px", displayUserName(user.data), Styles.wordWrap),
      )
    }


    def nodeTag(
      state: GlobalState,
      tag: Node,
      pageOnClick: Boolean = false,
      dragOptions: NodeId => VDomModifier = nodeId => DragComponents.drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
    ): VNode = {
      val contentString = renderAsOneLineText(tag)
      renderNodeTag(state, tag, VDomModifier(contentString, dragOptions(tag.id)), pageOnClick)
    }

    def removableNodeTagCustom(state: GlobalState, tag: Node, action: () => Unit, pageOnClick:Boolean = false): VNode = {
      nodeTag(state, tag, pageOnClick)(removableTagMod(action))
    }

    def removableNodeTag(state: GlobalState, tag: Node, taggedNodeId: NodeId, pageOnClick:Boolean = false): VNode = {
      removableNodeTagCustom(state, tag, () => {
        state.eventProcessor.changes.onNext(
          GraphChanges.disconnect(Edge.Child)(Array(ParentId(tag.id)), ChildId(taggedNodeId))
        )
      }, pageOnClick)
    }

    def renderProject(node: Node, renderNode: Node => VDomModifier, withIcon: Boolean = false, openFolder: Boolean = false) = {
      if(withIcon) {
        val nodeWithoutFirstEmoji = node match {
          case n@Node.Content(_, editable: NodeData.EditableText, _, _, _) =>
            editable.updateStr(EmojiReplacer.emojiRegex.replaceFirstIn(n.str, "")) match {
              case Some(dataWithoutEmoji) =>
                n.copy(data = dataWithoutEmoji)
              case None                   => n
            }
          case n                                                           => n
        }

        val iconModifier: VNode = node.str match {
          case EmojiReplacer.emojiRegex(emoji) =>
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

    def renderNodeCardMod(node: Node, contentInject: Node => VDomModifier, projectWithIcon: Boolean = false): VDomModifier = {
      def contentNode(node: Node) = div(
        cls := "nodecard-content",
        contentInject(node)
      )

      VDomModifier(
        cls := "nodecard",
        node.role match {
          case NodeRole.Project => VDomModifier(
            cls := "project",
            renderProject(node, contentNode, withIcon = projectWithIcon)
          )
          case NodeRole.Tag => VDomModifier( //TODO merge this definition with renderNodeTag
            cls := "tag colorful",
            backgroundColor := tagColor(node.id).toHex,
            contentNode(node),
          )
          case _ => VDomModifier(
            cls := "node",
            contentNode(node)
          )
        },
      )
    }

    def renderNodeCard(node: Node, contentInject: Node => VDomModifier, projectWithIcon: Boolean = false): VNode = {
      div(
        keyed(node.id),
        renderNodeCardMod(node, contentInject, projectWithIcon = projectWithIcon)
      )
    }
    def nodeCardMod(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VDomModifier = {
      renderNodeCardMod(
        node,
        contentInject = node => VDomModifier(renderNodeData(node.data, maxLength).apply(nodeInject), contentInject)
      )
    }
    def nodeCard(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(renderNodeData(node.data, maxLength).apply(nodeInject), contentInject)
      )
    }
    def nodeCardWithFile(state: GlobalState, node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(renderNodeDataWithFile(state, node.id, node.data, maxLength).apply(nodeInject), contentInject)
      )
    }
    def nodeCardWithoutRender(node: Node, contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(p(StringOps.trimToMaxLength(node.str, maxLength), nodeInject), contentInject)
      )
    }
    def nodeCardEditable(state: GlobalState, node: Node, editMode: Var[Boolean], contentInject: VDomModifier = VDomModifier.empty, nodeInject: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, prependInject: VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VNode = {
      renderNodeCard(
        node,
        contentInject = node => VDomModifier(
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
      contentInject = node => VDomModifier(
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
        val nodeIdx = graph.idToIdxOrThrow(node.id)
        @inline def nodeIsDoneInParent(parentId:NodeId) = {
          val parentIdx = graph.idToIdxOrThrow(parentId)
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


    def zoomButton(state:GlobalState, nodeId:NodeId) = {
      div(
        Icons.zoom,
        cursor.pointer,
        onClick.stopPropagation.foreach {
          state.urlConfig.update(_.focus(Page(nodeId)))
          ()
        }
      )
    }


    def nodeCardWithCheckbox(state:GlobalState, node: Node, directParentIds:Iterable[NodeId])(implicit ctx: Ctx.Owner): VNode = {
      nodeCardWithFile(state, node).prepend(
        Styles.flex,
        alignItems.flexStart,
        taskCheckbox(state, node, directParentIds)
      )
    }

  def nodeAvatar(node: Node, size: Int): VNode = {
      Avatar(node)(
        width := s"${ size }px",
        height := s"${ size }px"
      )
    }

    def editableNodeOnClick(
      state: GlobalState,
      node: Node,
      maxLength: Option[Int] = None,
      editMode: Var[Boolean] = Var(false),
      config: EditableContent.Config = EditableContent.Config.cancelOnError
    )(
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
        EditableContent.ofNodeOrRender(state, node, editMode, implicit ctx => node => renderNodeDataWithFile(state, node.id, node.data, maxLength), config).editValue.map(GraphChanges.addNode) --> state.eventProcessor.changes,
      )
    }

    def editablePropertyNode(state: GlobalState, node: Node, edge: Edge.LabeledProperty, editMode: Var[Boolean], nonPropertyModifier: VDomModifier = VDomModifier.empty, maxLength: Option[Int] = None, config: EditableContent.Config = EditableContent.Config.cancelOnError)(implicit ctx: Ctx.Owner): VNode = {
      div(
        node.role match {
          case NodeRole.Neutral => VDomModifier(
            EditableContent.ofNodeOrRender(state, node, editMode, implicit ctx => node => renderNodeDataWithFile(state, node.id, node.data, maxLength), config).editValue.map(GraphChanges.addNode) --> state.eventProcessor.changes
          )
          case _ => VDomModifier(
            EditableContent.customOrRender[Node](node, editMode,
              implicit ctx => node => nodeCardWithFile(state, node, maxLength = maxLength).apply(Styles.wordWrap, nonPropertyModifier),
              implicit ctx => handler => searchAndSelectNodeApplied(state, handler.edit.collectHandler[Option[NodeId]] { case id => EditInteraction.fromOption(id.map(state.rawGraph.now.nodesByIdOrThrow(_))) } { case EditInteraction.Input(v) => Some(v.id) }.transformObservable(_.prepend(Some(node.id)))), config
            ).editValue.collect { case newNode if newNode.id != edge.propertyId => GraphChanges(delEdges = Array(edge), addEdges = Array(edge.copy(propertyId = PropertyId(newNode.id)))) } --> state.eventProcessor.changes,
          )
        }
      )
    }

    def searchAndSelectNodeApplied(state: GlobalState, current: Var[Option[NodeId]])(implicit ctx: Ctx.Owner): VNode = searchAndSelectNode(state, current.toObservable) --> current
    def searchAndSelectNodeApplied(state: GlobalState, current: Handler[Option[NodeId]])(implicit ctx: Ctx.Owner): VNode = searchAndSelectNode(state, current) --> current
    def searchAndSelectNode(state: GlobalState, observable: Observable[Option[NodeId]])(implicit ctx: Ctx.Owner): EmitterBuilder[Option[NodeId], VNode] =
      Components.searchInGraph(state.rawGraph, "Search", filter = {
            case n: Node.Content => InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task, NodeRole.Project)(n.role)
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
              state.graph.map { g =>
                val node = g.nodesByIdOrThrow(nodeId)
                Components.nodeCardWithFile(state, node, maxLength = Some(100)).apply(Styles.wordWrap)
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

  def automatedNodesOfNode(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = {
    val automatedNodes: Rx[Seq[Node]] = Rx {
      val graph = state.rawGraph()
      graph.idToIdxFold(nodeId)(Seq.empty[Node])(graph.automatedNodes)
    }

    Rx {
      if (automatedNodes().isEmpty) VDomModifier.empty
      else VDomModifier(
        automatedNodes().map { node =>
          div(
            div(background := "repeating-linear-gradient(45deg, yellow, yellow 6px, black 6px, black 12px)", height := "3px"),
            UI.tooltip("bottom center") := "This node is an active automation template")
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

  def sidebarNodeFocusClickMod(sidebarNode: Var[Option[FocusPreference]], nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier(
    cursor.pointer,
    onMouseDown.stopPropagation --> Observer.empty, // don't globally close sidebar by clicking here. Instead onClick toggles the sidebar directly
    onClick.stopPropagation.foreach {
      val nextNode = if (sidebarNode.now.exists(_.nodeId == nodeId)) None else Some(FocusPreference(nodeId))
      sidebarNode() = nextNode
    },
  )

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

  def showHoveredNode(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier.ifNot(BrowserDetect.isMobile)(
    state.hoverNodeId.map {
      case Some(`nodeId`) => boxShadow := s"inset 0 0 1px 1px gray"
      case _ => VDomModifier.empty
    }
  )
  def writeHoveredNode(state: GlobalState, nodeId: NodeId)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier.ifNot(BrowserDetect.isMobile)(
    onMouseOver(Some(nodeId)) --> state.hoverNodeId,
    onMouseOut(None) --> state.hoverNodeId,
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
      Elements.onClickN(desiredClicks = 8).foreach {
        Logging.setup(enabled = true)
        dom.window.alert(s"Woost version: ${woostConfig.WoostConfig.value.versionString}\nLogging is now enabled")
      }
    )
  }

  val betaSign = maturityLabel("beta")
  def experimentalSign(color: String) = maturityLabel("experimental", fgColor = color, borderColor = color)

  val unreadStyle = VDomModifier(
    float.right,
    marginLeft := "5px",
    marginRight := "5px",
  )

  val unreadLabelElement = div(
    cls := "ui label",
    color := "white",
    fontSize.xSmall,
    unreadStyle,
    backgroundColor := Colors.unread,
  )

  val unreadDot = span(
    freeSolid.faCircle,
    color := Colors.unread,
    transition := "color 10s",
    transitionDelay := "5s",
    unreadStyle
  )

  def readObserver(state: GlobalState, nodeId: NodeId, labelModifier:VDomModifier = VDomModifier.empty)(implicit ctx: Ctx.Owner): VDomModifier = {
    def nodeIsRead(graph: Graph, userId: UserId, nodeIdx: Int): Boolean = {
      val node = graph.nodes(nodeIdx)
      if (InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Project, NodeRole.Note, NodeRole.Task)(node.role)) {
        val lastModification = graph.nodeModified(nodeIdx)
        graph.readEdgeIdx.exists(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).as[Edge.Read]
          edge.targetId == userId && edge.data.timestamp >= lastModification
        }
      } else true
    }

    val nodeIdx = state.graph.map(_.idToIdxOrThrow(nodeId))

    val isUnread = Rx {
      val graph = state.graph()
      val user = state.user()

      !nodeIsRead(graph, user.id, nodeIdx())
    }

    val unreadChildren = Rx {
      val graph = state.graph()
      val user = state.user()

      graph.descendantsIdxCount(nodeIdx())(idx => !nodeIsRead(graph, user.id, idx))
    }


    val unreadLabel = unreadLabelElement.apply(
      alignSelf.center,
      labelModifier,
    )

    var observed = Var(false)

    Rx {
      isUnread() match {
        case true => VDomModifier(
          unreadChildren() match {
            case 0 => unreadDot(Rx{VDomModifier.ifTrue(observed())(color := Colors.nodecardBg)})
            case count => unreadLabel(count)
          },

          managedElement.asHtml { elem =>
            val observer = new IntersectionObserver(
              { (entry, observer) =>
                val isIntersecting = entry.head.isIntersecting
                if (isIntersecting && isUnread.now) {
                  val markAsReadChanges = GraphChanges(
                    addEdges = Array(Edge.Read(nodeId, EdgeData.Read(EpochMilli.now), state.user.now.id))
                  )

                  // stop observing once read
                  observer.unobserve(elem)
                  observer.disconnect()

                  observed() = true // makes the dot fade out

                  state.eventProcessor.changesRemoteOnly.onNext(markAsReadChanges)
                }
              },
              new IntersectionObserverOptions {
                root = null //TODO need actual scroll-parent?
                // TODO: rootMargin = "100px 0px 0px 0px"
                threshold = 0
              }
            )

            observer.observe(elem)

            Cancelable { () =>
              observer.unobserve(elem)
              observer.disconnect()
            }
          }
        )
        case false =>
          unreadChildren() match {
            case 0 => VDomModifier.empty
            case count => unreadLabel(count)
          }
      },
    }
  }
}
