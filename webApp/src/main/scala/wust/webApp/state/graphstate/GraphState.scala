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

//TODO: idempotence
//TODO: commutativity (e.g. store edges without loaded nodes until the nodes appear)

final class GraphState(initialGraph: Graph) {
  import initialGraph.{ nodes, edges }
  val nodeState = new NodeState
  val edgeState = new EdgeState(nodeState)
  // import nodeState.idToIdxForeach

  // val n = initialGraph.size
  // private val consistentEdges = ArraySet.create(edges.length)
  // val edgesIdx = InterleavedArrayInt.create(edges.length)

  // TODO: have one big triple nested array for all edge lookups?

  // To avoid array builders for each node, we collect the node degrees in a
  // loop and then add the indices in a second loop. This is twice as fast
  // than using one loop with arraybuilders. (A lot less allocations)
  // private val outDegree = new Array[Int](n)
  // private val parentsDegree = new Array[Int](n)
  // private val contentsDegree = new Array[Int](n)
  // private val readDegree = new Array[Int](n)
  // private val childrenDegree = new Array[Int](n)
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

  // private val buildNow = EpochMilli.now

  // edges.foreachIndexAndElement { (edgeIdx, edge) =>
  //   idToIdxForeach(edge.sourceId) { sourceIdx =>
  //     idToIdxForeach(edge.targetId) { targetIdx =>
  //       consistentEdges.add(edgeIdx)
  //       edgesIdx.updatea(edgeIdx, sourceIdx)
  //       edgesIdx.updateb(edgeIdx, targetIdx)
  //       // outDegree(sourceIdx) += 1
  //       // edge match {
  //       //   case e: Edge.Content => contentsDegree(sourceIdx) += 1
  //       //   case _               =>
  //       // }

  //       edge match {
  //         //   case _: Edge.Author =>
  //         //     authorshipDegree(sourceIdx) += 1
  //         //   case _: Edge.Member =>
  //         //     membershipsForNodeDegree(sourceIdx) += 1
  //         case e: Edge.Child =>
  //           //     val childIsMessage = nodes(targetIdx).role == NodeRole.Message
  //           //     val childIsTask = nodes(targetIdx).role == NodeRole.Task
  //           //     val childIsNote = nodes(targetIdx).role == NodeRole.Note
  //           //     val childIsProject = nodes(targetIdx).role == NodeRole.Project
  //           //     val childIsTag = nodes(targetIdx).role == NodeRole.Tag
  //           //     val parentIsTag = nodes(sourceIdx).role == NodeRole.Tag
  //           //     val parentIsStage = nodes(sourceIdx).role == NodeRole.Stage
  //           //     parentsDegree(targetIdx) += 1
  //           childrenDegree(sourceIdx) += 1

  //         //     if (childIsProject) projectChildrenDegree(sourceIdx) += 1
  //         //     if (childIsMessage) messageChildrenDegree(sourceIdx) += 1
  //         //     if (childIsTask) taskChildrenDegree(sourceIdx) += 1
  //         //     if (childIsNote) noteChildrenDegree(sourceIdx) += 1

