package wust.webApp.views

import fontAwesome.{IconLookup, freeSolid}
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.reactive._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.util.macros.SubObjects
import wust.webApp.Icons
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ModalConfig, Ownable, UI}

object CreateNewPrompt {

  sealed trait SelectableNodeRole
  object SelectableNodeRole {
    case object Message extends SelectableNodeRole
    case object Task extends SelectableNodeRole
    case object Note extends SelectableNodeRole
    case object Project extends SelectableNodeRole
    val list: Array[SelectableNodeRole] = SubObjects.all[SelectableNodeRole]
  }
  import SelectableNodeRole._

  def apply[F[_] : Source](show: F[Boolean], defaultAddToChannels: Boolean, defaultNodeRole: SelectableNodeRole)(implicit ctx: Ctx.Owner): VDomModifier = VDomModifier.delay {
    val parentNodes = Var[Vector[ParentId]](Vector.empty)
    val childNodes = Var[Vector[ChildId]](Vector.empty)
    val nodeRole = Var[SelectableNodeRole](defaultNodeRole)
    val addToChannels = Var[Boolean](defaultAddToChannels)
    val nodeAccess = Var[NodeAccess](NodeAccess.Inherited)
    val triggerSubmit = SinkSourceHandler.publish[Unit]

    def newMessage(sub: InputRow.Submission) = {
      val parents: Vector[ParentId] = if (parentNodes.now.isEmpty) Vector(ParentId(GlobalState.user.now.id: NodeId)) else parentNodes.now

      GlobalState.clearSelectedNodes()

      val newNodeViews: List[View.Visible] = nodeRole.now match {
        case Message => List(View.Chat)
        case Task    => List(View.List, View.Chat)
        case Note    => List(View.Content, View.Chat)
        case Project => List(View.Dashboard, View.Chat)
      }
      val newNodeRole: NodeRole = nodeRole.now match {
        case Message => NodeRole.Message
        case Task    => NodeRole.Task
        case Note    => NodeRole.Note
        case Project => NodeRole.Project
      }
      val newNode = Node.Content(NodeId.fresh, NodeData.Markdown(sub.text), newNodeRole, NodeMeta(nodeAccess.now), views = Some(newNodeViews))
      val changes =
        GraphChanges.addNodeWithParent(newNode, parents) merge
          GraphChanges.addToParent(childNodes.now, ParentId(newNode.id)) merge
          sub.changes(newNode.id)

      if (addToChannels.now) {
        val channelChanges = GraphChanges.connect(Edge.Pinned)(newNode.id, GlobalState.user.now.id)
        GlobalState.submitChanges(changes merge channelChanges)
        GlobalState.urlConfig.update(_.focus(Page(newNode.id), needsGet = false))
      } else {
        GlobalState.submitChanges(changes)
        def newViewConfig = nodeRole.now match {
          case Message => GlobalState.urlConfig.now.focus(Page(parents.head), View.Conversation)
          case Task    => GlobalState.urlConfig.now.focus(Page(parents.head), View.Tasks)
          case Note    => GlobalState.urlConfig.now.focus(Page(parents.head), View.Content)
        }
        UI.toast(s"Created new ${nodeRole.now}: ${StringOps.trimToMaxLength(newNode.str, 10)}", click = () => GlobalState.urlConfig() = newViewConfig, level = UI.ToastLevel.Success)
      }

      GlobalState.uiModalClose.onNext(())
    }

    val targetNodeSelection = div(
      div(
        Styles.flex,
        flexDirection.row,
        alignItems.center,

        div("Inside:"),
        Rx {
          val g = GlobalState.graph()
          parentNodes().map(nodeId =>
            g.nodesById(nodeId).map { node =>
              nodeCard(
                node,
                contentInject = VDomModifier(
                  Styles.flex,
                  flexDirection.row,
                  justifyContent.spaceBetween,
                  span(freeSolid.faTimes, cursor.pointer, onClick.foreach { parentNodes.update(list => list.filter(_ != node.id)) }, opacity := 0.4, paddingLeft := "10px")
                )
              ).apply(padding := "2px", marginLeft := "5px")
            })
        },
        div(
          paddingLeft := "5px",
          searchInGraph(
            GlobalState.rawGraph,
            placeholder = "Select Project",
            valid = parentNodes.map(_.nonEmpty),
            {
              case n: Node.Content => !parentNodes.now.contains(n.id)
              // only allow own user, we do not have public profiles yet
              case n: Node.User    => GlobalState.user.now.id == n.id && !parentNodes.now.contains(n.id)
            }
          ).foreach { nodeId =>
              parentNodes() = (parentNodes.now :+ ParentId(nodeId)).distinct
            },
        )
      )
    )

    def header(implicit ctx: Ctx.Owner) = div(
      "Create New",
    // Styles.flex,
    // flexWrap.wrap,
    // alignItems.center,

    // backgroundColor <-- parentNodes.map[String](_.foldLeft[Color](RGB("#FFFFFF"))((c, id) => NodeColor.mixColors(c, NodeColor.eulerBgColor(id))).toHex),

    // div("Create new ", color := "rgba(0,0,0,0.62)"),
    // UI.toggle("Pin to sidebar", initialChecked = addToChannels.now) --> addToChannels
    )

    val roleSelection = div(
      cls := "ui basic buttons",
      Rx {
        def roleButton(title: String, icon: IconLookup, role: SelectableNodeRole): VDomModifier = div(
          cls := "ui button",
          icon, " ", title,
          (nodeRole() == role).ifTrue[VDomModifier](cls := "active"),
          onClick.use(role) --> nodeRole
        )
        VDomModifier(
          roleButton("Task", Icons.task, Task),
          roleButton("Message", Icons.message, Message),
          roleButton("Note", Icons.note, Note),
          roleButton("Project", Icons.project, Project)
        )
      },
    )

    val createButton = div(
      marginTop := "20px",
      button(
        "Create",
        cls := "ui violet button",
        onClick.stopPropagation.use(()) --> triggerSubmit
      )
    )

    def description(implicit ctx: Ctx.Owner) = {

      VDomModifier(
        InputRow (
          focusState = None,
          submitAction = newMessage,
          autoFocus = true,
          showMarkdownHelp = true,
          triggerSubmit = triggerSubmit,
        ).apply(marginBottom := "5px", width := "100%"),
        div(
          Styles.flex,
          alignItems.center,
          justifyContent.spaceBetween,
          flexWrap.wrap,
          roleSelection(Styles.flexStatic, marginBottom := "5px"),
          targetNodeSelection(Styles.flexStatic),
        ),

        div(
          padding := "5px",
          Styles.flex,
          flexWrap.wrap,
          justifyContent.spaceBetween,

        // div(
        //   div("Permission:", color := "rgba(0,0,0,0.62)"),
        //   UI.dropdown(
        //     tabIndex := -1, // cannot focus this dropdown via tab
        //     new DropdownEntry {
        //       value = NodeAccess.Inherited.str
        //       name = "Inherited"
        //       selected = nodeAccess.now == NodeAccess.Inherited
        //     },
        //     new DropdownEntry {
        //       value = NodeAccess.ReadWrite.str
        //       name = "Public"
        //       selected = nodeAccess.now == NodeAccess.ReadWrite
        //     },
        //     new DropdownEntry {
        //       value = NodeAccess.Restricted.str
        //       name = "Private"
        //       selected = nodeAccess.now == NodeAccess.Restricted
        //     },
        //   ).collect(NodeAccess.fromString) --> nodeAccess
        // )
        ),

        div(
          Styles.flex,
          flexDirection.column,
          alignItems.flexStart,

          Rx {
            val nodes = childNodes().flatMap { id =>
              GlobalState.graph().nodesById(id).map { node =>
                nodeCard(
                  node,
                  contentInject = VDomModifier(
                    Styles.flex,
                    flexDirection.row,
                    justifyContent.spaceBetween,
                    span(freeSolid.faTimes, cursor.pointer, onClick.useLazy(childNodes.now.filterNot(_ == node.id)) --> childNodes, opacity := 0.4, paddingLeft := "10px")
                  ),
                  maxLength = Some(20),
                  projectWithIcon = true,
                ).apply(
                    marginBottom := "3px",
                    padding := "3px" // like in `.chat-row .nodecard`
                  )
              }
            }

            VDomModifier.ifTrue(nodes.nonEmpty)(
              div(
                "Originated from conversation:",
              // span(marginLeft := "auto", freeSolid.faTimes, cursor.pointer, onClick(Nil) --> childNodes)
              ),
              nodes
            )
          },

          createButton(alignSelf.flexEnd)
        )
      )
    }

    VDomModifier(
      emitter(show).foreach { show =>
        if (show) {
          Var.set(
            parentNodes -> Vector(ParentId(GlobalState.page.now.parentId.getOrElse(GlobalState.user.now.id))),
            childNodes -> ChildId(GlobalState.selectedNodes.now.map(_.nodeId))
          )

          GlobalState.uiModalConfig.onNext(Ownable(implicit ctx => ModalConfig(header = header, description = description, modalModifier = VDomModifier(
            cls := "create-new-prompt",
          ))))
        } else {
          GlobalState.uiModalClose.onNext(())
        }
      }
    )
  }

}
