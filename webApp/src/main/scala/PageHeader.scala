package wust.webApp

import fontAwesome._
import org.scalajs.dom
import org.scalajs.dom.console
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
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
      pageParentNodes.map(_.map { parent =>
        div(
          Styles.flex,
          alignItems.center,
          Avatar.node(parent.id)(
            width := "40px",
            height := "40px",
            marginRight := "10px"
          ),
          editableNodeOnClick(state, parent, renderNodeData(parent.data)(fontSize := "20px")),
          state.user.map { user =>
            if (user.channelNodeId == parent.id) Seq.empty
            else
              Seq[VDomModifier](
                bookMarkControl(state, parent)(ctx)(margin := "0px 10px"),
                joinControl(state, parent)(ctx)(marginLeft := "auto"),
              )
          }
        )
      }),
      overflowX.auto
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

  private def joinControl(state: GlobalState, channel: Node)(implicit ctx: Ctx.Owner): VNode = {
    select(
      cls := "ui dropdown selection",
      onChange.value.map { value =>
        val newAccess = PermissionSelection.all.find(_.value == value).get.access
        val nextNode = channel match {
          case n: Node.Content => n.copy(meta = n.meta.copy(accessLevel = newAccess))
          case _               => ??? //FIXME
        }
        GraphChanges.addNode(nextNode)
      } --> state.eventProcessor.changes,
      PermissionSelection.all.map { selection =>
        option(
          (channel.meta.accessLevel == selection.access).ifTrueOption(selected := true),
          value := selection.value,
          renderFontAwesomeIcon(selection.icon)(cls := "icon"),
          Rx {
            selection.name(channel.id, state.graph()) //TODO: report Scala.Rx bug, where two reactive variables in one function call give a compile error: selection.name(state.user().id, node.id, state.graph())
          }
        )
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
