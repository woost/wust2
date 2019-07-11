package wust.webApp.state.graphstate

import org.scalatest._
import wust.graph._
import wust.ids._

class NodeStateSpec extends FreeSpec with MustMatchers {
  def parent(childId:Cuid, parentId:Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))
  def child(parentId:Cuid, childId:Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))
  def user(id:Cuid) = Node.User(UserId(NodeId(id)), NodeData.User(id.toString, false, 0), NodeMeta.User)
  implicit def stringToCuid(id:String):Cuid = Cuid.fromBase58String("5Q4is6Gc5NbA7T7W7PvAUw".dropRight(id.length) + id).right.get
  implicit def idToNode(id: String): Node = Node.Content(id = NodeId(id:Cuid), data = NodeData.PlainText("content"), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))
  def idToNode(id: String, content:String): Node = Node.Content(id = NodeId(id:Cuid), data = NodeData.PlainText(content), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))

  "factory" in {
    val nodes = Array[Node]("A", "B", "C")
    val nodeState = NodeState(nodes)

    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A"))
    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("B":Cuid))) == idToNode("B"))
    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("C":Cuid))) == idToNode("C"))

    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now == idToNode("B"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("C":Cuid))).now == idToNode("C"))
  }

  "add nodes" in {
    val nodes = Array[Node]("A", "B", "C")
    val nodeState = NodeState(nodes)

    nodeState.update(GraphChanges(addNodes = Array("G","H")))

    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A"))
    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("B":Cuid))) == idToNode("B"))
    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("C":Cuid))) == idToNode("C"))
    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("G":Cuid))) == idToNode("G"))
    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("H":Cuid))) == idToNode("H"))

    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("B":Cuid))).now == idToNode("B"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("C":Cuid))).now == idToNode("C"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("G":Cuid))).now == idToNode("G"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("H":Cuid))).now == idToNode("H"))
  }


  "update nodes" in {
    val nodes = Array[Node]("A", "B", "C")
    val nodeState = NodeState(nodes)

    nodeState.update(GraphChanges(addNodes = Array[Node](idToNode("A", "changed"))))

    assert(nodeState.nodesNow(nodeState.idToIdxOrThrow(NodeId("A":Cuid))) == idToNode("A", "changed"))
    assert(nodeState.nodesRx(nodeState.idToIdxOrThrow(NodeId("A":Cuid))).now == idToNode("A", "changed"))
  }

  "update: return LayerChanges" in {
    val nodes = Array[Node]("A", "B", "C")

    val nodeState = NodeState(nodes)
    val layerChanges = nodeState.update(GraphChanges(
      addNodes = Array("G","H"),
      addEdges = Array(child("G", "H")),
      delEdges = Array(child("X", "Y"))
    ))

    val expected = LayerChanges(
      addIdx = 2,
      addEdges = Array(child("G", "H")),
      delEdges = Array(child("X", "Y"))
    )
    assert(layerChanges.addIdx == expected.addIdx)
    assert(layerChanges.addEdges.toList == expected.addEdges.toList)
    assert(layerChanges.delEdges.toList == expected.delEdges.toList)
  }
}
