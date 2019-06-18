package wust.webApp.state

import rx._
import flatland._
import wust.ids._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.macros.InlineList
import wust.graph._
import wust.util.time.time

import scala.collection.{ breakOut, immutable, mutable }

//TODO: idempotence
//TODO: commutativity (e.g. store edges without loaded nodes until the nodes appear)

class GraphState(initialGraph: Graph) {
  import initialGraph.{ nodes, edges }
  val nodeState = NodeState(initialGraph.nodes)
  val edgeState = EdgeState(initialGraph.edges)
  import nodeState.idToIdxForeach

  val n = initialGraph.size
  private val consistentEdges = ArraySet.create(edges.length)
  val edgesIdx = InterleavedArrayInt.create(edges.length)

  // TODO: have one big triple nested array for all edge lookups?

  // To avoid array builders for each node, we collect the node degrees in a
  // loop and then add the indices in a second loop. This is twice as fast
  // than using one loop with arraybuilders. (A lot less allocations)
  // private val outDegree = new Array[Int](n)
  // private val parentsDegree = new Array[Int](n)
  // private val contentsDegree = new Array[Int](n)
  private val readDegree = new Array[Int](n)
  private val childrenDegree = new Array[Int](n)
  // private val messageChildrenDegree = new Array[Int](n)
  // private val taskChildrenDegree = new Array[Int](n)
  // private val noteChildrenDegree = new Array[Int](n)
  // private val projectChildrenDegree = new Array[Int](n)
  // private val tagChildrenDegree = new Array[Int](n)
  // private val tagParentsDegree = new Array[Int](n)
  // private val stageParentsDegree = new Array[Int](n)
  // private val notDeletedParentsDegree = new Array[Int](n)
  // private val notDeletedChildrenDegree = new Array[Int](n)
  // private val authorshipDegree = new Array[Int](n)
  // private val membershipsForNodeDegree = new Array[Int](n)
  // private val notifyByUserDegree = new Array[Int](n)
  // private val pinnedNodeDegree = new Array[Int](n)
  // private val inviteNodeDegree = new Array[Int](n)
  // private val expandedEdgesDegree = new Array[Int](n)
  // private val assignedNodesDegree = new Array[Int](n)
  // private val assignedUsersDegree = new Array[Int](n)
  // private val propertiesDegree = new Array[Int](n)
  // private val propertiesReverseDegree = new Array[Int](n)
  // private val automatedDegree = new Array[Int](n)
  // private val automatedReverseDegree = new Array[Int](n)
  // private val derivedFromTemplateDegree = new Array[Int](n)

  private val buildNow = EpochMilli.now

