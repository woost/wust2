package wust.graph

import wust.ids._
import wust.util._
import wust.util.algorithm._
import wust.util.collection._
import wust.util.time.time
import wust.util.macros.InlineList

import scala.collection.{ breakOut, mutable }
import scala.collection.immutable
import scala.collection
import flatland._

object Graph {
  val empty = new Graph(Array.empty, Array.empty)
  val doneText: String = "Done"
  val doneTextLower: String = doneText.toLowerCase

  def apply(nodes: Iterable[Node] = Nil, edges: Iterable[Edge] = Nil): Graph = {
    new Graph(nodes.toArray, edges.toArray)
  }

  @inline implicit def graphToGraphLookup(graph: Graph): GraphLookup = graph.lookup
}

//TODO: this is only a case class because julius is too  lazy to write a custom encoder/decoder for boopickle and circe
final case class Graph(nodes: Array[Node], edges: Array[Edge]) {
  scribe.info(s"Creating new graph (nodes = ${nodes.length}, edges = ${edges.length})")

  // because it is a case class, we overwrite equals and hashcode, because we do not want comparisons here.
  override def hashCode(): Int = super.hashCode()
  override def equals(that: Any): Boolean = super.equals(that)

  lazy val lookup = GraphLookup(this, nodes, edges)

  @inline def isEmpty: Boolean = nodes.isEmpty
  @inline def nonEmpty: Boolean = !isEmpty
  @inline def size: Int = nodes.length
  @inline def length: Int = size

  @inline def nodeStr(nodeIdx: Int): String = {
    val node = nodes(nodeIdx)
    s"""[${node.id.shortHumanReadable}]"${node.str}\""""
  }
  @inline def nodeStrDetail(nodeIdx: Int): String = {
    val node = nodes(nodeIdx)
    s"""$nodeIdx ${nodeStr(nodeIdx)}:${node.tpe}/${node.role}"""
  }

  @inline def edgeStr(edgeIdx: Int): String = {
    val edge = edges(edgeIdx)
    val sourceIdx = lookup.edgesIdx.a(edgeIdx)
    val targetIdx = lookup.edgesIdx.b(edgeIdx)
    s"""${nodeStr(sourceIdx)} -${edge.data.toString}-> ${nodeStr(targetIdx)}"""
  }

  def toDetailedString: String = {
    def nodeStr(nodeIdx: Int): String = {
      val node = nodes(nodeIdx)
      s"${nodeStrDetail(nodeIdx)}:${node.meta.accessLevel}  ${node.id.toBase58}  ${node.id.toUuid}"
    }

    s"Graph(\n" +
      s"${nodes.indices.map(nodeStr).mkString("\t", "\n\t", "\n")}\n" +
      s"${edges.indices.map(edgeStr).mkString("\t", "\n\t", "\n")}" +
      ")"
  }

  override def toString: String = {
    s"Graph(nodes: ${nodes.length}, edges: ${edges.length})"
  }

  def debug(node: Node): String = lookup.idToIdxMap(node.id)(idx => debug(idx)).toString
  def debug(nodeId: NodeId): String = lookup.idToIdxMap(nodeId)(debug).toString
  def debug(nodeIds: Iterable[NodeId]): String = nodeIds.map(debug).mkString(", ")
  def debug(nodeIdx: Int): String = nodeStr(nodeIdx)
  def debug(nodesIdx: Seq[Int]): String = nodesIdx.map(debug).mkString(", ")

  def subset(p: Int => Boolean): ArraySet = {
    val set = ArraySet.create(nodes.length)
    nodes.foreachIndex{ i =>
      if (p(i)) set.add(i)
    }
    set
  }

  def applyChangesWithUser(user: Node.User, c: GraphChanges): Graph = {
    val addNodes = if (c.addNodes.exists(_.id == user.id)) c.addNodes else c.addNodes ++ Set(user) // do not add author of change if the node was updated, the author might be outdated.
    changeGraphInternal(addNodes = addNodes, addEdges = c.addEdges, deleteEdges = c.delEdges)
  }
  def applyChanges(c: GraphChanges): Graph = changeGraphInternal(addNodes = c.addNodes, addEdges = c.addEdges, deleteEdges = c.delEdges)

  def replaceNode(oldNodeId: NodeId, newNode: Node): Graph = {
    val newNodes = Array.newBuilder[Node]
    newNodes += newNode
    this.nodes.foreach { n =>
      if (n.id != oldNodeId && n.id != newNode.id) {
        newNodes += n
      }
    }

    val newEdges = this.edges.map { e =>
      if (e.sourceId == oldNodeId && e.targetId == oldNodeId) e.copyId(sourceId = newNode.id, targetId = newNode.id)
      else if (e.sourceId == oldNodeId) e.copyId(sourceId = newNode.id, targetId = e.targetId)
      else if (e.targetId == oldNodeId) e.copyId(sourceId = e.sourceId, targetId = newNode.id)
      else e
    }

    Graph(newNodes.result(), newEdges)
  }

  private def changeGraphInternal(addNodes: collection.Set[Node], addEdges: collection.Set[Edge], deleteEdges: collection.Set[Edge] = Set.empty): Graph = {
    val nodesBuilder = mutable.ArrayBuilder.make[Node]()
    val edgesBuilder = mutable.ArrayBuilder.make[Edge]()
    // nodesBuilder.sizeHint(nodes.length + addNodes.size)
    // edgesBuilder.sizeHint(edges.length + addEdges.size)

    val addNodeIds: Set[NodeId] = addNodes.map(_.id)(breakOut)
    val addEdgeIds: Set[EdgeEquality.Unique] = addEdges.flatMap(EdgeEquality.Unique(_))(breakOut)
    val deleteEdgeIds: Set[EdgeEquality.Unique] = deleteEdges.flatMap(EdgeEquality.Unique(_))(breakOut)
    val updatedEdgeIds = addEdgeIds ++ deleteEdgeIds

    nodes.foreach { node =>
      if (!addNodeIds(node.id)) nodesBuilder += node
    }
    addNodes.foreach { node =>
      nodesBuilder += node
    }
    edges.foreach { edge =>
      val alreadyUpdated = EdgeEquality.Unique(edge).exists(updatedEdgeIds)
      if (!alreadyUpdated) edgesBuilder += edge
    }
    addEdges.foreach { edge =>
      edgesBuilder += edge
    }

    new Graph(
      nodes = nodesBuilder.result(),
      edges = edgesBuilder.result()
    )
  }

  @deprecated("Be aware that you are constructing a new graph here.", "")
  def addNodes(newNodes: Iterable[Node]): Graph = new Graph(nodes = nodes ++ newNodes, edges = edges)
}

final case class RoleStat(role: NodeRole, count: Int, unreadCount: Int)
final case class RoleStats(roles: List[RoleStat]) {
  lazy val mostCommonRole: NodeRole = roles.maxBy(_.count).role
  lazy val active: List[RoleStat] = roles.filter(_.count > 0)
  def contains(role: NodeRole): Boolean = active.exists(_.role == role)
}