  //         //     e.data.deletedAt match {
  //         //       case None =>
  //         //         if (childIsTag) tagChildrenDegree(sourceIdx) += 1
  //         //         if (parentIsTag) tagParentsDegree(targetIdx) += 1
  //         //         if (parentIsStage) stageParentsDegree(targetIdx) += 1
  //         //         notDeletedParentsDegree(targetIdx) += 1
  //         //         notDeletedChildrenDegree(sourceIdx) += 1
  //         //       case Some(deletedAt) =>
  //         //         if (deletedAt isAfter buildNow) { // in the future
  //         //           if (childIsTag) tagChildrenDegree(sourceIdx) += 1
  //         //           if (parentIsTag) tagParentsDegree(targetIdx) += 1
  //         //           if (parentIsStage) stageParentsDegree(targetIdx) += 1
  //         //           notDeletedParentsDegree(targetIdx) += 1
  //         //           notDeletedChildrenDegree(sourceIdx) += 1
  //         //         }
  //         //       // TODO everything deleted further in the past should already be filtered in backend
  //         //       // BUT received on request
  //         //     }
  //         //   case _: Edge.Assigned =>
  //         //     assignedNodesDegree(targetIdx) += 1
  //         //     assignedUsersDegree(sourceIdx) += 1
  //         //   case _: Edge.Expanded =>
  //         //     expandedEdgesDegree(sourceIdx) += 1
  //         //   case _: Edge.Notify =>
  //         //     notifyByUserDegree(targetIdx) += 1
  //         //   case _: Edge.Pinned =>
  //         //     pinnedNodeDegree(targetIdx) += 1
  //         //   case _: Edge.Invite =>
  //         //     inviteNodeDegree(targetIdx) += 1
  //         //   case _: Edge.LabeledProperty =>
  //         //     propertiesDegree(sourceIdx) += 1
  //         //     propertiesReverseDegree(targetIdx) += 1
  //         //   case _: Edge.Automated =>
  //         //     automatedDegree(sourceIdx) += 1
  //         //     automatedReverseDegree(targetIdx) += 1
  //         //   case _: Edge.DerivedFromTemplate =>
  //         //     derivedFromTemplateDegree(sourceIdx) += 1
  //         case _: Edge.Read =>
  //           readDegree(sourceIdx) += 1
  //         case _ =>
  //       }
  //     }
  //   }
  // }

  // private val outgoingEdgeIdxBuilder = NestedArrayInt.builder(outDegree)
  // private val parentsIdxBuilder = NestedArrayInt.builder(parentsDegree)
  // private val parentEdgeIdxBuilder = NestedArrayInt.builder(parentsDegree)
  // private val contentsEdgeIdxBuilder = NestedArrayInt.builder(contentsDegree)
  // private val readEdgeIdxBuilder = NestedArrayInt.builder(readDegree)
  // private val childrenIdxBuilder = NestedArrayInt.builder(childrenDegree)
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

  // consistentEdges.foreach { edgeIdx =>
  //   val sourceIdx = edgesIdx.a(edgeIdx)
  //   val targetIdx = edgesIdx.b(edgeIdx)
  //   val edge = edges(edgeIdx)
  //   // outgoingEdgeIdxBuilder.add(sourceIdx, edgeIdx)

  //   // edge match {
  //   //   case e: Edge.Content => contentsEdgeIdxBuilder.add(sourceIdx, edgeIdx)

  //   //   case _               =>
  //   // }

  //   edge match {
  //     // case _: Edge.Author =>
  //     //   authorshipEdgeIdxBuilder.add(sourceIdx, edgeIdx)
  //     //   authorIdxBuilder.add(sourceIdx, targetIdx)
  //     // case _: Edge.Member =>
  //     //   membershipEdgeForNodeIdxBuilder.add(sourceIdx, edgeIdx)
  //     case e: Edge.Child =>
  //       //   val childIsMessage = nodes(targetIdx).role == NodeRole.Message
  //       //   val childIsTask = nodes(targetIdx).role == NodeRole.Task
  //       //   val childIsNote = nodes(targetIdx).role == NodeRole.Note
  //       //   val childIsTag = nodes(targetIdx).role == NodeRole.Tag
  //       //   val childIsProject = nodes(targetIdx).role == NodeRole.Project
  //       //   val parentIsTag = nodes(sourceIdx).role == NodeRole.Tag
  //       //   val parentIsStage = nodes(sourceIdx).role == NodeRole.Stage
  //       //   parentsIdxBuilder.add(targetIdx, sourceIdx)
  //       //   parentEdgeIdxBuilder.add(targetIdx, edgeIdx)
  //       childrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //   childEdgeIdxBuilder.add(sourceIdx, edgeIdx)

  //     //   if (childIsProject) projectChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //   if (childIsMessage) messageChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //   if (childIsTask) taskChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //   if (childIsNote) noteChildrenIdxBuilder.add(sourceIdx, targetIdx)

