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
  // val nodeState: NodeState
  val edgeState: EdgeState

  import edgeState.edgesIdxNow

  var edgeLookupNow: NestedArrayIntValues = NestedArrayInt.empty
  var edgeRevLookupNow: NestedArrayIntValues = NestedArrayInt.empty
  val edgeLookupRx: mutable.ArrayBuffer[Var[Array[Int]]] = mutable.ArrayBuffer.empty
  val edgeRevLookupRx: mutable.ArrayBuffer[Var[Array[Int]]] = mutable.ArrayBuffer.empty

  var lookupNow: NestedArrayIntMapped = edgeLookupNow.viewMapInt(edgesIdxNow.right)
  var revLookupNow: NestedArrayIntMapped = edgeRevLookupNow.viewMapInt(edgesIdxNow.left)
  def lookupRx(idx: Int)(implicit ctx: Ctx.Owner) = edgeLookupRx(idx).map(_.map(edgesIdxNow.right))
  def revLookupRx(idx: Int)(implicit ctx: Ctx.Owner) = edgeRevLookupRx(idx).map(_.map(edgesIdxNow.left))

  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit]

  def update(changes: LayerChanges): Unit = {
    val affectedSourceNodes = new mutable.ArrayBuffer[Int]
    val affectedTargetNodes = new mutable.ArrayBuffer[Int]
    val addElemBuilder = new mutable.ArrayBuilder.ofRef[(Int, Int)]
    val addRevElemBuilder = new mutable.ArrayBuilder.ofRef[(Int, Int)]
    changes.addEdges.foreachElement {
      ifMyEdge { (sourceId, targetId) =>
        edgeState.idToIdxForeach(sourceId -> targetId) { edgeIdx =>
          val sourceIdx = edgesIdxNow.left(edgeIdx)
          val targetIdx = edgesIdxNow.right(edgeIdx)
          addElemBuilder += sourceIdx -> edgeIdx
          addRevElemBuilder += targetIdx -> edgeIdx
          affectedSourceNodes += sourceIdx
          affectedTargetNodes += targetIdx
        }
      }
    }
    val addElem = InterleavedArrayInt(addElemBuilder.result().sortBy(_._1)) //TODO: performance: build InterleavedArray directly to avoid tuples and reiteration in InterleavedArrayInt.apply, but how to sort? Put two ints into one long?
    val addRevElem = InterleavedArrayInt(addRevElemBuilder.result().sortBy(_._1))

    val delElemBuilder = new mutable.ArrayBuilder.ofRef[(Int, Int)]
    val delRevElemBuilder = new mutable.ArrayBuilder.ofRef[(Int, Int)]
    changes.delEdges.foreach {
      ifMyEdge { (sourceId, targetId) =>
        edgeState.idToIdxForeach(sourceId -> targetId) { edgeIdx =>
          val sourceIdx = edgesIdxNow.left(edgeIdx)
          val targetIdx = edgesIdxNow.right(edgeIdx)
          delElemBuilder += sourceIdx -> lookupNow.indexOf(sourceIdx)(edgeIdx) // Remove the first occurence of the sourceId/targetId combination
          delRevElemBuilder += targetIdx -> lookupNow.indexOf(targetIdx)(edgeIdx) // Remove the first occurence of the sourceId/targetId combination
          affectedSourceNodes += sourceIdx
          affectedTargetNodes += targetIdx
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
      edgeLookupNow = edgeLookupNow.changedWithAssertions(changes.addIdx, addElem, delElem)
      edgeRevLookupNow = edgeRevLookupNow.changedWithAssertions(changes.addIdx, addRevElem, delRevElem)
    } else {
      edgeLookupNow = edgeLookupNow.changed(changes.addIdx, addElem, delElem)
      edgeRevLookupNow = edgeRevLookupNow.changed(changes.addIdx, addRevElem, delRevElem)
    }

    lookupNow = edgeLookupNow.viewMapInt(edgesIdxNow.right)
    revLookupNow = edgeRevLookupNow.viewMapInt(edgesIdxNow.left)

    loop(changes.addIdx) { _ =>
      edgeLookupRx += Var(new Array[Int](0))
      edgeRevLookupRx += Var(new Array[Int](0))
    }

    affectedSourceNodes.foreachElement { idx =>
      edgeLookupRx(idx)() = edgeLookupNow(idx).toArray
    }
    affectedTargetNodes.foreachElement { idx =>
      edgeRevLookupRx(idx)() = edgeRevLookupNow(idx).toArray
    }
  }
}