final case class GraphLookup(graph: Graph, nodes: Array[Node], edges: Array[Edge]) {
  scribe.info(s"Creating new graph lookup (nodes = $n, edges = $m)")

  @inline private def n = nodes.length
  @inline private def m = edges.length

  def createArraySet(ids: Iterable[NodeId]): ArraySet = {
    val marker = ArraySet.create(n)
    ids.foreach { id =>
      idToIdxForeach(id)(marker.add)
    }
    marker
  }

  def createImmutableBitSet(ids: Iterable[NodeId]): immutable.BitSet = {
    val builder = immutable.BitSet.newBuilder
    ids.foreach { id =>
      idToIdxForeach(id)(builder += _)
    }
    builder.result()
  }

  private val idToIdxMap = mutable.HashMap.empty[NodeId, Int]
  idToIdxMap.sizeHint(n)
  val nodeIds = new Array[NodeId](n)

  nodes.foreachIndexAndElement { (i, node) =>
    val nodeId = node.id
    idToIdxMap(nodeId) = i
    nodeIds(i) = nodeId
  }

  //TODO: measure performance if these inline helper are better than just idToIdxMap.get.fold
  @inline def idToIdxFold[T](id: NodeId)(default: => T)(f: Int => T): T = {
    idToIdxMap.get(id) match {
      case Some(idx) => f(idx)
      case None => default
    }
  }
  @inline def idToIdxForeach[U](id: NodeId)(f: Int => U): Unit = idToIdxFold(id)(())(f(_))
  @inline def idToIdxMap[T](id: NodeId)(f: Int => T): Option[T] = idToIdxFold(id)(Option.empty[T])(idx => Some(f(idx)))
  private def throwIdNotFound(id: NodeId) = throw new Exception(s"Id '${id.toBase58}'/'${id.toUuid}' not found in graph")
  def idToIdxOrThrow(nodeId: NodeId): Int = idToIdxFold[Int](nodeId)(throwIdNotFound(nodeId))(x => x)
  def idToIdx(nodeId: NodeId): Option[Int] = idToIdxFold[Option[Int]](nodeId)(None)(Some(_))
  def nodesByIdOrThrow(nodeId: NodeId): Node = idToIdxFold[Node](nodeId)(throwIdNotFound(nodeId))(idx => nodes(idx))
  def nodesById(nodeId: NodeId): Option[Node] = idToIdxFold[Option[Node]](nodeId)(None)(idx => Some(nodes(idx)))

  @inline def contains(nodeId: NodeId): Boolean = idToIdxFold[Boolean](nodeId)(false)(_ => true)

  assert(idToIdxMap.size == nodes.length, s"nodes are not distinct by id: ${graph.toDetailedString}")

  private val emptyNodeIdSet = Set.empty[NodeId]
  private val consistentEdges = ArraySet.create(edges.length)
  val edgesIdx = InterleavedArrayInt.create(edges.length)

  // TODO: have one big triple nested array for all edge lookups?

  // To avoid array builders for each node, we collect the node degrees in a
  // loop and then add the indices in a second loop. This is twice as fast
  // than using one loop with arraybuilders. (A lot less allocations)
  private val outDegree = new Array[Int](n)
  private val parentsDegree = new Array[Int](n)
  private val contentsDegree = new Array[Int](n)
  private val readDegree = new Array[Int](n)
  private val childrenDegree = new Array[Int](n)
  private val messageChildrenDegree = new Array[Int](n)
  private val taskChildrenDegree = new Array[Int](n)
  private val noteChildrenDegree = new Array[Int](n)
  private val projectChildrenDegree = new Array[Int](n)
  private val tagChildrenDegree = new Array[Int](n)
  private val tagParentsDegree = new Array[Int](n)
  private val stageParentsDegree = new Array[Int](n)
  private val notDeletedParentsDegree = new Array[Int](n)
  private val notDeletedChildrenDegree = new Array[Int](n)
  private val authorshipDegree = new Array[Int](n)
  private val membershipsForNodeDegree = new Array[Int](n)
  private val notifyByUserDegree = new Array[Int](n)
  private val pinnedNodeDegree = new Array[Int](n)
  private val inviteNodeDegree = new Array[Int](n)
  private val expandedEdgesDegree = new Array[Int](n)
  private val assignedNodesDegree = new Array[Int](n)
  private val assignedUsersDegree = new Array[Int](n)
  private val propertiesDegree = new Array[Int](n)
  private val propertiesReverseDegree = new Array[Int](n)
  private val automatedDegree = new Array[Int](n)
  private val automatedReverseDegree = new Array[Int](n)
  private val derivedFromTemplateDegree = new Array[Int](n)
  private val derivedFromTemplateReverseDegree = new Array[Int](n)

  private val buildNow = EpochMilli.now

  edges.foreachIndexAndElement { (edgeIdx, edge) =>
    idToIdxForeach(edge.sourceId) { sourceIdx =>
      idToIdxForeach(edge.targetId) { targetIdx =>
        consistentEdges.add(edgeIdx)
        edgesIdx.updatea(edgeIdx, sourceIdx)
        edgesIdx.updateb(edgeIdx, targetIdx)
        outDegree(sourceIdx) += 1
        edge match {
          case e: Edge.Content => contentsDegree(sourceIdx) += 1
          case _               =>
        }

        edge match {
          case _: Edge.Author =>
            authorshipDegree(sourceIdx) += 1
          case _: Edge.Member =>
            membershipsForNodeDegree(sourceIdx) += 1
          case e: Edge.Child =>
            val childIsMessage = nodes(targetIdx).role == NodeRole.Message
            val childIsTask = nodes(targetIdx).role == NodeRole.Task
            val childIsNote = nodes(targetIdx).role == NodeRole.Note
            val childIsProject = nodes(targetIdx).role == NodeRole.Project
            val childIsTag = nodes(targetIdx).role == NodeRole.Tag
            val parentIsTag = nodes(sourceIdx).role == NodeRole.Tag
            val parentIsStage = nodes(sourceIdx).role == NodeRole.Stage
            parentsDegree(targetIdx) += 1
            childrenDegree(sourceIdx) += 1

            if (childIsProject) projectChildrenDegree(sourceIdx) += 1
            if (childIsMessage) messageChildrenDegree(sourceIdx) += 1
            if (childIsTask) taskChildrenDegree(sourceIdx) += 1
            if (childIsNote) noteChildrenDegree(sourceIdx) += 1

            e.data.deletedAt match {
              case None =>
                if (childIsTag) tagChildrenDegree(sourceIdx) += 1
                if (parentIsTag) tagParentsDegree(targetIdx) += 1
                if (parentIsStage) stageParentsDegree(targetIdx) += 1
                notDeletedParentsDegree(targetIdx) += 1
                notDeletedChildrenDegree(sourceIdx) += 1
              case Some(deletedAt) =>
                if (deletedAt isAfter buildNow) { // in the future
                  if (childIsTag) tagChildrenDegree(sourceIdx) += 1
                  if (parentIsTag) tagParentsDegree(targetIdx) += 1
                  if (parentIsStage) stageParentsDegree(targetIdx) += 1
                  notDeletedParentsDegree(targetIdx) += 1
                  notDeletedChildrenDegree(sourceIdx) += 1
                }
              // TODO everything deleted further in the past should already be filtered in backend
              // BUT received on request
            }
          case _: Edge.Assigned =>
            assignedNodesDegree(targetIdx) += 1
            assignedUsersDegree(sourceIdx) += 1
          case _: Edge.Expanded =>
            expandedEdgesDegree(sourceIdx) += 1
          case _: Edge.Notify =>
            notifyByUserDegree(targetIdx) += 1
          case _: Edge.Pinned =>
            pinnedNodeDegree(targetIdx) += 1
          case _: Edge.Invite =>
            inviteNodeDegree(targetIdx) += 1
          case _: Edge.LabeledProperty =>
            propertiesDegree(sourceIdx) += 1
            propertiesReverseDegree(targetIdx) += 1
          case _: Edge.Automated =>
            automatedDegree(sourceIdx) += 1
            automatedReverseDegree(targetIdx) += 1
          case _: Edge.DerivedFromTemplate =>
            derivedFromTemplateDegree(sourceIdx) += 1
            derivedFromTemplateReverseDegree(targetIdx) += 1
          case _: Edge.Read =>
            readDegree(sourceIdx) += 1
          case _ =>
        }
      }
    }
  }

  private val outgoingEdgeIdxBuilder = NestedArrayInt.builder(outDegree)
  private val parentsIdxBuilder = NestedArrayInt.builder(parentsDegree)
  private val parentEdgeIdxBuilder = NestedArrayInt.builder(parentsDegree)
  private val contentsEdgeIdxBuilder = NestedArrayInt.builder(contentsDegree)
  private val readEdgeIdxBuilder = NestedArrayInt.builder(readDegree)
  private val childrenIdxBuilder = NestedArrayInt.builder(childrenDegree)
  private val childEdgeIdxBuilder = NestedArrayInt.builder(childrenDegree)
  private val messageChildrenIdxBuilder = NestedArrayInt.builder(messageChildrenDegree)
  private val taskChildrenIdxBuilder = NestedArrayInt.builder(taskChildrenDegree)
  private val noteChildrenIdxBuilder = NestedArrayInt.builder(noteChildrenDegree)
  private val projectChildrenIdxBuilder = NestedArrayInt.builder(projectChildrenDegree)
  private val tagChildrenIdxBuilder = NestedArrayInt.builder(tagChildrenDegree)
  private val tagParentsIdxBuilder = NestedArrayInt.builder(tagParentsDegree)
  private val stageParentsIdxBuilder = NestedArrayInt.builder(stageParentsDegree)
  private val notDeletedParentsIdxBuilder = NestedArrayInt.builder(notDeletedParentsDegree)
  private val notDeletedChildrenIdxBuilder = NestedArrayInt.builder(notDeletedChildrenDegree)
  private val authorshipEdgeIdxBuilder = NestedArrayInt.builder(authorshipDegree)
  private val authorIdxBuilder = NestedArrayInt.builder(authorshipDegree)
  private val membershipEdgeForNodeIdxBuilder = NestedArrayInt.builder(membershipsForNodeDegree)
  private val notifyByUserIdxBuilder = NestedArrayInt.builder(notifyByUserDegree)
  private val pinnedNodeIdxBuilder = NestedArrayInt.builder(pinnedNodeDegree)
  private val inviteNodeIdxBuilder = NestedArrayInt.builder(inviteNodeDegree)
  private val expandedEdgeIdxBuilder = NestedArrayInt.builder(expandedEdgesDegree)
  private val assignedNodesIdxBuilder = NestedArrayInt.builder(assignedNodesDegree)
  private val assignedUsersIdxBuilder = NestedArrayInt.builder(assignedUsersDegree)
  private val propertiesEdgeIdxBuilder = NestedArrayInt.builder(propertiesDegree)
  private val propertiesEdgeReverseIdxBuilder = NestedArrayInt.builder(propertiesReverseDegree)
  private val automatedEdgeIdxBuilder = NestedArrayInt.builder(automatedDegree)
  private val automatedEdgeReverseIdxBuilder = NestedArrayInt.builder(automatedReverseDegree)
  private val derivedFromTemplateEdgeIdxBuilder = NestedArrayInt.builder(derivedFromTemplateDegree)
  private val derivedFromTemplateRerverseEdgeIdxBuilder = NestedArrayInt.builder(derivedFromTemplateReverseDegree)

  consistentEdges.foreach { edgeIdx =>
    val sourceIdx = edgesIdx.a(edgeIdx)
    val targetIdx = edgesIdx.b(edgeIdx)
    val edge = edges(edgeIdx)
    outgoingEdgeIdxBuilder.add(sourceIdx, edgeIdx)

    edge match {
      case e: Edge.Content => contentsEdgeIdxBuilder.add(sourceIdx, edgeIdx)

      case _               =>
    }

    edge match {
      case _: Edge.Author =>
        authorshipEdgeIdxBuilder.add(sourceIdx, edgeIdx)
        authorIdxBuilder.add(sourceIdx, targetIdx)
      case _: Edge.Member =>
        membershipEdgeForNodeIdxBuilder.add(sourceIdx, edgeIdx)
      case e: Edge.Child =>
        val childIsMessage = nodes(targetIdx).role == NodeRole.Message
        val childIsTask = nodes(targetIdx).role == NodeRole.Task
        val childIsNote = nodes(targetIdx).role == NodeRole.Note
        val childIsTag = nodes(targetIdx).role == NodeRole.Tag
        val childIsProject = nodes(targetIdx).role == NodeRole.Project
        val parentIsTag = nodes(sourceIdx).role == NodeRole.Tag
        val parentIsStage = nodes(sourceIdx).role == NodeRole.Stage
        parentsIdxBuilder.add(targetIdx, sourceIdx)
        parentEdgeIdxBuilder.add(targetIdx, edgeIdx)
        childrenIdxBuilder.add(sourceIdx, targetIdx)
        childEdgeIdxBuilder.add(sourceIdx, edgeIdx)

        if (childIsProject) projectChildrenIdxBuilder.add(sourceIdx, targetIdx)
        if (childIsMessage) messageChildrenIdxBuilder.add(sourceIdx, targetIdx)
        if (childIsTask) taskChildrenIdxBuilder.add(sourceIdx, targetIdx)
        if (childIsNote) noteChildrenIdxBuilder.add(sourceIdx, targetIdx)

        e.data.deletedAt match {
          case None =>
            if (childIsTag) tagChildrenIdxBuilder.add(sourceIdx, targetIdx)
            if (parentIsTag) tagParentsIdxBuilder.add(targetIdx, sourceIdx)
            if (parentIsStage) stageParentsIdxBuilder.add(targetIdx, sourceIdx)
            notDeletedParentsIdxBuilder.add(targetIdx, sourceIdx)
            notDeletedChildrenIdxBuilder.add(sourceIdx, targetIdx)
          case Some(deletedAt) =>
            if (deletedAt isAfter buildNow) { // in the future
              if (childIsTag) tagChildrenIdxBuilder.add(sourceIdx, targetIdx)
              if (parentIsTag) tagParentsIdxBuilder.add(targetIdx, sourceIdx)
              if (parentIsStage) stageParentsIdxBuilder.add(targetIdx, sourceIdx)
              notDeletedParentsIdxBuilder.add(targetIdx, sourceIdx)
              notDeletedChildrenIdxBuilder.add(sourceIdx, targetIdx)
            }
          // TODO everything deleted further in the past should already be filtered in backend
          // BUT received on request
        }
      case _: Edge.Expanded =>
        expandedEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      case _: Edge.Assigned =>
        assignedNodesIdxBuilder.add(targetIdx, sourceIdx)
        assignedUsersIdxBuilder.add(sourceIdx, targetIdx)
      case _: Edge.Notify =>
        notifyByUserIdxBuilder.add(targetIdx, sourceIdx)
      case _: Edge.Pinned =>
        pinnedNodeIdxBuilder.add(targetIdx, sourceIdx)
      case _: Edge.Invite =>
        inviteNodeIdxBuilder.add(targetIdx, sourceIdx)
      case _: Edge.LabeledProperty =>
        propertiesEdgeIdxBuilder.add(sourceIdx, edgeIdx)
        propertiesEdgeReverseIdxBuilder.add(targetIdx, edgeIdx)
      case _: Edge.Automated =>
        automatedEdgeIdxBuilder.add(sourceIdx, edgeIdx)
        automatedEdgeReverseIdxBuilder.add(targetIdx, edgeIdx)
      case _: Edge.DerivedFromTemplate =>
        derivedFromTemplateEdgeIdxBuilder.add(sourceIdx, edgeIdx)
        derivedFromTemplateRerverseEdgeIdxBuilder.add(targetIdx, edgeIdx)
      case _: Edge.Read =>
        readEdgeIdxBuilder.add(sourceIdx, edgeIdx)
      case _ =>
    }
  }

  val outgoingEdgeIdx: NestedArrayInt = outgoingEdgeIdxBuilder.result()
  val parentsIdx: NestedArrayInt = parentsIdxBuilder.result()
  val parentEdgeIdx: NestedArrayInt = parentEdgeIdxBuilder.result()
  val readEdgeIdx: NestedArrayInt = readEdgeIdxBuilder.result()
  val childrenIdx: NestedArrayInt = childrenIdxBuilder.result()
  val childEdgeIdx: NestedArrayInt = childEdgeIdxBuilder.result()
  val contentsEdgeIdx: NestedArrayInt = contentsEdgeIdxBuilder.result()
  val messageChildrenIdx: NestedArrayInt = messageChildrenIdxBuilder.result()
  val taskChildrenIdx: NestedArrayInt = taskChildrenIdxBuilder.result()
  val noteChildrenIdx: NestedArrayInt = noteChildrenIdxBuilder.result()
  val tagChildrenIdx: NestedArrayInt = tagChildrenIdxBuilder.result()
  val projectChildrenIdx: NestedArrayInt = projectChildrenIdxBuilder.result()
  val tagParentsIdx: NestedArrayInt = tagParentsIdxBuilder.result()
  val stageParentsIdx: NestedArrayInt = stageParentsIdxBuilder.result()
  val notDeletedParentsIdx: NestedArrayInt = notDeletedParentsIdxBuilder.result()
  val notDeletedChildrenIdx: NestedArrayInt = notDeletedChildrenIdxBuilder.result()
  val authorshipEdgeIdx: NestedArrayInt = authorshipEdgeIdxBuilder.result()
  val membershipEdgeForNodeIdx: NestedArrayInt = membershipEdgeForNodeIdxBuilder.result()
  val notifyByUserIdx: NestedArrayInt = notifyByUserIdxBuilder.result()
  val authorsIdx: NestedArrayInt = authorIdxBuilder.result()
  val pinnedNodeIdx: NestedArrayInt = pinnedNodeIdxBuilder.result()
  val inviteNodeIdx: NestedArrayInt = inviteNodeIdxBuilder.result()
  val expandedEdgeIdx: NestedArrayInt = expandedEdgeIdxBuilder.result()
  val assignedNodesIdx: NestedArrayInt = assignedNodesIdxBuilder.result() // user -> node
  val assignedUsersIdx: NestedArrayInt = assignedUsersIdxBuilder.result() // node -> user
  val propertiesEdgeIdx: NestedArrayInt = propertiesEdgeIdxBuilder.result() // node -> property edge
  val propertiesEdgeReverseIdx: NestedArrayInt = propertiesEdgeReverseIdxBuilder.result() // node -> property edge
  val automatedEdgeIdx: NestedArrayInt = automatedEdgeIdxBuilder.result()
  val automatedEdgeReverseIdx: NestedArrayInt = automatedEdgeReverseIdxBuilder.result()
  val derivedFromTemplateEdgeIdx: NestedArrayInt = derivedFromTemplateEdgeIdxBuilder.result()
  val derivedFromTemplateReverseEdgeIdx: NestedArrayInt = derivedFromTemplateRerverseEdgeIdxBuilder.result()

  @inline def isExpanded(userId: UserId, nodeId: NodeId): Option[Boolean] = idToIdx(nodeId).flatMap(isExpanded(userId, _))
  @inline def isExpanded(userId: UserId, nodeIdx: Int): Option[Boolean] = expandedEdgeIdx.collectFirst(nodeIdx) {
    case edgeIdx if edges(edgeIdx).targetId == userId => edges(edgeIdx).as[Edge.Expanded].data.isExpanded
  }

  @inline def parents(nodeId: NodeId): Seq[NodeId] = idToIdxFold(nodeId)(Seq.empty[NodeId])(idx => parentsIdx.map(idx)(nodeIds(_)))
  @inline def children(nodeId: NodeId): Seq[NodeId] = idToIdxFold(nodeId)(Seq.empty[NodeId])(idx => childrenIdx.map(idx)(nodeIds(_)))
  @inline def parentsContains(nodeId: NodeId)(parentId: NodeId): Boolean = idToIdxFold(nodeId)(false) { nodeIdx =>
    idToIdxFold(parentId)(false)(parentIdx => parentsIdx.contains(nodeIdx)(parentIdx))
  }
  @inline def childrenContains(nodeId: NodeId)(childId: NodeId): Boolean = idToIdxFold(nodeId)(false) { nodeIdx =>
    idToIdxFold(childId)(false)(childIdx => childrenIdx.contains(nodeIdx)(childIdx))
  }

  @inline def isPinned(idx: Int, userIdx: Int): Boolean = pinnedNodeIdx.contains(userIdx)(idx)

  def propertyLookup(name: String): NestedArrayInt = {
    val targetDegree = new Array[Int](n)
    val relevantEdges = ArraySet.create(edges.length)

    consistentEdges.foreach { edgeIdx =>
      edges(edgeIdx).data match {
        case EdgeData.LabeledProperty(`name`) =>
          val sourceIdx = edgesIdx.a(edgeIdx)
          targetDegree(sourceIdx) += 1
          relevantEdges.add(edgeIdx)
        case _ =>
      }
    }

    val targetIdxBuilder = NestedArrayInt.builder(targetDegree)
    relevantEdges.foreach { edgeIdx =>
      val sourceIdx = edgesIdx.a(edgeIdx)
      val targetIdx = edgesIdx.b(edgeIdx)
      targetIdxBuilder.add(sourceIdx, targetIdx)
    }
    targetIdxBuilder.result()
  }

  def templateNodes(idx: Int): Seq[Node] = {
    val automatedIdxs = graph.automatedEdgeIdx(idx)
    automatedIdxs.map { automatedIdx =>
      val targetIdx = graph.edgesIdx.b(automatedIdx)
      graph.nodes(targetIdx)
    }
  }
  def automatedNodes(idx: Int): Seq[Node] = {
    val automatedIdxs = graph.automatedEdgeReverseIdx(idx)
    automatedIdxs.map { automatedIdx =>
      val sourceIdx = graph.edgesIdx.a(automatedIdx)
      graph.nodes(sourceIdx)
    }
  }

  val sortedAuthorshipEdgeIdx: NestedArrayInt = NestedArrayInt(authorshipEdgeIdx.map(slice => slice.sortBy(author => edges(author).as[Edge.Author].data.timestamp).toArray)(breakOut) : Array[Array[Int]])

  // not lazy because it often used for sorting. and we do not want to compute a lazy val in a for loop.
  val (nodeCreated: Array[EpochMilli], nodeCreatorIdx: Array[Int], nodeModified: Array[EpochMilli]) = {
    val nodeCreator = new Array[Int](n)
    val nodeCreated = new Array[EpochMilli](n) // filled with 0L = EpochMilli.min by default
    val nodeModified = new Array[EpochMilli](n) // filled with 0L = EpochMilli.min by default
    var nodeIdx = 0
    while (nodeIdx < n) {
      val authorEdgeIndices: ArraySliceInt = sortedAuthorshipEdgeIdx(nodeIdx)
      if (authorEdgeIndices.nonEmpty) {
        val (createdEdgeIdx, lastModifierEdgeIdx) = (authorEdgeIndices.head, authorEdgeIndices.last)
        nodeCreated(nodeIdx) = edges(createdEdgeIdx).as[Edge.Author].data.timestamp
        nodeCreator(nodeIdx) = edgesIdx.b(createdEdgeIdx)
        nodeModified(nodeIdx) = edges(lastModifierEdgeIdx).as[Edge.Author].data.timestamp
      } else {
        nodeCreator(nodeIdx) = -1 //TODO: we do not want -1 indices...
      }
      nodeIdx += 1
    }
    (nodeCreated, nodeCreator, nodeModified)
  }

  def nodeCreator(idx: Int): Option[Node.User] = {
    nodeCreatorIdx(idx) match {
      case -1        => None
      case authorIdx => Option(nodes(authorIdx).as[Node.User])
    }
  }

  def nodeModifier(idx: Int): IndexedSeq[(Node.User, EpochMilli)] = {
    val numAuthors = sortedAuthorshipEdgeIdx(idx).length
    if (numAuthors > 1) {
      sortedAuthorshipEdgeIdx(idx).tail.map{ eIdx =>
        val user = nodes(edgesIdx.b(eIdx)).as[Node.User]
        val time = edges(eIdx).as[Edge.Author].data.timestamp
        (user, time)
      }
    } else IndexedSeq.empty[(Node.User, EpochMilli)]
  }

  def topLevelRoleStats(userId: UserId, parentIds: Iterable[NodeId]): RoleStats = {
    var messageCount = 0
    var taskCount = 0
    var messageUnreadCount = 0
    var taskUnreadCount = 0

    def isRead(childIdx: Int): Boolean = readEdgeIdx.exists(childIdx)(edgeIdx => graph.edges(edgeIdx).targetId == userId)

    parentIds.foreach { nodeId =>
      idToIdxForeach(nodeId) { nodeIdx =>
        childrenIdx.foreachElement(nodeIdx) { childIdx =>
          nodes(childIdx).role match {
            case NodeRole.Message =>
              messageCount += 1
              if (!isRead(childIdx)) messageUnreadCount += 1
            case NodeRole.Task =>
              taskCount += 1
              if (!isRead(childIdx)) taskUnreadCount += 1
            case _ =>
          }
        }
      }
    }
    RoleStats(List(RoleStat(NodeRole.Message, messageCount, messageUnreadCount), RoleStat(NodeRole.Task, taskCount, taskUnreadCount)))
  }

  def filterIdx(p: Int => Boolean): Graph = {
    // we only want to call p once for each node
    // and not trigger the pre-caching machinery of nodeIds
    val (filteredNodesIndices, retained) = nodes.filterIdxToArraySet(p)

    @inline def nothingFiltered = retained == nodes.length

    if (nothingFiltered) graph
    else {
      val filteredNodeIds: Set[NodeId] = filteredNodesIndices.map(nodeIds)(breakOut)
      val filteredNodes: Set[Node] = filteredNodesIndices.map(nodes)(breakOut)
      Graph(
        nodes = filteredNodes,
        edges = edges.filter(e => filteredNodeIds(e.sourceId) && filteredNodeIds(e.targetId))
      )
    }
  }

  def authorsByIndex(idx: Int): Seq[Node.User] = {
    if (idx < 0) Nil
    else authorsIdx(idx).map(idx => nodes(idx).as[Node.User])
  }
  @inline def authors(nodeId: NodeId): Seq[Node.User] = idToIdxFold(nodeId)(Seq.empty[Node.User])(authorsByIndex(_))

  def authorsInByIndex(idx: Int): Seq[Node.User] = {
    if (idx < 0) Nil
    else {
      val rootAuthors = authorsByIndex(idx)
      val builder = new mutable.ArrayBuilder.ofRef[Node.User]
      builder.sizeHint(rootAuthors.size)
      builder ++= rootAuthors
      descendantsIdxForeach(idx) { idx =>
        builder ++= authorsByIndex(idx)
      }
      builder.result().distinct
    }
  }
  @inline def authorsIn(nodeId: NodeId): Seq[Node.User] = idToIdxFold(nodeId)(Seq.empty[Node.User])(authorsInByIndex(_))

  def membersByIndex(idx: Int): Seq[Node.User] = {
    membershipEdgeForNodeIdx(idx).flatMap(edgeIdx => nodesById(edges(edgeIdx).targetId).asInstanceOf[Option[Node.User]])
  }
  @inline def members(nodeId: NodeId): Seq[Node.User] = idToIdxFold(nodeId)(Seq.empty[Node.User])(membersByIndex(_))

  def usersInNode(id: NodeId): collection.Set[Node.User] = idToIdxFold(id)(collection.Set.empty[Node.User]) { nodeIdx =>
    val builder = new mutable.LinkedHashSet[Node.User]
    val members = membersByIndex(nodeIdx)
    builder ++= members
    dfs.withManualAppend(_(nodeIdx), dfs.withStart, childrenIdx, append = { idx =>
      builder ++= authorsByIndex(idx)
    })

    builder.result()
  }

  def latestDeletedAt(subjectIdx: Int): Option[EpochMilli] = {
    parentEdgeIdx(subjectIdx).foldLeft(Option.empty[EpochMilli]) { (result, currentEdgeIdx) =>
      val currentDeletedAt = edges(currentEdgeIdx).as[Edge.Child].data.deletedAt
      (result, currentDeletedAt) match {
        case (None, currentDeletedAt)               => currentDeletedAt
        case (result, None)                         => result
        case (Some(result), Some(currentDeletedAt)) => Some(result newest currentDeletedAt)
      }
    }
  }

  def getRoleParents(nodeId: NodeId, nodeRole: NodeRole): IndexedSeq[NodeId] = idToIdxFold(nodeId)(IndexedSeq.empty[NodeId]) { nodeIdx =>
    parentsIdx(nodeIdx).collect{ case idx if nodes(idx).role == nodeRole => nodeIds(idx) }
  }

  def getRoleParentsIdx(nodeIdx: Int, nodeRole: NodeRole): IndexedSeq[Int] =
    parentsIdx(nodeIdx).collect{ case idx if nodes(idx).role == nodeRole => idx }

  def partiallyDeletedParents(nodeId: NodeId): IndexedSeq[Edge.Child] = idToIdxFold(nodeId)(IndexedSeq.empty[Edge.Child]) { nodeIdx =>
    val now = EpochMilli.now
    graph.parentEdgeIdx(nodeIdx).map(edges).flatMap { e =>
      val parentEdge = e.as[Edge.Child]
      val deleted = parentEdge.data.deletedAt.exists(_ isBefore now)
      if (deleted) Some(parentEdge) else None
    }
  }
  def isPartiallyDeleted(nodeId: NodeId): Boolean = idToIdxFold(nodeId)(false)(nodeIdx => parentEdgeIdx(nodeIdx).map(edges).exists{ e => e.as[Edge.Child].data.deletedAt.fold(false)(_ isBefore buildNow) })

  @inline def isDeletedNowIdx(nodeIdx: Int, parentIdx: Int): Boolean = !notDeletedChildrenIdx.contains(parentIdx)(nodeIdx)
  def isDeletedNowIdx(nodeIdx: Int, parentIndices: Iterable[Int]): Boolean = parentIndices.nonEmpty && parentIndices.forall(parentIdx => isDeletedNowIdx(nodeIdx, parentIdx))
  def isDeletedNow(nodeId: NodeId, parentId: NodeId): Boolean = idToIdxFold(nodeId)(false)(nodeIdx => idToIdxFold(parentId)(false)(parentIdx => isDeletedNowIdx(nodeIdx, parentIdx)))
  def isDeletedNow(nodeId: NodeId, parentIds: Iterable[NodeId]): Boolean = parentIds.nonEmpty && idToIdxFold(nodeId)(false)(nodeIdx => isDeletedNowIdx(nodeIdx, parentIds.flatMap(idToIdx)))
  def isDeletedNowInAllParents(nodeId: NodeId): Boolean = {
    val nodeIdx = idToIdxOrThrow(nodeId)
    val parentIndices = parentsIdx(nodeIdx)
    isDeletedNowIdx(nodeIdx, parentIndices)
  }

  def directNodeTags(nodeIdx: Int): Array[Node] = {
    //      (parents(nodeId).toSet -- (parentIds - nodeId)).map(nodesById) // "- nodeId" reveals self-loops with page-parent

    val tagSet = new mutable.ArrayBuilder.ofRef[Node]

    parentsIdx.foreachElement(nodeIdx) { nodeParentIdx =>
      val node = nodes(nodeParentIdx)
      if (!isDeletedNowIdx(nodeIdx, nodeParentIdx)
        && InlineList.contains(NodeRole.Tag, NodeRole.Stage)(node.role))
        tagSet += node
    }

    tagSet.result()
  }

  def transitiveNodeTags(nodeIdx: Int, parentIndices: immutable.BitSet): Array[Node] = {
    //      val transitivePageParents = parentIds.flatMap(ancestors)
    //      (ancestors(nodeId).toSet -- parentIds -- transitivePageParents -- parents(nodeId))
    //        .map(nodesById)
    val tagSet = ArraySet.create(n)

    ancestorsIdxForeach(nodeIdx)(tagSet.add)
    parentIndices.foreach { parentIdx =>
      tagSet.remove(parentIdx)
      ancestorsIdxForeach(parentIdx)(tagSet.remove)
    }
    parentsIdx.foreachElement(nodeIdx)(tagSet.remove)

    tagSet.mapToArray(nodes)
  }

  lazy val chronologicalNodesAscendingIdx: Array[Int] = {
    Array.range(0, nodes.length).sortBy(nodeCreated)
  }

  lazy val chronologicalNodesAscending: IndexedSeq[Node] = {
    chronologicalNodesAscendingIdx.map(nodes)
  }

  def topologicalSortByIdx[T](seq: Seq[T], extractIdx: T => Int, liftIdx: Int => Option[T]): Seq[T] = {
    if (seq.isEmpty || nodes.isEmpty) return seq

    @inline def idArray: Array[Int] = seq.map(extractIdx)(breakOut)

    idArray.sortBy(nodeCreated).flatMap(i => liftIdx(i))(breakOut)
  }

  lazy val allParentIdsTopologicallySortedByChildren: Array[Int] = {
    val parentSet = ArraySet.create(n)
    edgesIdx.foreachIndexAndTwoElements { (i, sourceIdx, _) =>
      if (edges(i).isInstanceOf[Edge.Child])
        parentSet += sourceIdx
    }
    topologicalSort(parentSet.collectAllElements, childrenIdx)
  }

  def inChildParentRelation(childIdx: Int, possibleParent: Int): Boolean = parentsIdx.contains(childIdx)(possibleParent)
  def inChildParentRelation(child: NodeId, possibleParent: NodeId): Boolean = idToIdxFold(child)(false) { childIdx =>
    idToIdxFold(possibleParent)(false)(parentIdx => inChildParentRelation(childIdx, parentIdx))
  }
  def inDescendantAncestorRelation(descendent: NodeId, possibleAncestor: NodeId): Boolean = idToIdxFold(descendent)(false) { descendantIdx =>
    idToIdxFold(possibleAncestor)(false)(possibleAncestorIdx => ancestorsIdxExists(descendantIdx)(_ == possibleAncestorIdx))
  }

  @inline def hasChildrenIdx(nodeIdx: Int): Boolean = childrenIdx.sliceNonEmpty(nodeIdx)
  @inline def hasParentsIdx(nodeIdx: Int): Boolean = parentsIdx.sliceNonEmpty(nodeIdx)

  @inline def hasNotDeletedChildrenIdx(nodeIdx: Int): Boolean = notDeletedChildrenIdx.sliceNonEmpty(nodeIdx)
  @inline def hasNotDeletedParentsIdx(nodeIdx: Int): Boolean = notDeletedParentsIdx.sliceNonEmpty(nodeIdx)

  @inline private def hasSomethingById(nodeId: NodeId, lookup: Int => Boolean) = idToIdxFold(nodeId)(false)(lookup)
  def hasChildren(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasChildrenIdx)
  def hasParents(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasParentsIdx)

  def hasNotDeletedChildren(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasNotDeletedChildrenIdx)
  def hasNotDeletedParents(nodeId: NodeId): Boolean = hasSomethingById(nodeId, hasNotDeletedParentsIdx)

  def pageFilesIdx(pageIdx: Int): Seq[Int] = {
    val pageFiles = mutable.ArrayBuffer[Int]()
    dfs.withManualAppend(_(pageIdx), dfs.withStart, childrenIdx, { idx =>
      propertiesEdgeIdx.foreachElement(idx) { edgeIdx =>
        val targetIdx = graph.edgesIdx.b(edgeIdx)
        val targetNode = graph.nodes(targetIdx)
        targetNode.data match {
          case data: NodeData.File => pageFiles += targetIdx
          case _ => ()
        }
      }
    })
    pageFiles
  }

  def involvedInContainmentCycleIdx(idx: Int): Boolean = {
    dfs.exists(_(idx), dfs.afterStart, childrenIdx, isFound = _ == idx)
  }
  def involvedInNotDeletedContainmentCycleIdx(idx: Int): Boolean = {
    dfs.exists(_(idx), dfs.afterStart, notDeletedChildrenIdx, isFound = _ == idx)
  }
  def involvedInContainmentCycle(id: NodeId): Boolean = idToIdxFold(id)(false)(involvedInContainmentCycleIdx)

  @inline def descendantsIdxCount(nodeIdx: Int)(f: Int => Boolean): Int = { // inline to inline f
    var count = 0
    dfs.withManualAppend(_(nodeIdx), dfs.afterStart, childrenIdx, append = idx => if (f(idx)) count += 1)
    count
  }
  @inline def descendantsIdxExists(nodeIdx: Int)(f: Int => Boolean) = dfs.exists(_(nodeIdx), dfs.afterStart, childrenIdx, isFound = f) // inline to inline f
  @inline def descendantsIdxForeach(nodeIdx: Int)(f: Int => Unit) = dfs.withManualAppend(_(nodeIdx), dfs.afterStart, childrenIdx, f)
  def descendantsIdx(nodeIdx: Int) = dfs.toArray(_(nodeIdx), dfs.afterStart, childrenIdx)
  def descendants(nodeId: NodeId) = idToIdxFold(nodeId)(Seq.empty[NodeId])(nodeIdx => descendantsIdx(nodeIdx).map(nodeIds))

  @inline def ancestorsIdxCount(nodeIdx: Int)(f: Int => Boolean): Int = { // inline to inline f
    var count = 0
    dfs.withManualAppend(_(nodeIdx), dfs.afterStart, parentsIdx, append = idx => if (f(idx)) count += 1)
    count
  }
  @inline def ancestorsIdxExists(nodeIdx: Int)(f: Int => Boolean) = dfs.exists(_(nodeIdx), dfs.afterStart, parentsIdx, isFound = f) // inline to inline f
  @inline def ancestorsIdxForeach(nodeIdx: Int)(f: Int => Unit) = dfs.withManualAppend(_(nodeIdx), dfs.afterStart, parentsIdx, f)
  def ancestorsIdx(nodeIdx: Int) = dfs.toArray(_(nodeIdx), dfs.afterStart, parentsIdx)
  def ancestors(nodeId: NodeId) = idToIdxFold(nodeId)(Seq.empty[NodeId])(nodeIdx => ancestorsIdx(nodeIdx).map(nodeIds))

  def anyAncestorIsPinned(nodeIds: Iterable[NodeId], userId: NodeId): Boolean = idToIdxFold(userId)(false) { userIdx =>
    def starts(f: Int => Unit): Unit = nodeIds.foreach { nodeId =>
      idToIdxForeach(nodeId)(f)
    }

    val isPinnedSet = {
      val set = ArraySet.create(n)
      pinnedNodeIdx.foreachElement(userIdx)(set.add)
      set
    }

    dfs.exists(starts, dfs.withStart, parentsIdx, isFound = isPinnedSet.contains)
  }

  // IMPORTANT:
  // exactly the same as in the stored procedure
  // when changing things, make sure to change them for the stored procedure as well.
  def can_access_node(userId: UserId, nodeId: NodeId): Boolean = {
    def can_access_node_recursive(
      nodeIdx: Int,
      visited: immutable.BitSet,
    ): Boolean = {
      if (visited(nodeIdx)) return false // prevent inheritance cycles

      // is there a membership?
      val levelFromMembership = membershipEdgeForNodeIdx(nodeIdx).map(edges).collectFirst {
        case Edge.Member(_, EdgeData.Member(level), `userId`) => level
      }
      levelFromMembership match {
        case None => // if no member edge exists
          // read access level directly from node
          nodes(nodeIdx).meta.accessLevel match {
            case NodeAccess.Level(level) => level == AccessLevel.ReadWrite
            case NodeAccess.Inherited =>
              // recursively inherit permissions from parents. minimum one parent needs to allow access.
              parentsIdx.exists(nodeIdx) { parentIdx =>
                can_access_node_recursive(parentIdx, visited + nodeIdx)
              }
          }
        case Some(level) =>
          level == AccessLevel.ReadWrite
      }
    }

    // everybody has full access to non-existent nodes
    idToIdxFold(nodeId)(true)(can_access_node_recursive(_, immutable.BitSet.empty))
  }

  def accessLevelOfNode(nodeId: NodeId): Option[AccessLevel] = {
    def inner(
      nodeIdx: Int,
      visited: immutable.BitSet
    ): Option[AccessLevel] = {
      if (visited(nodeIdx)) return None // prevent inheritance cycles and just disallow

      nodes(nodeIdx).meta.accessLevel match {
        case NodeAccess.Level(level) => Some(level)
        case NodeAccess.Inherited =>
          // recursively inherit permissions from parents. minimum one parent needs to allow access.
          var hasPrivateLevel = false
          parentsIdx.foreachElement(nodeIdx) { parentIdx =>
            inner(parentIdx, visited + nodeIdx) match {
              case Some(AccessLevel.ReadWrite)  => return Some(AccessLevel.ReadWrite) // return outer method, there is at least one public parent
              case Some(AccessLevel.Restricted) => hasPrivateLevel = true
              case None                         => ()
            }
          }
          if (hasPrivateLevel) Some(AccessLevel.Restricted) else None
      }
    }

    // everybody has full access to non-existent nodes
    idToIdxFold[Option[AccessLevel]](nodeId)(None)(inner(_, immutable.BitSet.empty))
  }

  def doneNodeForWorkspace(workspaceIdx: Int): Option[Int] = graph.childrenIdx.find(workspaceIdx) { nodeIdx =>
    val node = nodes(nodeIdx)
    isDoneStage(node)
  }

  def isDoneStage(node: Node): Boolean = node.role == NodeRole.Stage && node.str.trim.toLowerCase == Graph.doneTextLower

  def workspacesForNode(nodeIdx: Int): Array[Int] = {
    (parentsIdx(nodeIdx).flatMap(workspacesForParent)(breakOut): Array[Int]).distinct
  }

  def workspacesForParent(parentIdx: Int): Array[Int] = {
    val parentNode = nodes(parentIdx)
    parentNode.role match {
      case NodeRole.Stage =>
        val workspacesBuilder = new mutable.ArrayBuilder.ofInt
        // search for first transitive parents which are not stages
        dfs.withContinue(_(parentIdx), dfs.afterStart, parentsIdx, { idx =>
          nodes(idx).role match {
            case NodeRole.Stage => true
            case _ =>
              workspacesBuilder += idx
              false
          }
        })
        workspacesBuilder.result()
      case NodeRole.Tag => Array.empty[Int]
      case _ =>
        Array(parentIdx)
    }
  }

  def isDoneInAllWorkspaces(nodeIdx: Int, workspaces: Array[Int]): Boolean = {
    @inline def isDoneIn(doneIdx: Int, nodeIdx: Int) = childrenIdx.contains(doneIdx)(nodeIdx)
    workspaces.forall{ workspaceIdx =>
      doneNodeForWorkspace(workspaceIdx).exists(doneIdx => isDoneIn(doneIdx, nodeIdx))
    }
  }

  def isDone(nodeIdx: Int): Boolean = isDoneInAllWorkspaces(nodeIdx, workspacesForNode(nodeIdx))

  //  lazy val containmentNeighbours
  //  : collection.Map[NodeId, collection.Set[NodeId]] = nodeDefaultNeighbourhood ++ adjacencyList[
  //    NodeId,
  //    Edge
  //    ](containments, _.targetId, _.sourceId)
  // Get connected components by only considering containment edges
  //  lazy val connectedContainmentComponents: List[Set[NodeId]] = {
  //    connectedComponents(nodeIds, containmentNeighbours)
  //  }

  lazy val childDepth: Array[Int] = longestPathsIdx(childrenIdx)
  lazy val parentDepth: Array[Int] = longestPathsIdx(parentsIdx)

  lazy val rootNodes: Array[Int] = {
    // val coveredChildrenx: Set[NodeId] = nodes.filter(n => !hasParents(n.id)).flatMap(n => descendants(n.id))(breakOut)
    val coveredChildren = ArraySet.create(n)
    nodes.foreachIndex { i =>
      if (!hasParentsIdx(i)) {
        coveredChildren ++= descendantsIdx(i)
      }
    }

    // val rootNodes = nodes.filter(n => coveredChildren(idToIdx(n.id)) == 0 && (!hasParents(n.id) || involvedInContainmentCycle(n.id))).toSet
    val rootNodesIdx = new mutable.ArrayBuilder.ofInt
    rootNodesIdx.sizeHint(n)
    nodes.foreachIndex { i =>
      // assert(coveredChildren(i) == coveredChildren(idToIdx(nodes(i).id)))
      if (coveredChildren.containsNot(i) && (!hasParentsIdx(i) || involvedInContainmentCycleIdx(i)))
        rootNodesIdx += i
    }
    rootNodesIdx.result()
  }

  def roleTree(root: Int, role: NodeRole, visited: ArraySet = ArraySet.create(n)): Tree = {
    if (visited.containsNot(root) && nodes(root).role == role) {
      visited.add(root)
      Tree.Parent(nodes(root), (
        childrenIdx(root)
          .collect{
            case idx if nodes(idx).role == role => roleTree(idx, role, visited)
          }(breakOut): List[Tree]
      ).sortBy(_.node.id))
    } else
      Tree.Leaf(nodes(root))
  }

  def parentDepths(node: NodeId): Map[Int, Map[Int, Seq[NodeId]]] = {
    import wust.util.algorithm.dijkstra
    type ResultMap = Map[Distance, Map[GroupIdx, Seq[NodeId]]]

    def ResultMap() = Map[Distance, Map[GroupIdx, Seq[NodeId]]]()

    // NodeId -> distance
    val (distanceMap: Map[NodeId, Int], _) = dijkstra[NodeId](parents, node)
    val nodesInCycles = distanceMap.keys.filter(involvedInContainmentCycle)
    val groupedByCycle = nodesInCycles.groupBy { node => dfs.withStartInCycleDetection[NodeId](node, parents) }
    type GroupIdx = Int
    type Distance = Int
    val distanceMapForCycles: Map[NodeId, (GroupIdx, Distance)] =
      groupedByCycle.zipWithIndex.map {
        case ((group, cycledNodes), groupIdx) =>
          val smallestDistToGroup: Int = group.map(distanceMap).min
          cycledNodes.zip(Stream.continually { (groupIdx, smallestDistToGroup) })
      }.flatten.toMap

    // we want: distance -> (nocycle : Seq[NodeId], cycle1 : Seq[NodeId],...)
    (distanceMap.keySet ++ distanceMapForCycles.keySet).foldLeft(
      ResultMap()
    ) { (result, nodeid) =>
        // in case that the nodeid is inside distanceMapForCycles, it is contained
        // inside a cycle, so we use the smallest distance of the cycle
        val (gId, dist) = if (distanceMapForCycles.contains(nodeid))
          distanceMapForCycles(nodeid)
        else
          (-1, distanceMap(nodeid))

        import monocle.function.At._
        (monocle.Iso.id[ResultMap] composeLens at(dist)).modify { optInnerMap =>
          val innerMap = optInnerMap.getOrElse(Map.empty)
          Some(((monocle.Iso.id[Map[GroupIdx, Seq[NodeId]]] composeLens at(gId)) modify { optInnerSeq =>
            val innerSeq = optInnerSeq.getOrElse(Nil)
            Some(innerSeq ++ Seq(nodeid))
          }) (innerMap))
        }(result)
      }
  }
}

sealed trait Tree {
  def node: Node
  def hasChildren: Boolean
  def flatten: List[Node]
  def flattenWithDepth(depth: Int = 0): List[(Node, Int)]
}
object Tree {
  case class Parent(node: Node, children: List[Tree]) extends Tree {
    override def hasChildren = children.nonEmpty
    override def flatten: List[Node] = node :: (children.flatMap(_.flatten)(breakOut): List[Node])
    override def flattenWithDepth(depth: Int = 0): List[(Node, Int)] = (node, depth) :: (children.flatMap(_.flattenWithDepth(depth + 1))(breakOut): List[(Node, Int)])
  }
  case class Leaf(node: Node) extends Tree {
    override def hasChildren = false
    override def flatten: List[Node] = node :: Nil
    override def flattenWithDepth(depth: Int = 0): List[(Node, Int)] = (node, depth) :: Nil
  }
}
