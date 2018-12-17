package wust.api

import wust.graph._

object EventUpdate {
  import ApiEvent._

  def applyEventOnGraph(graph: Graph, event: ApiEvent.GraphContent): Graph = event match {
    case NewGraphChanges(user, changes) => graph.applyChangesWithUser(user, changes)

    case ReplaceGraph(newGraph) => newGraph

    case ReplaceNode(oldNodeId, newNode) => graph.replaceNode(oldNodeId, newNode)
  }

  def createAuthFromEvent(event: ApiEvent.AuthContent): Authentication = event match {
    case ApiEvent.LoggedIn(auth)       => auth
    case ApiEvent.AssumeLoggedIn(auth) => auth
  }
}
