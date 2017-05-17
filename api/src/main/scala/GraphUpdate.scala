package wust.api

import wust.graph._

object GraphUpdate {
  def onEvent(graph: Graph, event: ApiEvent): Graph = {
    event match {
      case NewPost(post) => graph + post
      case NewConnection(connection) => graph + connection
      case NewContainment(containment) => graph + containment
      case NewOwnership(ownership) => graph + ownership
      case NewMembership(user, membership, group) => graph + user + group + membership
      // case NewUser(user, membership) => graph + user + membership
      // case NewGroup(group, membership) => graph + group + membership

      case UpdatedPost(post) => graph + post

      case DeletePost(postId) => graph - postId
      case DeleteConnection(connectionId) => graph - connectionId
      case DeleteContainment(containmentId) => graph - containmentId

      case ReplaceGraph(newGraph) => newGraph

      case _ => graph
    }
  }
}