  edges.foreachIndexAndElement { (edgeIdx, edge) =>
    idToIdxForeach(edge.sourceId) { sourceIdx =>
      idToIdxForeach(edge.targetId) { targetIdx =>
        consistentEdges.add(edgeIdx)
        edgesIdx.updatea(edgeIdx, sourceIdx)
        edgesIdx.updateb(edgeIdx, targetIdx)
        // outDegree(sourceIdx) += 1
        // edge match {
        //   case e: Edge.Content => contentsDegree(sourceIdx) += 1
        //   case _               =>
        // }

        edge match {
          //   case _: Edge.Author =>
          //     authorshipDegree(sourceIdx) += 1
          //   case _: Edge.Member =>
          //     membershipsForNodeDegree(sourceIdx) += 1
          case e: Edge.Child =>
            //     val childIsMessage = nodes(targetIdx).role == NodeRole.Message
            //     val childIsTask = nodes(targetIdx).role == NodeRole.Task
            //     val childIsNote = nodes(targetIdx).role == NodeRole.Note
            //     val childIsProject = nodes(targetIdx).role == NodeRole.Project
            //     val childIsTag = nodes(targetIdx).role == NodeRole.Tag
            //     val parentIsTag = nodes(sourceIdx).role == NodeRole.Tag
            //     val parentIsStage = nodes(sourceIdx).role == NodeRole.Stage
            //     parentsDegree(targetIdx) += 1
            childrenDegree(sourceIdx) += 1

          //     if (childIsProject) projectChildrenDegree(sourceIdx) += 1
          //     if (childIsMessage) messageChildrenDegree(sourceIdx) += 1
          //     if (childIsTask) taskChildrenDegree(sourceIdx) += 1
          //     if (childIsNote) noteChildrenDegree(sourceIdx) += 1

          //     e.data.deletedAt match {
          //       case None =>
          //         if (childIsTag) tagChildrenDegree(sourceIdx) += 1
          //         if (parentIsTag) tagParentsDegree(targetIdx) += 1
          //         if (parentIsStage) stageParentsDegree(targetIdx) += 1
          //         notDeletedParentsDegree(targetIdx) += 1
          //         notDeletedChildrenDegree(sourceIdx) += 1
          //       case Some(deletedAt) =>
          //         if (deletedAt isAfter buildNow) { // in the future
          //           if (childIsTag) tagChildrenDegree(sourceIdx) += 1
          //           if (parentIsTag) tagParentsDegree(targetIdx) += 1
          //           if (parentIsStage) stageParentsDegree(targetIdx) += 1
          //           notDeletedParentsDegree(targetIdx) += 1
          //           notDeletedChildrenDegree(sourceIdx) += 1
          //         }
          //       // TODO everything deleted further in the past should already be filtered in backend
          //       // BUT received on request
          //     }
          //   case _: Edge.Assigned =>
          //     assignedNodesDegree(targetIdx) += 1
          //     assignedUsersDegree(sourceIdx) += 1
          //   case _: Edge.Expanded =>
          //     expandedEdgesDegree(sourceIdx) += 1
          //   case _: Edge.Notify =>
          //     notifyByUserDegree(targetIdx) += 1
          //   case _: Edge.Pinned =>
          //     pinnedNodeDegree(targetIdx) += 1
          //   case _: Edge.Invite =>
          //     inviteNodeDegree(targetIdx) += 1
          //   case _: Edge.LabeledProperty =>
          //     propertiesDegree(sourceIdx) += 1
          //     propertiesReverseDegree(targetIdx) += 1
          //   case _: Edge.Automated =>
          //     automatedDegree(sourceIdx) += 1
          //     automatedReverseDegree(targetIdx) += 1
          //   case _: Edge.DerivedFromTemplate =>
          //     derivedFromTemplateDegree(sourceIdx) += 1
          case _: Edge.Read =>
            readDegree(sourceIdx) += 1
          case _ =>
        }
      }
    }
  }

  // private val outgoingEdgeIdxBuilder = NestedArrayInt.builder(outDegree)
  // private val parentsIdxBuilder = NestedArrayInt.builder(parentsDegree)
  // private val parentEdgeIdxBuilder = NestedArrayInt.builder(parentsDegree)
  // private val contentsEdgeIdxBuilder = NestedArrayInt.builder(contentsDegree)
  private val readEdgeIdxBuilder = NestedArrayInt.builder(readDegree)
  private val childrenIdxBuilder = NestedArrayInt.builder(childrenDegree)
  // private val childEdgeIdxBuilder = NestedArrayInt.builder(childrenDegree)
  // private val messageChildrenIdxBuilder = NestedArrayInt.builder(messageChildrenDegree)
  // private val taskChildrenIdxBuilder = NestedArrayInt.builder(taskChildrenDegree)
  // private val noteChildrenIdxBuilder = NestedArrayInt.builder(noteChildrenDegree)
  // private val projectChildrenIdxBuilder = NestedArrayInt.builder(projectChildrenDegree)
  // private val tagChildrenIdxBuilder = NestedArrayInt.builder(tagChildrenDegree)
  // private val tagParentsIdxBuilder = NestedArrayInt.builder(tagParentsDegree)
  // private val stageParentsIdxBuilder = NestedArrayInt.builder(stageParentsDegree)
  // private val notDeletedParentsIdxBuilder = NestedArrayInt.builder(notDeletedParentsDegree)
  // private val notDeletedChildrenIdxBuilder = NestedArrayInt.builder(notDeletedChildrenDegree)
  // private val authorshipEdgeIdxBuilder = NestedArrayInt.builder(authorshipDegree)
  // private val authorIdxBuilder = NestedArrayInt.builder(authorshipDegree)
  // private val membershipEdgeForNodeIdxBuilder = NestedArrayInt.builder(membershipsForNodeDegree)
  // private val notifyByUserIdxBuilder = NestedArrayInt.builder(notifyByUserDegree)
  // private val pinnedNodeIdxBuilder = NestedArrayInt.builder(pinnedNodeDegree)
  // private val inviteNodeIdxBuilder = NestedArrayInt.builder(inviteNodeDegree)
  // private val expandedEdgeIdxBuilder = NestedArrayInt.builder(expandedEdgesDegree)
  // private val assignedNodesIdxBuilder = NestedArrayInt.builder(assignedNodesDegree)
  // private val assignedUsersIdxBuilder = NestedArrayInt.builder(assignedUsersDegree)
  // private val propertiesEdgeIdxBuilder = NestedArrayInt.builder(propertiesDegree)
  // private val propertiesEdgeReverseIdxBuilder = NestedArrayInt.builder(propertiesReverseDegree)
  // private val automatedEdgeIdxBuilder = NestedArrayInt.builder(automatedDegree)
  // private val automatedEdgeReverseIdxBuilder = NestedArrayInt.builder(automatedReverseDegree)
  // private val derivedFromTemplateEdgeIdxBuilder = NestedArrayInt.builder(derivedFromTemplateDegree)

