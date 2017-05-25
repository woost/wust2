package wust.api

import wust.graph._

object GraphUpdate {
  def onEvent(graph: Graph, event: ApiEvent): Graph = {
    event match {
      case NewPost(post) => graph + post
      case NewConnection(connection) => graph + connection
      case NewContainment(containment) => graph + containment
      case NewOwnership(ownership) => graph + ownership
      case NewMembership(membership) => graph + membership
      case NewUser(user) => graph + user
      case NewGroup(group) => graph + group

      case UpdatedPost(post) => graph + post

      case DeletePost(postId) => graph - postId
      case DeleteConnection(connection) => graph - connection
      case DeleteContainment(containment) => graph - containment

      case ReplaceGraph(newGraph) => newGraph

      case _ => graph
    }
  }
}
