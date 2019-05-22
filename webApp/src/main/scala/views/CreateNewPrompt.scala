package wust.webApp.views

import fontAwesome.{IconLookup, freeRegular, freeSolid}
import jquery.JQuerySelection
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.ids._
import wust.webApp.state.{GlobalState, PageChange}
import Components._
import cats.effect.IO
import colorado.{Color, RGB}
import fomanticui.{DropdownEntry, ModalOptions}
import monix.execution.Ack
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import wust.css.Styles
import wust.graph._
import wust.sdk.{BaseColors, NodeColor}
import wust.webApp.outwatchHelpers._
import wust.util._
import wust.webApp.{Ownable, views}

import scala.concurrent.Future

object CreateNewPrompt {

  def apply(state: GlobalState, show: Observable[Boolean], defaultAddToChannels: Boolean, defaultNodeRole: NodeRole)(implicit ctx: Ctx.Owner): VDomModifier = IO {
    val parentNodes = Var[List[ParentId]](Nil)
    val childNodes = Var[List[ChildId]](Nil)
    val nodeRole = Var[NodeRole](defaultNodeRole)
    val addToChannels = Var[Boolean](defaultAddToChannels)
    val nodeAccess = Var[NodeAccess](NodeAccess.Inherited)

    def newMessage(msg: String): Future[Ack] = {
      val parents: List[ParentId] = if (parentNodes.now.isEmpty) List(ParentId(state.user.now.id: NodeId)) else parentNodes.now

      val newNode = Node.Content(NodeData.Markdown(msg), nodeRole.now, NodeMeta(nodeAccess.now))
      val changes =
        GraphChanges.addNodeWithParent(newNode, parents) merge
        GraphChanges.addToParent(childNodes.now, ParentId(newNode.id))

      val ack = if (addToChannels.now) {
        val channelChanges = GraphChanges.connect(Edge.Pinned)(newNode.id, state.user.now.id)
        val ack = state.eventProcessor.changes.onNext(changes merge channelChanges)
        state.urlConfig.update(_.focus(Page(newNode.id), needsGet = false))
        ack
      } else {
        val ack = state.eventProcessor.changes.onNext(changes)
        def newViewConfig = nodeRole.now match {
          case NodeRole.Message => state.urlConfig.now.focus(Page(parents.head), View.Conversation)
          case NodeRole.Task => state.urlConfig.now.focus(Page(parents.head), View.Tasks)
        }
        UI.toast(s"Created new ${nodeRole.now}: ${StringOps.trimToMaxLength(newNode.str, 10)}", click = () => state.urlConfig() = newViewConfig, level = UI.ToastLevel.Success)
        ack
      }

      state.uiModalClose.onNext(())
      ack
    }

    def header(implicit ctx: Ctx.Owner) = div(
      Styles.flex,
      flexDirection.row,
      flexWrap.wrap,
      alignItems.center,

      div("Create new ", color := "rgba(0,0,0,0.62)"),
      div(
        marginLeft := "10px",
        cls := "ui basic buttons",
        Rx {
          def roleButton(title: String, icon: IconLookup, role: NodeRole): VDomModifier = div(
            cls := "ui button",
            icon, " ", title,
            (nodeRole() == role).ifTrue[VDomModifier](cls := "active"),
            onClick(role) --> nodeRole
          )
          VDomModifier(
            roleButton("Task", freeRegular.faCheckSquare, NodeRole.Task),
            roleButton("Message", freeRegular.faComment, NodeRole.Message)
          )
        },
        marginRight := "30px"
      ),
      UI.toggle("Pin to sidebar", initialChecked = addToChannels.now) --> addToChannels
    )

    def description(implicit ctx: Ctx.Owner) = VDomModifier(
      div(
        padding := "5px",
        Styles.flex,
        flexWrap.wrap,
        justifyContent.spaceBetween,

        div(
          div("Tags:", color := "rgba(0,0,0,0.62)"),
          div(
            Styles.flex,
            flexDirection.row,
            alignItems.center,

            Rx {
              val g = state.graph()
              parentNodes().map(tagId =>
                g.nodesById(tagId).map { tag =>
                  removableNodeTagCustom(state, tag, () => parentNodes.update(list => list.filter(_ != tag.id)))(padding := "2px")
                }
              )
            },
            div(
              paddingLeft := "5px",
              searchInGraph(
                state.rawGraph,
                placeholder = "Add an existing tag",
                valid = parentNodes.map(_.nonEmpty),
                {
                  case n: Node.Content => !parentNodes.now.contains(n.id)
                  // only allow own user, we do not have public profiles yet
                  case n: Node.User => state.user.now.id == n.id && !parentNodes.now.contains(n.id)
                }
              ).foreach { nodeId =>
                parentNodes() = (parentNodes.now :+ ParentId(nodeId)).distinct
              },
            )
          )
        ),
        div(
          div("Permission:", color := "rgba(0,0,0,0.62)"),
          UI.dropdown(
            tabIndex := -1, // cannot focus this dropdown via tab
            new DropdownEntry {
              value = NodeAccess.Inherited.str
              name = "Inherited"
              selected = nodeAccess.now == NodeAccess.Inherited
            },
            new DropdownEntry {
              value = NodeAccess.ReadWrite.str
              name = "Public"
              selected = nodeAccess.now == NodeAccess.ReadWrite
            },
            new DropdownEntry {
              value = NodeAccess.Restricted.str
              name = "Private"
              selected = nodeAccess.now == NodeAccess.Restricted
            },
          ).collect(NodeAccess.fromString) --> nodeAccess
        )
      ),

      InputRow(state, submitAction = newMessage, autoFocus = true, showMarkdownHelp = true).apply(width := "100%", padding := "10px"),

      div(
        width := "300px",
        marginLeft := "auto",
        Rx {
          val nodes = childNodes().flatMap { id =>
            state.graph().nodesById(id).map { node =>
              nodeCard(node, contentInject = VDomModifier(Styles.flex, flexDirection.row, justifyContent.spaceBetween, span(freeSolid.faTimes, cursor.pointer, onClick.mapTo(childNodes.now.filterNot(_ == node.id)) --> childNodes)), maxLength = Some(20))
            }
          }

          if (nodes.isEmpty) VDomModifier.empty
          else VDomModifier(
            div(
              Styles.flex,
              flexDirection.column,
              justifyContent.spaceBetween,

              freeRegular.faComments,
              span(marginLeft := "auto", freeSolid.faTimes, cursor.pointer, onClick(Nil) --> childNodes)
            ),
            nodes
          )
        }
      )
    )

    VDomModifier(
      emitter(show).foreach { show =>
        if (show) {
          Var.set(
            parentNodes -> List(ParentId(state.page.now.parentId.getOrElse(state.user.now.id))),
            childNodes -> ChildId(state.selectedNodes.now)
          )

          state.uiModalConfig.onNext(Ownable(implicit ctx => UI.ModalConfig(header = header, description = description, modalModifier = VDomModifier(
            cls := "basic",
            backgroundColor <-- parentNodes.map[String](_.foldLeft[Color](RGB("#FFFFFF"))((c, id) => NodeColor.mixColors(c, NodeColor.eulerBgColor(id))).toHex),
          ))))
        }
        else state.uiModalClose.onNext(())
      }
    )
  }

}
