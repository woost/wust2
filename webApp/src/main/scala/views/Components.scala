package wust.webApp.views

import wust.sdk.Colors
import wust.sdk.{BaseColors, NodeColor}
import cats.effect.IO
import emojijs.EmojiConvertor
import fomanticui.{SearchOptions, SearchSourceEntry, ToastOptions}
import fontAwesome._
import googleAnalytics.Analytics
import monix.execution.Cancelable
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
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
import wust.webApp.state.{EmojiReplacer, FocusPreference, GlobalState, PageChange, UploadingFile}
import wust.webApp.views.Elements._
import wust.webApp.views.UI.ModalConfig

import scala.collection.breakOut
import scala.scalajs.js

// This file contains woost-related UI helpers.

object Placeholders {
  val newNode: Attr = placeholder := "Create new item. Press Enter to submit."
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

    def downloadLink = a(downloadUrl(href), s"Download ${file.fileName}", onClick.stopPropagation --> Observer.empty)

    div(
      if (file.key.isEmpty) { // this only happens for currently-uploading files
        VDomModifier(
          file.fileName,
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
    svg.thunkStatic(uniqueKey)(VDomModifier(
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
            path(cls := "woost-loading-animation-logo", d := woostPathCurve, fill := "none", stroke := "#7574DB", strokeLineCap := "round", strokeWidth := "3.5865", pathLength := "100")
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

  def woostLoadingAnimationWithFadeIn = woostLoadingAnimation(cls := "animated-fadein")

  def spaceFillingLoadingAnimation(state: GlobalState)(implicit data: Ctx.Data): VNode = {
    div(
      Styles.flex,
      alignItems.center,
      justifyContent.center,
      flexDirection.column,
      Styles.growFull,

      woostLoadingAnimationWithFadeIn,

      div(
        cls := "animated-late-fadein",
        Styles.flex,
        alignItems.center,

        fontSize.xSmall,
        marginTop := "20px",

        span("Loading forever?", marginRight := "10px"),
        button(margin := "0px", cls := "ui button compact mini", freeSolid.faRedo, " Reload", cursor.pointer, onClick.stopPropagation.foreach { dom.window.location.reload() })
      )
    )
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

      drag(DragItem.Property(key), target = DragItem.DisableDrag),

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
      drag(DragItem.User(userNode.id), target = DragItem.DisableDrag),
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
      withAutomation: Boolean = false,
    )(implicit ctx: Ctx.Owner): VNode = {

      div( // checkbox and nodetag are both inline elements because of fomanticui
        cls := "tagWithCheckbox",
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
        VDomModifier.ifTrue(withAutomation)(GraphChangesAutomationUI.settingsButton(state, tagNode.id, activeMod = visibility.visible).apply(cls := "singleButtonWithBg", marginLeft.auto)),
      )
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
      dragOptions: NodeId => VDomModifier = nodeId => drag(DragItem.Tag(nodeId), target = DragItem.DisableDrag),
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
      @inline def disableDrag = payload.isInstanceOf[DragItem.DisableDrag.type]
      VDomModifier(
        //TODO: draggable bug: draggable sets display:none, then does not restore the old value https://github.com/Shopify/draggable/issues/318
        cls := "draggable", // makes this element discoverable for the Draggable library
        cls := "drag-feedback", // visual feedback for drag-start
        onMouseDown.stopPropagation --> Observer.empty, // don't trigger global onMouseDown (e.g. closing right sidebar) when dragging
        VDomModifier.ifTrue(disableDrag)(cursor.auto), // overwrites cursor set by .draggable class
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
        container match {
          case _:SortableContainer => cls := "sortable-container"
          case _ => cls := "draggable-container"
        },
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

      val iconAndPopup = selected.prepend(None).map {
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
          backgroundColor := "#545454",
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

      EditableContent.editorRx[AWS.UploadableFile](selected, config = EditableContent.Config(
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

  val unreadLabelElement = div(
    float.right,
    cls := "ui label",
    color := "white",
    backgroundColor := Colors.unread,
    fontSize.xxSmall,
    marginLeft := "5px",
    marginRight := "5px",
    padding := "4px",
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

    Rx {
      isUnread() match {
        case true => VDomModifier(
          unreadChildren() match {
            case 0 => unreadLabel
            case count => unreadLabel(count)
          },

          managedElement.asHtml { elem =>
            val observer = new IntersectionObserver(
              { (entry, observer) =>
                val isIntersecting = entry.head.isIntersecting
                if (isIntersecting && isUnread.now) {
                  val changes = GraphChanges(
                    addEdges = Array(Edge.Read(nodeId, EdgeData.Read(EpochMilli.now), state.user.now.id))
                  )

                  // stop observing once read
                  observer.unobserve(elem)
                  observer.disconnect()

                  state.eventProcessor.changesRemoteOnly.onNext(changes)
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
