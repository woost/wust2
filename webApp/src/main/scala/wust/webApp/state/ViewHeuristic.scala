package wust.webApp.state

import wust.graph._
import wust.ids._

object ViewHeuristic {

  def apply(graph: Graph, parentId: NodeId, view: Option[ViewName], userId:UserId): Option[View] = view match {
    case Some(view) => visibleView(graph, parentId, view)
    case None =>
      graph.idToIdx(parentId).flatMap[View] { parentIdx =>
        val node = graph.nodes(parentIdx)
        bestView(graph, node, userId)
      }
  }

  def visibleView(graph: Graph, parentId: NodeId, viewName: ViewName): Option[View] = {
    ??? //fIXME:
    // case view => Some(view)
    // case View.Tasks =>
    //   graph.idToIdx(parentId).map { parentIdx =>
    //     val hasStagesOtherThanDone = graph.childrenIdx(parentIdx).exists { childIdx =>
    //       val node = graph.nodes(childIdx)
    //       node.role == NodeRole.Stage && !graph.isDoneStage(node)
    //     }

    //     if (hasStagesOtherThanDone) View.Kanban else View.List
    //   }

  }

  def bestView(graph: Graph, node: Node, userId:UserId): Option[View] = {
    ??? //FIXME
    // node.views.fold(fallbackView(graph, node)){ views =>
    //   val roleStats = graph.topLevelRoleStats(userId, node.id)
    //   (if(roleStats.nonEmpty)
    //   views.find(_.view match {
    //     case View.Dashboard => true
    //     // unread
    //     case View.Table(roles) if roles.exists(_ == NodeRole.Message) && roleStats.messageStat.unreadCount > 0 => true
    //     case View.Table(roles) if roles.exists(_ == NodeRole.Note) && roleStats.noteStat.unreadCount > 0 => true
    //     case View.Table(roles) if roles.exists(_ == NodeRole.Task) && roleStats.taskStat.unreadCount > 0 => true
    //     case View.Chat if roleStats.messageStat.unreadCount > 0 => true
    //     case View.Thread if roleStats.messageStat.unreadCount > 0 => true
    //     case View.List if roleStats.taskStat.unreadCount > 0 => true
    //     case View.Kanban if roleStats.taskStat.unreadCount > 0 => true
    //     case View.Content if roleStats.noteStat.unreadCount > 0 => true
    //     // non-empty
    //     case View.Table(roles) if roles.exists(_ == NodeRole.Message) && roleStats.messageStat.count > 0 => true
    //     case View.Table(roles) if roles.exists(_ == NodeRole.Note) && roleStats.noteStat.count > 0 => true
    //     case View.Table(roles) if roles.exists(_ == NodeRole.Task) && roleStats.taskStat.count > 0 => true
    //     case View.Chat if roleStats.messageStat.count > 0 => true
    //     case View.Thread if roleStats.messageStat.count > 0 => true
    //     case View.List if roleStats.taskStat.count > 0 => true
    //     case View.Kanban if roleStats.taskStat.count > 0 => true
    //     case View.Content if roleStats.noteStat.count > 0 => true

    //     case _ => false
    //   }) else None
    //   ).orElse(views.headOption)
    //   .flatMap(v => visibleView(graph, node.id, v.view))
    // }
  }

  def fallbackView(graph: Graph, node: Node): Option[View] = View.forNodeRole(node.role)
}
