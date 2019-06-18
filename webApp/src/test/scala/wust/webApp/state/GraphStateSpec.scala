package wust.webApp.state

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
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("B":Cuid))) == idToNode("B"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("C":Cuid))) == idToNode("C"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("D":Cuid))) == idToNode("D"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("E":Cuid))) == idToNode("E"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("F":Cuid))) == idToNode("F"))

    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now == idToNode("B"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("C":Cuid))).now == idToNode("C"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("D":Cuid))).now == idToNode("D"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now == idToNode("E"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).now == idToNode("F"))

    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List())
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("F"))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("E"))

    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("F"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("E"))
  }

  "GraphState add nodes" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    graphState.update(GraphChanges(addNodes = Array[Node]("G", "H")))
    import graphState.{nodeState, children}
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("B":Cuid))) == idToNode("B"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("C":Cuid))) == idToNode("C"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("D":Cuid))) == idToNode("D"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("E":Cuid))) == idToNode("E"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("F":Cuid))) == idToNode("F"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("G":Cuid))) == idToNode("G"))
    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("H":Cuid))) == idToNode("H"))

    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now == idToNode("B"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("C":Cuid))).now == idToNode("C"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("D":Cuid))).now == idToNode("D"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now == idToNode("E"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).now == idToNode("F"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).now == idToNode("G"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("H":Cuid))).now == idToNode("H"))

    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("F"))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("E"))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())

    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("C", "D"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("F"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("F":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("E"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
  }

  "GraphState update node" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A"))

    graphState.update(GraphChanges(addNodes = Array[Node](idToNode("A", "changed"))))

    assert(nodeState.nodes(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A", "changed"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A", "changed"))
  }

  "GraphState add edge" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())

    graphState.update(GraphChanges(addEdges = Array(child("A", "C"))))

    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("C"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("C"))
  }

  "GraphState add nodes with edge" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    graphState.update(GraphChanges(addNodes = Array("G","H"), addEdges = Array(child("G", "H"))))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("H"))
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]("H"))
  }

  "GraphState delete edge" in {
    val graph = Graph(
      nodes = Array("A", "B", "C", "D", "E", "F"),
      edges = Array(child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    )

    val graphState = new GraphState(graph)
    import graphState.{nodeState, children}

    graphState.update(GraphChanges(delEdges = Array(child("E", "F"))))
    assert(children.lookup(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
    assert(children.lookupRx(nodeState.idToIdxOrThrow(NodeId("E":Cuid))).now.map(idx => nodeState.nodes(idx).id).toList == List[Cuid]())
  }

  //TODO: delete for author LabeledProperty edges, where edges can have the same source/target combination
  //TODO: addEdges overwrite edge
}