  consistentEdges.foreach { edgeIdx =>
    val sourceIdx = edgesIdx.a(edgeIdx)
    val targetIdx = edgesIdx.b(edgeIdx)
    val edge = edges(edgeIdx)
    // outgoingEdgeIdxBuilder.add(sourceIdx, edgeIdx)

    // edge match {
    //   case e: Edge.Content => contentsEdgeIdxBuilder.add(sourceIdx, edgeIdx)

    //   case _               =>
    // }

    edge match {
      // case _: Edge.Author =>
      //   authorshipEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      //   authorIdxBuilder.add(sourceIdx, targetIdx)
      // case _: Edge.Member =>
      //   membershipEdgeForNodeIdxBuilder.add(sourceIdx, edgeIdx)
      case e: Edge.Child =>
        //   val childIsMessage = nodes(targetIdx).role == NodeRole.Message
        //   val childIsTask = nodes(targetIdx).role == NodeRole.Task
        //   val childIsNote = nodes(targetIdx).role == NodeRole.Note
        //   val childIsTag = nodes(targetIdx).role == NodeRole.Tag
        //   val childIsProject = nodes(targetIdx).role == NodeRole.Project
        //   val parentIsTag = nodes(sourceIdx).role == NodeRole.Tag
        //   val parentIsStage = nodes(sourceIdx).role == NodeRole.Stage
        //   parentsIdxBuilder.add(targetIdx, sourceIdx)
        //   parentEdgeIdxBuilder.add(targetIdx, edgeIdx)
        childrenIdxBuilder.add(sourceIdx, targetIdx)
      //   childEdgeIdxBuilder.add(sourceIdx, edgeIdx)

      //   if (childIsProject) projectChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //   if (childIsMessage) messageChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //   if (childIsTask) taskChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //   if (childIsNote) noteChildrenIdxBuilder.add(sourceIdx, targetIdx)

      //   e.data.deletedAt match {
      //     case None =>
      //       if (childIsTag) tagChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //       if (parentIsTag) tagParentsIdxBuilder.add(targetIdx, sourceIdx)
      //       if (parentIsStage) stageParentsIdxBuilder.add(targetIdx, sourceIdx)
      //       notDeletedParentsIdxBuilder.add(targetIdx, sourceIdx)
      //       notDeletedChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //     case Some(deletedAt) =>
      //       if (deletedAt isAfter buildNow) { // in the future
      //         if (childIsTag) tagChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //         if (parentIsTag) tagParentsIdxBuilder.add(targetIdx, sourceIdx)
      //         if (parentIsStage) stageParentsIdxBuilder.add(targetIdx, sourceIdx)
      //         notDeletedParentsIdxBuilder.add(targetIdx, sourceIdx)
      //         notDeletedChildrenIdxBuilder.add(sourceIdx, targetIdx)
      //       }
      //     // TODO everything deleted further in the past should already be filtered in backend
      //     // BUT received on request
      //   }
      // case _: Edge.Expanded =>
      //   expandedEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      // case _: Edge.Assigned =>
      //   assignedNodesIdxBuilder.add(targetIdx, sourceIdx)
      //   assignedUsersIdxBuilder.add(sourceIdx, targetIdx)
      // case _: Edge.Notify =>
      //   notifyByUserIdxBuilder.add(targetIdx, sourceIdx)
      // case _: Edge.Pinned =>
      //   pinnedNodeIdxBuilder.add(targetIdx, sourceIdx)
      // case _: Edge.Invite =>
      //   inviteNodeIdxBuilder.add(targetIdx, sourceIdx)
      // case _: Edge.LabeledProperty =>
      //   propertiesEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      //   propertiesEdgeReverseIdxBuilder.add(targetIdx, edgeIdx)
      // case _: Edge.Automated =>
      //   automatedEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      //   automatedEdgeReverseIdxBuilder.add(targetIdx, edgeIdx)
      // case _: Edge.DerivedFromTemplate =>
      //   derivedFromTemplateEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      case _: Edge.Read =>
        readEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      case _ =>
    }
  }

