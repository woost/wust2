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
  def apply(graphEdges: Array[Edge]) = {
    val edges = mutable.ArrayBuffer.empty[Edge]
    val idToIdxHashMap = mutable.HashMap.empty[(NodeId, NodeId), Int]
    idToIdxHashMap.sizeHint(graphEdges.length)

    graphEdges.foreachIndexAndElement { (idx, edge) =>
      edges += edge
      idToIdxHashMap(edgeKey(edge)) = idx
    }
    new EdgeState(edges, idToIdxHashMap)
  }

  @inline def edgeKey(edge: Edge): (NodeId, NodeId) = edge.sourceId -> edge.targetId
}

final class EdgeState private (
  val edgesNow: mutable.ArrayBuffer[Edge],
  val idToIdxHashMap: mutable.HashMap[(NodeId, NodeId), Int]
) {
  val edgesRx: mutable.ArrayBuffer[Var[Edge]] = edgesNow.map(Var(_))
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

