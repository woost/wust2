package wust.webApp.state.graphstate

import org.scalatest._
import wust.graph._
import wust.ids._

class EdgeStateSpec extends FreeSpec with MustMatchers {
  def parent(childId:Cuid, parentId:Cuid) = Edge.Child(ParentId(NodeId(parentId)), ChildId(NodeId(childId)))
  def child(parentId:Cuid, childId:Cuid, deletedAt:Option[EpochMilli] = None) = Edge.Child(ParentId(NodeId(parentId)), deletedAt, ChildId(NodeId(childId)))
  def user(id:Cuid) = Node.User(UserId(NodeId(id)), NodeData.User(id.toString, false, 0), NodeMeta.User)
  implicit def stringToCuid(id:String):Cuid = Cuid.fromBase58String("5Q4is6Gc5NbA7T7W7PvAUw".dropRight(id.length) + id).right.get
  implicit def idToNode(id: String): Node = Node.Content(id = NodeId(id:Cuid), data = NodeData.PlainText("content"), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))
  def idToNode(id: String, content:String): Node = Node.Content(id = NodeId(id:Cuid), data = NodeData.PlainText(content), role = NodeRole.default, meta = NodeMeta(NodeAccess.ReadWrite))

  "factory" in {
    val edges = Array[Edge](child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    val edgeState = EdgeState(edges)

    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("B":Cuid) -> NodeId("C":Cuid))) == child("B", "C"))
    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("B":Cuid) -> NodeId("D":Cuid))) == child("B", "D"))
    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("E":Cuid) -> NodeId("F":Cuid))) == child("E", "F"))
    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("F":Cuid) -> NodeId("E":Cuid))) == child("F", "E"))

    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("B":Cuid) -> NodeId("C":Cuid))).now == child("B", "C"))
    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("B":Cuid) -> NodeId("D":Cuid))).now == child("B", "D"))
    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("E":Cuid) -> NodeId("F":Cuid))).now == child("E", "F"))
    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("F":Cuid) -> NodeId("E":Cuid))).now == child("F", "E"))
  }

  "add edges" in {
    val edges = Array[Edge](child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    val edgeState = EdgeState(edges)

    edgeState.update(GraphChanges(addEdges = Array(child("A", "C"), child("D", "E"))))

    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("A":Cuid) -> NodeId("C":Cuid))) == child("A", "C"))
    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("D":Cuid) -> NodeId("E":Cuid))) == child("D", "E"))

    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("A":Cuid) -> NodeId("C":Cuid))).now == child("A", "C"))
    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("D":Cuid) -> NodeId("E":Cuid))).now == child("D", "E"))
  }

  "update edge" in {
    val edges = Array[Edge](child("B", "C"), child("B", "D"), child("E", "F"), child("F", "E"))
    val edgeState = EdgeState(edges)

    edgeState.update(GraphChanges(addEdges = Array(child("A", "C", Some(EpochMilli(0L))), child("D", "E", Some(EpochMilli(1L))))))

    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("A":Cuid) -> NodeId("C":Cuid))) == child("A", "C", Some(EpochMilli(0L))))
    assert(edgeState.edgesNow(edgeState.idToIdxOrThrow(NodeId("D":Cuid) -> NodeId("E":Cuid))) == child("D", "E", Some(EpochMilli(1L))))

    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("A":Cuid) -> NodeId("C":Cuid))).now == child("A", "C", Some(EpochMilli(0L))))
    assert(edgeState.edgesRx(edgeState.idToIdxOrThrow(NodeId("D":Cuid) -> NodeId("E":Cuid))).now == child("D", "E", Some(EpochMilli(1L))))
  }
}