  // val outgoingEdgeIdx: NestedArrayInt = outgoingEdgeIdxBuilder.result()
  // val parentsIdx: NestedArrayInt = parentsIdxBuilder.result()
  // val parentEdgeIdx: NestedArrayInt = parentEdgeIdxBuilder.result()
  val readEdgeIdx: NestedArrayInt = readEdgeIdxBuilder.result()
  val childrenIdx: NestedArrayInt = childrenIdxBuilder.result()
  // val childEdgeIdx: NestedArrayInt = childEdgeIdxBuilder.result()
  // val contentsEdgeIdx: NestedArrayInt = contentsEdgeIdxBuilder.result()
  // val messageChildrenIdx: NestedArrayInt = messageChildrenIdxBuilder.result()
  // val taskChildrenIdx: NestedArrayInt = taskChildrenIdxBuilder.result()
  // val noteChildrenIdx: NestedArrayInt = noteChildrenIdxBuilder.result()
  // val tagChildrenIdx: NestedArrayInt = tagChildrenIdxBuilder.result()
  // val projectChildrenIdx: NestedArrayInt = projectChildrenIdxBuilder.result()
  // val tagParentsIdx: NestedArrayInt = tagParentsIdxBuilder.result()
  // val stageParentsIdx: NestedArrayInt = stageParentsIdxBuilder.result()
  // val notDeletedParentsIdx: NestedArrayInt = notDeletedParentsIdxBuilder.result()
  // val notDeletedChildrenIdx: NestedArrayInt = notDeletedChildrenIdxBuilder.result()
  // val authorshipEdgeIdx: NestedArrayInt = authorshipEdgeIdxBuilder.result()
  // val membershipEdgeForNodeIdx: NestedArrayInt = membershipEdgeForNodeIdxBuilder.result()
  // val notifyByUserIdx: NestedArrayInt = notifyByUserIdxBuilder.result()
  // val authorsIdx: NestedArrayInt = authorIdxBuilder.result()
  // val pinnedNodeIdx: NestedArrayInt = pinnedNodeIdxBuilder.result()
  // val inviteNodeIdx: NestedArrayInt = inviteNodeIdxBuilder.result()
  // val expandedEdgeIdx: NestedArrayInt = expandedEdgeIdxBuilder.result()
  // val assignedNodesIdx: NestedArrayInt = assignedNodesIdxBuilder.result() // user -> node
  // val assignedUsersIdx: NestedArrayInt = assignedUsersIdxBuilder.result() // node -> user
  // val propertiesEdgeIdx: NestedArrayInt = propertiesEdgeIdxBuilder.result() // node -> property edge
  // val propertiesEdgeReverseIdx: NestedArrayInt = propertiesEdgeReverseIdxBuilder.result() // node -> property edge
  // val automatedEdgeIdx: NestedArrayInt = automatedEdgeIdxBuilder.result()
  // val automatedEdgeReverseIdx: NestedArrayInt = automatedEdgeReverseIdxBuilder.result()
  // val derivedFromTemplateEdgeIdx: NestedArrayInt = derivedFromTemplateEdgeIdxBuilder.result()
  val children = new ChildrenLayer(childrenIdx)
  val read = new ChildrenLayer(childrenIdx)

  // private val sortedAuthorshipEdgeIdx: NestedArrayInt = NestedArrayInt(authorshipEdgeIdx.map(slice => slice.sortBy(author => edges(author).as[Edge.Author].data.timestamp).toArray)(breakOut) : Array[Array[Int]])

