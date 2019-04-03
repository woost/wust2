package wust.webApp.state

import wust.graph._
import wust.ids.{NodeId, NodeRole, View}

object ViewHeuristic {

  def apply(graph:Graph, parentId: NodeId, view: Option[View]): Option[View.Visible] = view match {
    case Some(view) => visibleView(graph, parentId, view)
    case None =>
      graph.idToIdxGet(parentId).flatMap[View.Visible] { parentIdx =>
        val node = graph.nodes(parentIdx)
        bestView(graph, node)
      }
  }

  def visibleView(graph: Graph, parentId: NodeId, view: View): Option[View.Visible] = view match {
    case view: View.Visible => Some(view)
    case View.Tasks =>
      graph.idToIdxGet(parentId).map { parentIdx =>
        val stageCount = graph.childrenIdx(parentIdx).count { childIdx =>
          val node = graph.nodes(childIdx)
          node.role == NodeRole.Stage && !graph.isDoneStage(node)
        }

        if (stageCount > 0) View.Kanban else View.List
      }
    case View.Conversation => Some(View.Chat)

  }

  def bestView(graph: Graph, node: Node): Option[View.Visible] = {
    node.views.fold(fallbackView(graph, node))(views => views.headOption.flatMap(visibleView(graph, node.id, _)))
  }

  def fallbackView(graph: Graph, node: Node): Option[View.Visible] = node.role match {
    case NodeRole.Project                 => Some(View.Dashboard)
    case NodeRole.Message | NodeRole.Note => visibleView(graph, node.id, View.Conversation)
    case NodeRole.Task                    => visibleView(graph, node.id, View.Tasks)
    case _ => None
  }
}
