package wust.api

import wust.graph._

object EventUpdate {
  import ApiEvent._

  def applyEventOnGraph(graph: Graph, event: ApiEvent.GraphContent): Graph = event match {
    case NewGraphChanges(user, changes)  => graph.applyChangesWithUser(user, changes)

    case ReplaceGraph(newGraph)          => newGraph

    case replaceNode: ReplaceNode => graph.applyChanges(LocalGraphUpdateEvent.calculateReplacementChanges(graph, replaceNode))
  }

  def createAuthFromEvent(event: ApiEvent.AuthContent): Authentication = event match {
    case ApiEvent.LoggedIn(auth)       => auth
    case ApiEvent.AssumeLoggedIn(auth) => auth
  }
}

sealed trait LocalGraphUpdateEvent
object LocalGraphUpdateEvent {
  case class NewGraph(graph: Graph) extends LocalGraphUpdateEvent
  case class NewChanges(changes: GraphChanges) extends LocalGraphUpdateEvent
  val empty: LocalGraphUpdateEvent = NewChanges(GraphChanges.empty)

  def deconstruct(graph: Graph, events: Seq[ApiEvent.GraphContent]): LocalGraphUpdateEvent = {
    var lastChanges: GraphChanges = GraphChanges.empty
    var replacedGraph: Graph = null
    events.foreach {
      case ApiEvent.NewGraphChanges(author, changes) =>
        // do not add author of change if the node was updated, the author might be outdated.
        val completeChanges = if (changes.addNodes.exists(_.id == author.id)) changes else changes.copy(addNodes = changes.addNodes ++ Set(author))
        lastChanges = lastChanges.merge(completeChanges)
      case ApiEvent.ReplaceGraph(graph) =>
        lastChanges = GraphChanges.empty
        replacedGraph = graph
      case r: ApiEvent.ReplaceNode =>
        lastChanges = lastChanges merge calculateReplacementChanges(graph, r)
    }
    lastChanges = lastChanges.consistent
    if (replacedGraph == null) NewChanges(lastChanges) // if there was no ReplaceGraph, just return the accumulated changes
    else NewGraph(replacedGraph.applyChanges(lastChanges)) // if there is a new graph, return the graph with succeeding changes already applied
  }

  def calculateReplacementChanges(graph: Graph, replaceNode: ApiEvent.ReplaceNode): GraphChanges = {
    import replaceNode.{ oldNodeId, newNode }

    val addEdgesBuilder = Array.newBuilder[Edge]
    val delEdgesBuilder = Array.newBuilder[Edge]
    graph.edges.foreach { e =>
      if (e.sourceId == oldNodeId && e.targetId == oldNodeId) {
        // self-loop
        delEdgesBuilder += e
        addEdgesBuilder += e.copyId(sourceId = newNode.id, targetId = newNode.id)
      } else if (e.sourceId == oldNodeId) {
        // replace sourceId
        delEdgesBuilder += e
        addEdgesBuilder += e.copyId(sourceId = newNode.id, targetId = e.targetId)
      } else if (e.targetId == oldNodeId) {
        // replace targetId
        delEdgesBuilder += e
        addEdgesBuilder += e.copyId(sourceId = e.sourceId, targetId = newNode.id)
      }
    }

    GraphChanges(
      addNodes = Array(newNode),
      addEdges = addEdgesBuilder.result(),
      delEdges = delEdgesBuilder.result()
    )
  }

}
