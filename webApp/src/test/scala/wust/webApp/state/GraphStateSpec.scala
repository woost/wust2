package wust.webApp.state.graphstate

import org.scalatest._
import wust.graph._
import wust.ids._

class IncrementalReactiveGraphSpec extends FreeSpec with MustMatchers {
  def parent(childId:Cuid, parentId:Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))
  def child(parentId:Cuid, childId:Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))
  def user(id:Cuid) = Node.User(UserId(NodeId(id)), NodeData.User(id.toString, false, 0), NodeMeta.User)
  implicit def stringToCuid(id:String):Cuid = Cuid.fromBase58String("5Q4is6Gc5NbA7T7W7PvAUw".dropRight(id.length) + id).right.get
  implicit def idToNode(id: String): Node = Node.Content(id = NodeId(id:Cuid), data = NodeData.PlainText("content"), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))
  def idToNode(id: String, content:String): Node = Node.Content(id = NodeId(id:Cuid), data = NodeData.PlainText(content), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))

  "GraphState factory" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List())
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("F"))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("E"))

    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("F"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("E"))
  }

  "GraphState add nodes" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    graphState.update(GraphChanges(addNodes = Array[Node]("G", "H")))
    import graphState.{nodeState, children}

    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("F"))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("E"))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())

    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("F"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("E"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
  }

  "GraphState add edge" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())

    graphState.update(GraphChanges(addEdges = Array(child("A", "C"))))

    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("C"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("C"))
  }

  "GraphState add nodes with edge" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    graphState.update(GraphChanges(addNodes = Array("G","H"), addEdges = Array(child("G", "H"))))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("H"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]("H"))
  }

  "GraphState delete edge" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    graphState.update(GraphChanges(delEdges = Array(child("E", "F"))))
    assert(children.lookupNow(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now.map(idx => nodeState.nodesNow(idx).id).toList == List[Cuid]())
  }

  //TODO: delete for author LabeledProperty edges, where edges can have the same source/target combination
  //TODO: addEdges overwrite edge
}
