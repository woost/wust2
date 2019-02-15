package wust.webApp.state

import wust.graph._
import wust.ids.{NodeId, NodeRole, View}

object ViewHeuristic {

  def apply(graph:Graph, parentId: NodeId, view: Option[View]): View.Visible = view match {
    case Some(view) => visibleView(graph, parentId, view)
    case None =>
      graph.idToIdxGet(parentId).fold[View.Visible](View.Empty) { parentIdx =>
        val node = graph.nodes(parentIdx)
        node.views.fold[View.Visible](bestView(graph, node))(views => visibleView(graph, parentId, views.headOption.getOrElse(View.Empty)))
      }
  }

  def visibleView(graph: Graph, parentId: NodeId, view: View): View.Visible = view match {
    case view: View.Visible => view
    case View.Tasks =>
      graph.idToIdxGet(parentId).fold[View.Visible](View.Empty){ parentIdx =>
        val stageCount = graph.notDeletedChildrenIdx(parentIdx).count { childIdx =>
          val node = graph.nodes(childIdx)
          node.role == NodeRole.Stage && !graph.isDoneStage(node)
        }

        if (stageCount > 0) View.Kanban else View.List
      }
    case View.Conversation => View.Chat

  }

  def bestView(graph: Graph, node: Node): View.Visible = node.role match {
    case NodeRole.Project => View.Dashboard
    case NodeRole.Message => visibleView(graph, node.id, View.Conversation)
    case NodeRole.Task    => visibleView(graph, node.id, View.Tasks)
    case _                => View.Empty
  }
}
