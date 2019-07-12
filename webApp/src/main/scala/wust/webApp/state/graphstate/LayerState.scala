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
    val addElemBuilder = new mutable.ArrayBuilder.ofInt
    changes.addEdges.foreachElement { 
      ifMyEdge { (sourceId, targetId) =>
        nodeState.idToIdxForeach(sourceId) { sourceIdx =>
          nodeState.idToIdxForeach(targetId) { targetIdx =>
            addElemBuilder += sourceIdx
            addElemBuilder += targetIdx
            affectedSourceNodes += sourceIdx
          }
        }
      }
    }
    val addElem = new InterleavedArrayInt(addElemBuilder.result())

    val delElemBuilder = new mutable.ArrayBuilder.ofInt
    changes.delEdges.foreach {
      ifMyEdge { (sourceId, targetId) =>
        nodeState.idToIdxForeach(sourceId) { sourceIdx =>
          nodeState.idToIdxForeach(targetId) { targetIdx =>
            delElemBuilder += sourceIdx
            delElemBuilder += lookupNow.indexOf(sourceIdx)(targetIdx) // Remove the first occurence of the sourceId/targetId combination
            affectedSourceNodes += sourceIdx
          }
        }
      }
    }
    val delElem = new InterleavedArrayInt(delElemBuilder.result())

    // NestedArray.changed() parameters:
    // addIdx: Int, // how many nodes are added
    // addElem: InterleavedArrayInt // Array[idx -> elem]
    // delElem: InterleavedArrayInt // Array[idx -> position]
    // if (scala.scalajs.LinkingInfo.developmentMode)
    //   lookupNow = lookupNow.changedWithAssertions(changes.addIdx, addElem, delElem)
    // else
    lookupNow = lookupNow.changed(changes.addIdx, addElem, delElem)

    loop(changes.addIdx) { _ =>
      lookupRx += Var(new Array[Int](0))
    }

    affectedSourceNodes.foreachElement { idx =>
      lookupRx(idx)() = lookupNow(idx).toArray
    }
  }
}