  // private val (nodeCreated: mutable.ArrayBuffer[Var[EpochMilli]], nodeCreatorIdx: mutable.ArrayBuffer[Int], nodeModified: mutable.ArrayBuffer[Var[EpochMilli]]) = {
  //   val nodeCreator = new mutable.ArrayBuffer[Int](n)
  //   val nodeCreated = new mutable.ArrayBuffer.fill[Var[EpochMilli]](n)(Var(EpochMilli.min)) // filled with 0L = EpochMilli.min by default
  //   val nodeModified = new mutable.ArrayBuffer.fill[Var[EpochMilli]](n)(Var(EpochMilli.min)) // filled with 0L = EpochMilli.min by default
  //   var nodeIdx = 0
  //   while (nodeIdx < n) {
  //     val authorEdgeIndices: ArraySliceInt = sortedAuthorshipEdgeIdx(nodeIdx)
  //     if (authorEdgeIndices.nonEmpty) {
  //       val (createdEdgeIdx, lastModifierEdgeIdx) = (authorEdgeIndices.head, authorEdgeIndices.last)
  //       nodeCreated(nodeIdx) = edges(createdEdgeIdx).as[Edge.Author].data.timestamp
  //       nodeCreator(nodeIdx) = edgesIdx.b(createdEdgeIdx)
  //       nodeModified(nodeIdx) = edges(lastModifierEdgeIdx).as[Edge.Author].data.timestamp
  //     } else {
  //       nodeCreator(nodeIdx) = -1 //TODO: we do not want -1 indices...
  //     }
  //     nodeIdx += 1
  //   }
  //   (nodeCreated, nodeCreator, nodeModified)
  // }
  // def nodeDeepCreated(nodeIdx:Int):Rx[EpochMilli] = {
  //   var created = nodeCreated(nodeIdx)
  //   dfs.foreach(_(nodeIdx), dfs.withoutStart, childrenIdx, { childIdx =>
  //     val childCreated = nodeCreated(childIdx)
  //     if(childCreated isAfter created) created = childCreated
  //   })
  //   created
  // }

  def update(changes: GraphChanges) = {
    time("graphstate") {
      edgeState.update(changes)
      val layerChanges = nodeState.update(changes)
      children.update(nodeState, layerChanges)
      read.update(nodeState, layerChanges)
    }
  }
}

object NodeState {
  def apply(graphNodes: Array[Node]) = {
    val nodes = mutable.ArrayBuffer.empty[Node]
    val idToIdxHashMap = mutable.HashMap.empty[NodeId, Int]
    idToIdxHashMap.sizeHint(graphNodes.length)

    graphNodes.foreachIndexAndElement { (idx, node) =>
      val nodeId = node.id
      nodes += node
      idToIdxHashMap(nodeId) = idx
    }
    new NodeState(nodes, idToIdxHashMap)
  }
}

class NodeState private (
  val nodesNow: mutable.ArrayBuffer[Node],
  val idToIdxHashMap: mutable.HashMap[NodeId, Int]
) {
  val nodesRx: mutable.ArrayBuffer[Var[Node]] = nodesNow.map(Var(_))

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

class EdgeState private (
  val edgesNow: mutable.ArrayBuffer[Edge],
  val idToIdxHashMap: mutable.HashMap[(NodeId, NodeId), Int]
) {
  val edgesRx: mutable.ArrayBuffer[Var[Edge]] = edgesNow.map(Var(_))
  def update(changes: GraphChanges): Unit = {
    // register new and updated edges

    changes.addEdges.foreachElement { edge =>
      val key = edge.sourceId -> edge.targetId

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

final case class LayerChanges(
  addIdx: Int,
  addEdges: Array[Edge] = Array.empty,
  delEdges: Array[Edge] = Array.empty
)

abstract class LayerState {
  var lookupNow: NestedArrayInt
  val lookupRx: mutable.ArrayBuffer[Var[Array[Int]]] = lookupNow.map(slice => Var(slice.toArray))(breakOut)

  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit]

  def update(nodeState: NodeState, changes: LayerChanges): Unit = {
    val affectedSourceNodes = new mutable.ArrayBuffer[Int]
    val addElemBuilder = new mutable.ArrayBuilder.ofInt
    changes.addEdges.foreach {
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

final class ChildrenLayer(var lookupNow: NestedArrayInt) extends LayerState {
  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit] = {
    case edge: Edge.Child => code(edge.parentId, edge.childId)
    case _                =>
  }
}

final class ReadLayer(var lookupNow: NestedArrayInt) extends LayerState {
  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit] = {
    case edge: Edge.Read => code(edge.nodeId, edge.userId)
    case _               =>
  }
}
