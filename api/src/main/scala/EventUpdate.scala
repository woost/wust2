package wust.api

import wust.graph._

object EventUpdate {
  import ApiEvent._

  def applyEventOnGraph(graph: Graph, event: ApiEvent.GraphContent): Graph = event match {
    case NewMembership(membership) => graph + membership
    case NewUser(user) => graph + user
    case NewGroup(group) => graph + group
    case NewGraphChanges(changes) => graph applyChanges changes

    case ReplaceGraph(newGraph) => newGraph
  }

  def createAuthFromEvent(event: ApiEvent.AuthContent): Authentication = event match {
    case ApiEvent.LoggedIn(auth) => auth
    case ApiEvent.AssumeLoggedIn(auth) => auth
  }
}
