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

  def apply(show: Observable[Boolean], defaultAddToChannels: Boolean, defaultNodeRole: NodeRole)(implicit ctx: Ctx.Owner): VDomModifier = IO {
    val parentNodes = Var[List[ParentId]](Nil)
    val childNodes = Var[List[ChildId]](Nil)
    val nodeRole = Var[NodeRole](defaultNodeRole)
    val addToChannels = Var[Boolean](defaultAddToChannels)
    val nodeAccess = Var[NodeAccess](NodeAccess.Inherited)

    def newMessage(sub: InputRow.Submission): Future[Ack] = {
      val parents: List[ParentId] = if (parentNodes.now.isEmpty) List(ParentId(GlobalState.user.now.id: NodeId)) else parentNodes.now

      val newNode = Node.Content(NodeData.Markdown(sub.text), nodeRole.now, NodeMeta(nodeAccess.now))
      val changes =
        GraphChanges.addNodeWithParent(newNode, parents) merge
          GraphChanges.addToParent(childNodes.now, ParentId(newNode.id)) merge
          sub.changes(newNode.id)

      val ack = if (addToChannels.now) {
        val channelChanges = GraphChanges.connect(Edge.Pinned)(newNode.id, GlobalState.user.now.id)
        val ack = GlobalState.submitChanges(changes merge channelChanges)
        GlobalState.urlConfig.update(_.focus(Page(newNode.id), needsGet = false))
        ack
      } else {
        val ack = GlobalState.submitChanges(changes)
        def newViewConfig = nodeRole.now match {
          case NodeRole.Message => GlobalState.urlConfig.now.focus(Page(parents.head), View.Conversation)
          case NodeRole.Task    => GlobalState.urlConfig.now.focus(Page(parents.head), View.Tasks)
          case NodeRole.Note    => GlobalState.urlConfig.now.focus(Page(parents.head), View.Content)
        }
        UI.toast(s"Created new ${nodeRole.now}: ${StringOps.trimToMaxLength(newNode.str, 10)}", click = () => GlobalState.urlConfig() = newViewConfig, level = UI.ToastLevel.Success)
        ack
      }

      GlobalState.uiModalClose.onNext(())
      ack
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
        InputRow (focusState = None, submitAction = newMessage, autoFocus = true, showMarkdownHelp = true).apply(marginBottom := "5px", width := "100%"),
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
              GlobalState.graph().nodesById(id).map { node =>
                nodeCard(

                  node,
                  contentInject = VDomModifier(
                    Styles.flex,
                    flexDirection.row,
                    justifyContent.spaceBetween,
                    span(freeSolid.faTimes, cursor.pointer, onClick.mapTo(childNodes.now.filterNot(_ == node.id)) --> childNodes, opacity := 0.4, paddingLeft := "10px")
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
            parentNodes -> List(ParentId(GlobalState.page.now.parentId.getOrElse(GlobalState.user.now.id))),
            childNodes -> ChildId(GlobalState.selectedNodes.now)
          )

          GlobalState.uiModalConfig.onNext(Ownable(implicit ctx => ModalConfig(header = header, description = description, modalModifier = VDomModifier(
            cls := "create-new-prompt",
          ))))
        } else GlobalState.uiModalClose.onNext(())
      }
    )
  }

}
