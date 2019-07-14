package wust.webApp.state.graphstate

import scala.reflect.ClassTag
import scala.scalajs.js.JSConverters._
import acyclic.file
import rx._
import flatland._
import wust.ids._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.macros.InlineList
import wust.graph._
import wust.util.time.time
import scala.scalajs.js

import scala.collection.{ breakOut, immutable, mutable }
import scala.scalajs.js.WrappedArray

@inline final class LazyReactiveWrapper(now: => NestedArrayIntValues) {
  val self: mutable.ArrayBuffer[Var[Array[Int]]] = mutable.ArrayBuffer.empty

  @inline def grow(): Unit = { self += null }

  @inline def updateFrom(idx: Int, lookup: NestedArrayInt): Unit = {
    if (self(idx) != null) {
      self(idx)() = lookup(idx).toArray
    }
  }

  @inline def apply(idx: Int): Var[Array[Int]] = {
    if (self(idx) == null) {
      val value = Var(now(idx).toArray)
      self(idx) = value
      value
    } else {
      self(idx)
    }
  }
}

final class InterleavedJSArrayIntBuilder {
  @inline private def extractHi(l: Long): Int = (l >> 32).toInt
  @inline private def extractLo(l: Long): Int = l.toInt
  @inline private def combineToLong(a: Int, b: Int): Long = ((a.toLong) << 32) | (b & 0xffffffffL)

  val self = new js.Array[Long]

  @inline def add(a: Int, b: Int): Unit = {
    self += combineToLong(a, b)
  }

  @inline def result() = self
}

final class LayerState(val edgeState: EdgeState, ifMyEdge: ((NodeId, NodeId) => Unit) => (Edge => Unit)) {
  import edgeState.edgesIdxNow

  var edgeLookupNow: NestedArrayIntValues = NestedArrayInt.empty
  var edgeRevLookupNow: NestedArrayIntValues = NestedArrayInt.empty
  val edgeLookupRx = new LazyReactiveWrapper(edgeLookupNow)
  val edgeRevLookupRx = new LazyReactiveWrapper(edgeRevLookupNow)

  var lookupNow: NestedArrayIntMapped = edgeLookupNow.viewMapInt(edgesIdxNow.right)
  var revLookupNow: NestedArrayIntMapped = edgeRevLookupNow.viewMapInt(edgesIdxNow.left)
  def lookupRx(idx: Int)(implicit ctx: Ctx.Owner) = edgeLookupRx(idx).map(_.map(edgesIdxNow.right))
  def revLookupRx(idx: Int)(implicit ctx: Ctx.Owner) = edgeRevLookupRx(idx).map(_.map(edgesIdxNow.left))

  // @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit]
  @inline def update(changes: LayerChanges): Unit = {
    // time("graphstate:update") {
    val affectedSourceNodes = new mutable.ArrayBuilder.ofInt
    val affectedTargetNodes = new mutable.ArrayBuilder.ofInt
    val addElemBuilder = new InterleavedJSArrayIntBuilder
    val addRevElemBuilder = new InterleavedJSArrayIntBuilder
    time("graphstate:update:addEdges") {
      changes.addEdges.foreachElement {
        ifMyEdge { (sourceId, targetId) =>
          edgeState.idToIdxForeach(sourceId -> targetId) { edgeIdx =>
            val sourceIdx = edgesIdxNow.left(edgeIdx)
            val targetIdx = edgesIdxNow.right(edgeIdx)
            addElemBuilder.add(sourceIdx, edgeIdx)
            addRevElemBuilder.add(targetIdx, edgeIdx)
            affectedSourceNodes += sourceIdx
            affectedTargetNodes += targetIdx
          }
        }
      }
    }
    val addElem = time("graphstate:update:addElem") {
      val interleavedLong = addElemBuilder.result()
      // use native js Array.sort with compare function for long (faster than scala.util.Sorting.quickSort or java.util.Arrays.sort)
      interleavedLong.sort(_ compare _)
      new InterleavedArrayInt(interleavedLong.toArray) // TODO: is there a way to avoid copying? Either convert js.Array to scala array or call js.Array.sort on scala Array?
    }
    val addRevElem = time("graphstate:update:addRevElem") {
      val interleavedLong = addRevElemBuilder.result()
      // use native js Array.sort with compare function for long (faster than scala.util.Sorting.quickSort or java.util.Arrays.sort)
      interleavedLong.sort(_ compare _)
      new InterleavedArrayInt(interleavedLong.toArray) // TODO: is there a way to avoid copying? Either convert js.Array to scala array or call js.Array.sort on scala Array?
    }

    val delElemBuilder = new mutable.ArrayBuilder.ofRef[(Int, Int)]
    val delRevElemBuilder = new mutable.ArrayBuilder.ofRef[(Int, Int)]
    time("graphstate:update:delEdges") {
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
    }
    val delElem = time("graphstate:update:delElem") { InterleavedArrayInt(delElemBuilder.result().sortBy(_._1)) }
    val delRevElem = time("graphstate:update:delRevElem") { InterleavedArrayInt(delRevElemBuilder.result().sortBy(_._1)) }

    time("graphstate:update:change") {
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
    }

    time("graphstate:update:update-rx") {
      lookupNow = edgeLookupNow.viewMapInt(edgesIdxNow.right)
      revLookupNow = edgeRevLookupNow.viewMapInt(edgesIdxNow.left)

      loop(changes.addIdx) { _ =>
        edgeLookupRx.grow()
        edgeRevLookupRx.grow()
      }

      affectedSourceNodes.result().foreachElement { idx =>
        edgeLookupRx.updateFrom(idx, edgeLookupNow)
      }
      affectedTargetNodes.result().foreachElement { idx =>
        edgeRevLookupRx.updateFrom(idx, edgeRevLookupNow)
      }
    }
    // }
  }
}
