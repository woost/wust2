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

object EdgeState {
  @inline def edgeKey(edge: Edge): (NodeId, NodeId) = edge.sourceId -> edge.targetId

  def apply(edges: Array[Edge]):EdgeState = {
    val edgeState = new EdgeState
    edgeState.update(GraphChanges(addEdges = edges))
    edgeState
  }
}

final class EdgeState {
  val edgesNow: mutable.ArrayBuffer[Edge] = mutable.ArrayBuffer.empty
  val idToIdxHashMap: mutable.HashMap[(NodeId, NodeId), Int] = mutable.HashMap.empty
  val edgesRx: mutable.ArrayBuffer[Var[Edge]] = mutable.ArrayBuffer.empty

  @inline def idToIdxOrThrow(endPoints: (NodeId, NodeId)): Int = idToIdxHashMap(endPoints)

  def update(changes: GraphChanges): Unit = {
    // register new and updated edges

    changes.addEdges.foreachElement { edge =>
      val key = EdgeState.edgeKey(edge)

      idToIdxHashMap.get(key) match {
        case Some(idx) =>
          edgesNow(idx) = edge
          edgesRx(idx)() = edge
        case None =>
          val newIdx = edgesNow.length
          edgesNow += edge
          edgesRx += Var(edge)
          idToIdxHashMap(key) = newIdx
      }

      assert(edgesNow.length == idToIdxHashMap.size)
    }
  }
}

