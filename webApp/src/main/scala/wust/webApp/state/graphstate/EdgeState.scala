package wust.webApp.state.graphstate

import acyclic.file
import rx._
import flatland._
import wust.ids._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.macros.InlineList
import wust.graph._
import wust.util.time.time

import scala.collection.{ breakOut, immutable, mutable }

object EdgeState {
  @inline def edgeKey(edge: Edge): (NodeId, NodeId) = edge.sourceId -> edge.targetId

  def apply(nodeState: NodeState, edges: Array[Edge]): EdgeState = {
    val edgeState = new EdgeState(nodeState)
    edgeState.update(GraphChanges(addEdges = edges))
    edgeState
  }
}

final class EdgeState(nodeState: NodeState) {

  val edgesNow: mutable.ArrayBuffer[Edge] = mutable.ArrayBuffer.empty
  val idToIdxHashMap: mutable.HashMap[(NodeId, NodeId), Int] = mutable.HashMap.empty
  val edgesRx: mutable.ArrayBuffer[Var[Edge]] = mutable.ArrayBuffer.empty
  var edgesIdxNow: InterleavedArrayInt = InterleavedArrayInt.empty

  @inline def idToIdxFold[T](endPoints: (NodeId, NodeId))(default: => T)(f: Int => T): T = {
    idToIdxHashMap.get(endPoints) match {
      case Some(idx) => f(idx)
      case None      => default
    }
  }
  @inline def idToIdxForeach[U](endPoints: (NodeId, NodeId))(f: Int => U): Unit = idToIdxFold(endPoints)(())(f(_))
  @inline def idToIdxMap[T](endPoints: (NodeId, NodeId))(f: Int => T): Option[T] = idToIdxFold(endPoints)(Option.empty[T])(idx => Some(f(idx)))
  @inline def idToIdxOrThrow(endPoints: (NodeId, NodeId)): Int = idToIdxHashMap(endPoints)
  def idToIdx(endPoints: (NodeId, NodeId)): Option[Int] = idToIdxFold[Option[Int]](endPoints)(None)(Some(_))

  def update(changes: GraphChanges): Unit = {
    // register new and updated edges
    
    edgesNow.sizeHint(edgesNow.length + changes.addEdges.length)
    edgesRx.sizeHint(edgesRx.length + changes.addEdges.length)
    idToIdxHashMap.sizeHint(idToIdxHashMap.size + changes.addEdges.length)

    val addEdgeIdxBuilder = InterleavedArrayInt.builder
    changes.addEdges.foreachElement { edge =>
      val key = EdgeState.edgeKey(edge)

      idToIdxFold(key){
        val newIdx = edgesNow.length
        edgesNow += edge
        edgesRx += Var(edge)
        idToIdxHashMap(key) = newIdx
        nodeState.idToIdxForeach(edge.sourceId){ sourceIdx =>
          nodeState.idToIdxForeach(edge.targetId){ targetIdx =>
            addEdgeIdxBuilder.add(sourceIdx, targetIdx)
          }
        }
      }{ idx =>
        edgesNow(idx) = edge
        edgesRx(idx)() = edge //TODO: LazyReactiveWrapper
      }
    }

    assert(edgesNow.length == idToIdxHashMap.size)

    edgesIdxNow = new InterleavedArrayInt(edgesIdxNow.interleaved ++ addEdgeIdxBuilder.result().interleaved)
    assert(edgesRx.size == edgesNow.size)
    assert(edgesIdxNow.elementCount == edgesNow.size)
  }
}
