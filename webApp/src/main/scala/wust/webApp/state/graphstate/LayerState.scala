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

final case class LayerChanges(
  addIdx: Int,
  addEdges: Array[Edge] = Array.empty,
  delEdges: Array[Edge] = Array.empty
)

abstract class LayerState {
  var lookupNow: NestedArrayIntValues = NestedArrayInt.empty
  var revLookupNow: NestedArrayIntValues = NestedArrayInt.empty
  val lookupRx: mutable.ArrayBuffer[Var[Array[Int]]] = mutable.ArrayBuffer.empty
  val revLookupRx: mutable.ArrayBuffer[Var[Array[Int]]] = mutable.ArrayBuffer.empty


  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit]

  def update(nodeState: NodeState, changes: LayerChanges): Unit = {
    val affectedSourceNodes = new mutable.ArrayBuffer[Int]
    val affectedTargetNodes = new mutable.ArrayBuffer[Int]
    val addElemBuilder = new mutable.ArrayBuilder.ofRef[(Int,Int)]
    val addRevElemBuilder = new mutable.ArrayBuilder.ofRef[(Int,Int)]
    changes.addEdges.foreachElement { 
      ifMyEdge { (sourceId, targetId) =>
        nodeState.idToIdxForeach(sourceId) { sourceIdx =>
          nodeState.idToIdxForeach(targetId) { targetIdx =>
            addElemBuilder += sourceIdx -> targetIdx
            addRevElemBuilder += targetIdx -> sourceIdx
            affectedSourceNodes += sourceIdx
            affectedTargetNodes += targetIdx
          }
        }
      }
    }
    val addElem = InterleavedArrayInt(addElemBuilder.result().sortBy(_._1)) //TODO: performance: build InterleavedArray directly to avoid tuples and reiteration in InterleavedArrayInt.apply, but how to sort? Put two ints into one long?
    val addRevElem = InterleavedArrayInt(addRevElemBuilder.result().sortBy(_._1))

    val delElemBuilder = new mutable.ArrayBuilder.ofRef[(Int,Int)]
    val delRevElemBuilder = new mutable.ArrayBuilder.ofRef[(Int,Int)]
    changes.delEdges.foreach {
      ifMyEdge { (sourceId, targetId) =>
        nodeState.idToIdxForeach(sourceId) { sourceIdx =>
          nodeState.idToIdxForeach(targetId) { targetIdx =>
            delElemBuilder += sourceIdx -> lookupNow.indexOf(sourceIdx)(targetIdx) // Remove the first occurence of the sourceId/targetId combination
            delRevElemBuilder += targetIdx -> lookupNow.indexOf(targetIdx)(sourceIdx) // Remove the first occurence of the sourceId/targetId combination
            affectedSourceNodes += sourceIdx
            affectedTargetNodes += targetIdx
          }
        }
      }
    }
    val delElem = InterleavedArrayInt(delElemBuilder.result().sortBy(_._1))
    val delRevElem = InterleavedArrayInt(delRevElemBuilder.result().sortBy(_._1))

    // NestedArray.changed() parameters:
    // addIdx: Int, // how many nodes are added
    // addElem: InterleavedArrayInt // Array[idx -> elem]
    // delElem: InterleavedArrayInt // Array[idx -> position]
    if (scala.scalajs.LinkingInfo.developmentMode) {
      lookupNow = lookupNow.changedWithAssertions(changes.addIdx, addElem, delElem)
      revLookupNow = revLookupNow.changedWithAssertions(changes.addIdx, addRevElem, delRevElem)
    }
    else {
      lookupNow = lookupNow.changed(changes.addIdx, addElem, delElem)
      revLookupNow = revLookupNow.changed(changes.addIdx, addRevElem, delRevElem)
    }

    loop(changes.addIdx) { _ =>
      lookupRx += Var(new Array[Int](0))
      revLookupRx += Var(new Array[Int](0))
    }

    affectedSourceNodes.foreachElement { idx =>
      lookupRx(idx)() = lookupNow(idx).toArray
    }
    affectedTargetNodes.foreachElement { idx =>
      revLookupRx(idx)() = revLookupNow(idx).toArray
    }
  }
}