  //     //   e.data.deletedAt match {
  //     //     case None =>
  //     //       if (childIsTag) tagChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //       if (parentIsTag) tagParentsIdxBuilder.add(targetIdx, sourceIdx)
  //     //       if (parentIsStage) stageParentsIdxBuilder.add(targetIdx, sourceIdx)
  //     //       notDeletedParentsIdxBuilder.add(targetIdx, sourceIdx)
  //     //       notDeletedChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //     case Some(deletedAt) =>
  //     //       if (deletedAt isAfter buildNow) { // in the future
  //     //         if (childIsTag) tagChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //         if (parentIsTag) tagParentsIdxBuilder.add(targetIdx, sourceIdx)
  //     //         if (parentIsStage) stageParentsIdxBuilder.add(targetIdx, sourceIdx)
  //     //         notDeletedParentsIdxBuilder.add(targetIdx, sourceIdx)
  //     //         notDeletedChildrenIdxBuilder.add(sourceIdx, targetIdx)
  //     //       }
  //     //     // TODO everything deleted further in the past should already be filtered in backend
  //     //     // BUT received on request
  //     //   }
  //     // case _: Edge.Expanded =>
  //     //   expandedEdgeIdxBuilder.add(sourceIdx, edgeIdx)
  //     // case _: Edge.Assigned =>
  //     //   assignedNodesIdxBuilder.add(targetIdx, sourceIdx)
  //     //   assignedUsersIdxBuilder.add(sourceIdx, targetIdx)
  //     // case _: Edge.Notify =>
  //     //   notifyByUserIdxBuilder.add(targetIdx, sourceIdx)
  //     // case _: Edge.Pinned =>
  //     //   pinnedNodeIdxBuilder.add(targetIdx, sourceIdx)
  //     // case _: Edge.Invite =>
  //     //   inviteNodeIdxBuilder.add(targetIdx, sourceIdx)
  //     // case _: Edge.LabeledProperty =>
  //     //   propertiesEdgeIdxBuilder.add(sourceIdx, edgeIdx)
  //     //   propertiesEdgeReverseIdxBuilder.add(targetIdx, edgeIdx)
  //     // case _: Edge.Automated =>
  //     //   automatedEdgeIdxBuilder.add(sourceIdx, edgeIdx)
  //     //   automatedEdgeReverseIdxBuilder.add(targetIdx, edgeIdx)
  //     // case _: Edge.DerivedFromTemplate =>
  //     //   derivedFromTemplateEdgeIdxBuilder.add(sourceIdx, edgeIdx)
  //     case _: Edge.Read =>
  //       readEdgeIdxBuilder.add(sourceIdx, edgeIdx)
  //     case _ =>
  //   }
  // }

  // val outgoingEdgeIdx: NestedArrayInt = outgoingEdgeIdxBuilder.result()
  // val parentsIdx: NestedArrayInt = parentsIdxBuilder.result()
  // val parentEdgeIdx: NestedArrayInt = parentEdgeIdxBuilder.result()
  // val readEdgeIdx: NestedArrayIntValues = readEdgeIdxBuilder.result()
  // val childrenIdx: NestedArrayIntValues = childrenIdxBuilder.result()
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

  val children = new ChildrenLayer(edgeState)
  val read = new ReadLayer(edgeState)

  update(GraphChanges(addNodes = initialGraph.nodes, addEdges = initialGraph.edges))

  def update(changes: GraphChanges) = {
    time("graphstate") {
      val layerChanges = nodeState.update(changes)
      edgeState.update(changes)

      children.update(layerChanges)
      read.update(layerChanges)
    }
  }
}

final class ChildrenLayer(val edgeState: EdgeState) extends LayerState {
  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit] = {
    case edge: Edge.Child => code(edge.parentId, edge.childId)
    case _                =>
  }
}

final class ReadLayer(val edgeState: EdgeState) extends LayerState {
  @inline def ifMyEdge(code: (NodeId, NodeId) => Unit): PartialFunction[Edge, Unit] = {
    case edge: Edge.Read => code(edge.nodeId, edge.userId)
    case _                =>
  }
}
