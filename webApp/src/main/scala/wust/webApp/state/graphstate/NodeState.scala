package wust.webApp.state.graphstate

import rx._
import flatland._
import wust.ids._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.macros.InlineList
import wust.graph._
import wust.util.time.time

import scala.collection.{ breakOut, immutable, mutable }

object NodeState {
  def apply(nodes: Array[Node]):NodeState = {
    val nodeState = new NodeState
    nodeState.update(GraphChanges(addNodes = nodes))
    nodeState
  }
}

final class NodeState {
  val nodesNow: mutable.ArrayBuffer[Node] = mutable.ArrayBuffer.empty
  val idToIdxHashMap: mutable.HashMap[NodeId, Int] = mutable.HashMap.empty
  val nodesRx: mutable.ArrayBuffer[Var[Node]] = mutable.ArrayBuffer.empty

  @inline def idToIdxFold[T](id: NodeId)(default: => T)(f: Int => T): T = {
    idToIdxHashMap.get(id) match {
      case Some(idx) => f(idx)
      case None      => default
    }
  }
  @inline def idToIdxForeach[U](id: NodeId)(f: Int => U): Unit = idToIdxFold(id)(())(f(_))
  @inline def idToIdxMap[T](id: NodeId)(f: Int => T): Option[T] = idToIdxFold(id)(Option.empty[T])(idx => Some(f(idx)))
  @inline def idToIdxOrThrow(nodeId: NodeId): Int = idToIdxHashMap(nodeId)
  def idToIdx(nodeId: NodeId): Option[Int] = idToIdxFold[Option[Int]](nodeId)(None)(Some(_))
  def nodesByIdOrThrow(nodeId: NodeId): Node = nodesNow(idToIdxOrThrow(nodeId))
  def nodesById(nodeId: NodeId): Option[Node] = idToIdxFold[Option[Node]](nodeId)(None)(idx => Some(nodesNow(idx)))

  def update(changes: GraphChanges): LayerChanges = {
    // register new and updated nodes

    var addIdx = 0 // counts the number of newly added nodes
    changes.addNodes.foreachElement { node =>
      val nodeId = node.id
      idToIdxFold(nodeId){
        // add new node and update idToIdxHashMap
        val newIdx = nodesNow.length
        nodesNow += node
        nodesRx += Var(node)
        idToIdxHashMap(nodeId) = newIdx
        addIdx += 1
      }{ idx =>
        // already exists, update node
        nodesNow(idx) = node
        nodesRx(idx)() = node
      }
    }

    assert(nodesNow.length == idToIdxHashMap.size)
    assert(nodesNow.length == nodesRx.length)
    LayerChanges(addIdx, changes.addEdges, changes.delEdges)
  }
}

