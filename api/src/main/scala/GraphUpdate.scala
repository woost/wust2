package wust.api

import wust.graph._

object GraphUpdate {
  def onEvent(graph: Graph, event: ApiEvent): Graph = {
    event match {
      case NewMembership(membership) => graph + membership
      case NewUser(user) => graph + user
      case NewGroup(group) => graph + group
      case NewGraphChanges(changes) => graph applyChanges changes

      case ReplaceGraph(newGraph) => newGraph

      case _ => graph
    }
  }
}
