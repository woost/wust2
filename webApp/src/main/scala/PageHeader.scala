package wust.webApp

import fontAwesome._
import org.scalajs.dom
import org.scalajs.dom.console
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import scala.scalajs.js
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.util._
import wust.webApp.outwatchHelpers._
import wust.webApp.views.Elements._
import wust.webApp.views.Rendered._


object PageHeader {
  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    import state._
    div(
      padding := "5px 10px",
      Rx {
        pageParentNodes().map { channel => channelRow(state, channel) },
      }
    )
  }

  private def channelRow(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    div(
      Styles.flex,
      alignItems.center,

      channelAvatar(channel.id, size = 30)(Styles.flexStatic, marginRight := "10px"),
      editableNodeOnClick(state, channel, state.eventProcessor.changes)(ctx)(fontSize := "20px", marginRight := "auto"),
      Rx {
        (channel.id != state.user().channelNodeId).ifTrueSeq(
          Seq[VDomModifier](
            bookMarkControl(state, channel)(ctx)(margin := "0px 10px"),
            settingsMenu(state, channel)(ctx)(),
          )
        )
      }
    )
  }

  private def channelAvatar(nodeId:NodeId, size:Int) = {
    Avatar.node(nodeId)(
      width := s"${size}px",
      height := s"${size}px"
    )
  }

  private def bookMarkControl(state: GlobalState, node: Node)(implicit ctx: Ctx.Owner): VNode = div(
    Rx {
      (state
        .graph()
        .children(state.user().channelNodeId)
        .contains(node.id) match {
        case true  => freeSolid.faBookmark
        case false => freeRegular.faBookmark
      }): VNode //TODO: implicit for Rx[IconDefinition] ?
    },
    fontSize := "20px",
    cursor.pointer,
    onClick --> sideEffect { _ =>
      val changes = state.graph.now
        .children(state.user.now.channelNodeId)
        .contains(node.id) match {
        case true =>
          GraphChanges.disconnectParent(node.id, state.user.now.channelNodeId)
        case false =>
          GraphChanges.connectParent(node.id, state.user.now.channelNodeId)
      }
      state.eventProcessor.changes.onNext(changes)
    }
  )

  private def settingsMenu(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner):VNode = {
    // https://semantic-ui.com/modules/dropdown.html#pointing
    div(
      cls := "ui icon top left pointing dropdown",
      (freeSolid.faCog:VNode)(fontSize := "20px"),
      div(
        cls := "menu",
        div( cls := "header", "Settings"),
        div(
          cls := "item",
          i(cls := "dropdown icon"),
          span(cls := "text", "Permissions"),
          div(
            cls := "menu",
            PermissionSelection.all.map { selection =>
              div(
                cls := "item",
                (channel.meta.accessLevel == selection.access).ifTrueOption(i(cls := "check icon")),
                // value := selection.value,
                Rx {
                  selection.name(channel.id, state.graph()) //TODO: report Scala.Rx bug, where two reactive variables in one function call give a compile error: selection.name(state.user().id, node.id, state.graph())
                },
                onClick{
                  val nextNode = channel match {
                    case n: Node.Content => n.copy(meta = n.meta.copy(accessLevel = selection.access))
                    case _               => ??? //FIXME
                  }
                  GraphChanges.addNode(nextNode)
                } --> state.eventProcessor.changes
              ),
            },
          )
        )
      ),
      // https://semantic-ui.com/modules/dropdown.html#/usage
      onInsert.asHtml --> sideEffect { elem =>
        import semanticUi.JQuery._
        $(elem).dropdown()
      }
    )
  }
}

case class PermissionSelection(
    access: NodeAccess,
    value: String,
    name: (NodeId, Graph) => String,
    description: String,
    icon: IconLookup
)
object PermissionSelection {
  val all =
    PermissionSelection(
      access = NodeAccess.Inherited,
      name = { (nodeId, graph) =>
        val canAccess = graph
          .parents(nodeId)
          .exists(nid => graph.nodesById(nid).meta.accessLevel == NodeAccess.ReadWrite)
        console.log(graph.parents(nodeId).map(nid => graph.nodesById(nid)).mkString(", "))
        val inheritedLevel = if (canAccess) "Public" else "Private"
        s"Inherited ($inheritedLevel)"
      },
      value = "Inherited",
      description = "The permissions for this Node are inherited from its parents",
      icon = freeSolid.faArrowUp
    ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.ReadWrite),
        name = (_, _) => "Public",
        value = "Public",
        description = "Anyone can access this Node via the URL",
        icon = freeSolid.faUserPlus
      ) ::
      PermissionSelection(
        access = NodeAccess.Level(AccessLevel.Restricted),
        name = (_, _) => "Private",
        value = "Private",
        description = "Only you and explicit members can access this Node",
        icon = freeSolid.faLock
      ) ::
      Nil
}
