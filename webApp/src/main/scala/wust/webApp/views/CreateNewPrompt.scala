package wust.webApp.views

import cats.effect.IO
import colorado.{ Color, RGB }
import wust.facades.fomanticui.DropdownEntry
import fontAwesome.{ IconLookup, freeRegular, freeSolid }
import monix.execution.Ack
import monix.reactive.Observable
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.webUtil.outwatchHelpers._
import wust.webUtil.{ ModalConfig, Ownable, UI }
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.sdk.NodeColor
import wust.util._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._

import scala.concurrent.Future
import wust.webApp.Icons
import wust.sdk.Colors

object CreateNewPrompt {

  def apply(state: GlobalState, show: Observable[Boolean], defaultAddToChannels: Boolean, defaultNodeRole: NodeRole)(implicit ctx: Ctx.Owner): VDomModifier = IO {
    val parentNodes = Var[List[ParentId]](Nil)
    val childNodes = Var[List[ChildId]](Nil)
    val nodeRole = Var[NodeRole](defaultNodeRole)
    val addToChannels = Var[Boolean](defaultAddToChannels)
    val nodeAccess = Var[NodeAccess](NodeAccess.Inherited)

    def newMessage(sub: InputRow.Submission): Future[Ack] = {
      val parents: List[ParentId] = if (parentNodes.now.isEmpty) List(ParentId(state.user.now.id: NodeId)) else parentNodes.now

      val newNode = Node.Content(NodeData.Markdown(sub.text), nodeRole.now, NodeMeta(nodeAccess.now))
      val changes =
        GraphChanges.addNodeWithParent(newNode, parents) merge
          GraphChanges.addToParent(childNodes.now, ParentId(newNode.id)) merge
          sub.changes(newNode.id)

      val ack = if (addToChannels.now) {
        val channelChanges = GraphChanges.connect(Edge.Pinned)(newNode.id, state.user.now.id)
        val ack = state.eventProcessor.changes.onNext(changes merge channelChanges)
        state.urlConfig.update(_.focus(Page(newNode.id), needsGet = false))
        ack
      } else {
        val ack = state.eventProcessor.changes.onNext(changes)
        def newViewConfig = nodeRole.now match {
          case NodeRole.Message => state.urlConfig.now.focus(Page(parents.head), View.Conversation)
          case NodeRole.Task    => state.urlConfig.now.focus(Page(parents.head), View.Tasks)
          case NodeRole.Note    => state.urlConfig.now.focus(Page(parents.head), View.Content)
        }
        UI.toast(s"Created new ${nodeRole.now}: ${StringOps.trimToMaxLength(newNode.str, 10)}", click = () => state.urlConfig() = newViewConfig, level = UI.ToastLevel.Success)
        ack
      }

      state.uiModalClose.onNext(())
      ack
    }

    val targetNodeSelection = div(
      div(
        Styles.flex,
        flexDirection.row,
        alignItems.center,

        div("Inside:"),
        Rx {
          val g = state.graph()
          parentNodes().map(nodeId =>
            g.nodesById(nodeId).map { node =>
              nodeCard(state, node).apply(padding := "2px", marginLeft := "5px")
              // removableNodeTagCustom(state, tag, () => parentNodes.update(list => list.filter(_ != tag.id)))(padding := "2px")
            })
        },
        div(
          paddingLeft := "5px",
          searchInGraph(
            state.rawGraph,
            placeholder = "Select Project",
            valid = parentNodes.map(_.nonEmpty),
            {
              case n: Node.Content => !parentNodes.now.contains(n.id)
              // only allow own user, we do not have public profiles yet
              case n: Node.User    => state.user.now.id == n.id && !parentNodes.now.contains(n.id)
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
        def roleButton(title: String, icon: IconLookup, role: NodeRole): VDomModifier = div(
          cls := "ui button",
          icon, " ", title,
          (nodeRole() == role).ifTrue[VDomModifier](cls := "active"),
          onClick(role) --> nodeRole
        )
        VDomModifier(
          roleButton("Task", Icons.task, NodeRole.Task),
          roleButton("Message", Icons.message, NodeRole.Message),
          roleButton("Note", Icons.note, NodeRole.Note)
        )
      },
    )

    def description(implicit ctx: Ctx.Owner) = {

      VDomModifier(
        InputRow (state, focusState = None, submitAction = newMessage, autoFocus = true, showMarkdownHelp = true).apply(marginBottom := "5px", width := "100%"),
        div(
          Styles.flex,
          alignItems.center,
          justifyContent.spaceBetween,
          roleSelection,
          targetNodeSelection,
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
              state.graph().nodesById(id).map { node =>
                nodeCard(
                  state,
                  node,
                  contentInject = VDomModifier(
                    Styles.flex,
                    flexDirection.row,
                    justifyContent.spaceBetween,
                    span(freeSolid.faTimes, cursor.pointer, onClick.mapTo(childNodes.now.filterNot(_ == node.id)) --> childNodes, opacity := 0.4, padding := "0 5px 0 10px")
                  ),
                  maxLength = Some(20),
                  projectWithIcon = true,
                ).apply(
                  marginBottom := "3px",
                  padding := "3px" // like in `.chat-row .nodecard`
                )
              }
            }

            if (nodes.isEmpty) VDomModifier.empty
            else VDomModifier(
              div(
                "Originated from conversation:",
              // span(marginLeft := "auto", freeSolid.faTimes, cursor.pointer, onClick(Nil) --> childNodes)
              ),
              nodes
            )
          }
        )
      )
    }

    VDomModifier(
      emitter(show).foreach { show =>
        if (show) {
          Var.set(
            parentNodes -> List(ParentId(state.page.now.parentId.getOrElse(state.user.now.id))),
            childNodes -> ChildId(state.selectedNodes.now)
          )

          state.uiModalConfig.onNext(Ownable(implicit ctx => ModalConfig(header = header, description = description, modalModifier = VDomModifier(
            cls := "create-new-prompt",
          ))))
        }
        else state.uiModalClose.onNext(())
      }
    )
  }

}
