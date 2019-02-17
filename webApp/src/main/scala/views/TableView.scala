package wust.webApp.views

import flatland.ArraySliceInt
import fontAwesome.freeSolid
import monix.reactive.subjects.PublishSubject
import rx._
import outwatch.dom._
import outwatch.dom.dsl._
import wust.css.Styles
import wust.graph.{Edge, Graph, GraphChanges, Node}
import wust.ids.{NodeData, NodeId, NodeRole}
import wust.webApp.Icons
import wust.webApp.outwatchHelpers._
import wust.webApp.state.{FocusState, GlobalState}

import scala.collection.breakOut

object TableView {
  def apply(state: GlobalState, focusState: FocusState, roles: List[NodeRole])(implicit ctx: Ctx.Owner): VNode = {
    val sort = Var[Option[UI.ColumnSort]](None)

    div(
      Styles.growFull,
      overflow.auto,

      Rx {
        val graph = state.graph()
        table(state, graph, focusState.focusedId, roles, sort)
      }
    )
  }

  def table(state: GlobalState, graph: Graph, focusedId: NodeId, roles: List[NodeRole], sort: Var[Option[UI.ColumnSort]])(implicit ctx: Ctx.Owner): VDomModifier = {
    val focusedIdx = graph.idToIdxOrThrow(focusedId)

    def columnEntryOfNodes(row: NodeId, nodes: Array[Node], valueModifier: VDomModifier = VDomModifier.empty): UI.ColumnEntry = UI.ColumnEntry(
      sortValue = nodes.map {
        case node: Node.Content => node.str
        case user: Node.User    => Components.displayUserName(user.data) // sort users by display name
      }.mkString(", "),
      value = VDomModifier(
        nodes.map {
          case tag: Node.Content if tag.role == NodeRole.Tag => Components.removableNodeTag(state, tag, row)
          case node: Node.Content                            => Components.renderNodeDataWithFile(state, node.id, node.data, maxLength = Some(50)) //TODO editable?
          case user: Node.User                               => Components.removableAssignedUser(state, user, row)
        },
        valueModifier
      )
    )

    val (childrenIdxs, nodeToProperties): (Seq[Int], Array[Map[String, Array[Edge.LabeledProperty]]]) = {
      val array = new Array[Map[String, Array[Edge.LabeledProperty]]](graph.nodes.length)
      val childrenIdxs = {
        val arr = graph.notDeletedChildrenIdx(focusedIdx)
        if (roles.isEmpty) arr else arr.filter { childrenIdx =>
          val node = graph.nodes(childrenIdx)
          roles.contains(node.role)
        }
      }
      childrenIdxs.foreach { childIdx =>
        array(childIdx) = graph.propertiesEdgeIdx.map(childIdx)(idx => graph.edges(idx).asInstanceOf[Edge.LabeledProperty]).groupBy(_.data.key)
      }

      (childrenIdxs, array)
    }

    val sortedProperties = nodeToProperties.flatMap { map => if (map == null) Nil else map.keys }.distinct.sorted // TODO order

    val nodeColumns: List[UI.Column] =
      UI.Column(
        "Node",
        childrenIdxs.map { childrenIdx =>
          val node = graph.nodes(childrenIdx)
          columnEntryOfNodes(node.id, Array(node), Components.sidebarNodeFocusMod(state, node.id))
        }(breakOut)
      ) ::
      UI.Column(
        "Tags",
        childrenIdxs.map { childrenIdx =>
          val nodeId = graph.nodeIds(childrenIdx)
          val tags = graph.tagParentsIdx.map(childrenIdx)(idx => graph.nodes(idx))
          columnEntryOfNodes(nodeId, tags)
        }(breakOut)
      ) ::
      UI.Column(
        "Assigned",
        childrenIdxs.map { childrenIdx =>
          val nodeId = graph.nodeIds(childrenIdx)
          val assignedUsers = graph.assignedUsersIdx.map(childrenIdx)(idx => graph.nodes(idx))
          columnEntryOfNodes(nodeId, assignedUsers)
        }(breakOut)
      ) ::
      Nil

    val propertyColumns: List[UI.Column] = sortedProperties.map { propertyString =>
      UI.Column(
        propertyString,
        childrenIdxs.map { childrenIdx =>
          val nodeId = graph.nodeIds(childrenIdx)
          val propertyEdges = nodeToProperties(childrenIdx).getOrElse(propertyString, Array())
          val propertyNodes = propertyEdges.map(edge => graph.nodesById(edge.propertyId))
          columnEntryOfNodes(nodeId, propertyNodes)
        }(breakOut)
      )
    }(breakOut)

    VDomModifier(
      UI.sortableTable(nodeColumns ::: propertyColumns, sort),

      button(
        cls := "ui button",
        freeSolid.faPlus,
        cursor.pointer,
        onClick.stopPropagation.foreach {
          val targetRole = roles match {
            case head :: _ => head
            case Nil       => NodeRole.default
          }

          val newNode = Node.Content(NodeData.Markdown(""), targetRole)

          sort() = None // reset sorting again, so the new node appears at the bottom :)
          state.eventProcessor.changes.onNext(GraphChanges.addNodeWithParent(newNode, focusedId))

          ()
        }
      )
    )
  }

}
