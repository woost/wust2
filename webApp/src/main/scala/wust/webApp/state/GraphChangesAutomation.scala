package wust.webApp.state

import rx.Var
import wust.webUtil.UI
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.util.algorithm.dfs

import scala.collection.mutable

// This file is about automation. We want to automate changes to the graph and emit additional changes
// whenever an automation is triggered. Currently an automation can only be triggered by adding a
// parent(sourceId, targetId) where targetId has an automated edge to a template. This template will
// then be applied to the new sourceId.

object GraphChangesAutomation {

  // copy the whole subgraph of the templateNode and append it to newNode.
  // templateNode is a placeholder and we want make changes such newNode looks like a copy of templateNode.
  def copySubGraphOfNode(userId: UserId, graph: Graph, newNode: Node, templateNode: Node, ignoreParents: Set[NodeId] = Set.empty, newId: NodeId => NodeId = _ => NodeId.fresh, copyTime: EpochMilli = EpochMilli.now): GraphChanges = {
    scribe.info(s"Copying sub graph of node $newNode with template $templateNode")

    // we expect the template to be in the graph. the newNode may or may not be in the graph
    val templateNodeIdx = graph.idToIdxOrThrow(templateNode.id)
    val newNodeIdx = graph.idToIdx(newNode.id)

    val alreadyExistingNodes = new mutable.HashMap[NodeId, Node]
    val replacedNodes = new mutable.HashMap[NodeId, Node]

    val addNodes = Array.newBuilder[Node]
    val addEdges = Array.newBuilder[Edge]

    newNode match {
      // add defined views of template to new node
      case newNode: Node.Content if templateNode.views.isDefined => addNodes += newNode.copy(views = templateNode.views)
      case _ => ()
    }

    replacedNodes += templateNode.id -> newNode
    alreadyExistingNodes += newNode.id -> newNode
    alreadyExistingNodes += templateNode.id -> newNode

    def copyAndTransformNode(node: Node.Content): Node.Content = {
      // transform certain node data in automation
      val newData = node.data match {
        case NodeData.RelativeDate(duration) => NodeData.DateTime(DateTimeMilli(copyTime plus duration))
        case data => data
      }
      node.copy(id = newId(node.id), data = newData)
    }

    @inline def manualSuccessorsSize = graph.contentsEdgeIdx.size
    @inline def manualSuccessors(nodeIdx: Int, f: Int => Unit): Unit = {
      graph.contentsEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
        val descendantIdx = graph.edgesIdx.b(edgeIdx)
        graph.nodes(descendantIdx) match {
          case descendant: Node.Content =>
            val shouldContinue = graph.edges(edgeIdx) match {
              case e: Edge.LabeledProperty => descendant.role == NodeRole.Neutral // only follow neutral nodes, i.e. properties. Links are normal nodes with a proper node role.
              case e: Edge.Child => e.data.deletedAt.forall(_ isAfter copyTime) // only follow not-deleted children
              case e => true
            }
            if (shouldContinue) f(descendantIdx)
          case _ =>
        }
      }
    }

    // If the newNode is already in the graph, then get all contentedge-descendants.
    // For each descendant check whether it is derived from template node - i.e. the
    // newNode was already automated with this template before. If it was, we
    // do not want to copy again in the automation but reuse the node that was
    // copied in the previous automation run.
    newNodeIdx.foreach(newNodeIdx => dfs.withManualSuccessors(_(newNodeIdx), manualSuccessorsSize, idx => f => manualSuccessors(idx, f), { descendantIdx =>
      val descendant = graph.nodes(descendantIdx).as[Node.Content]
      graph.derivedFromTemplateEdgeIdx.foreachElement(descendantIdx) { edgeIdx =>
        val interfaceIdx = graph.edgesIdx.b(edgeIdx)
        val interfaceId = graph.nodeIds(interfaceIdx)
        alreadyExistingNodes.update(interfaceId, descendant)
      }
    }))

    // Get all descendants of the templateNode. For each descendant, we check
    // whether we already have an existing node implementing this template
    // descendant. If we do, we just keep this node, else we will create a copy
    // of the descendant node. We want to have a copy of each descendant of the
    // template.
    dfs.withManualSuccessors(_(templateNodeIdx), manualSuccessorsSize, idx => f => manualSuccessors(idx, f), { descendantIdx =>
      val descendant = graph.nodes(descendantIdx).as[Node.Content]
      alreadyExistingNodes.get(descendant.id) match {
        case Some(implementationNode) =>
          replacedNodes += descendant.id -> implementationNode
        case None =>
          val copyNode = copyAndTransformNode(descendant)
          addNodes += copyNode
          addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
          replacedNodes += descendant.id -> copyNode
      }
    })

    // Go through all edges and create new edges pointing to the replacedNodes, so
    // that we copy the edge structure that the template node had.
    graph.edges.foreach {
      case _: Edge.DerivedFromTemplate                                    => () // do not copy derived info, we get new derive infos for new nodes
      case edge: Edge.Automated if edge.templateNodeId == templateNode.id => () // do not copy automation edges of template, otherwise the newNode would become a template.
      case edge: Edge.Child if edge.data.deletedAt.exists(EpochMilli.now.isAfter) => () // do not copy deleted parent edges
      case edge: Edge.Author if edge.nodeId == templateNode.id            => () // do not copy author of template itself
      case edge: Edge.Read if edge.nodeId == templateNode.id              => () // do not copy read of template itself
      case edge: Edge.Child if edge.childId == templateNode.id && ignoreParents(edge.parentId) => () // do not copy child edges for ignore parents. This for special cases where we just want to copy the node but not where it is located.
      case edge: Edge.Author                                              => // need to keep date of authorship, but change author. We will have an author edge for every change that was done to this node
        // replace node ids to point to our copied nodes
        if (!alreadyExistingNodes.isDefinedAt(edge.nodeId)) replacedNodes.get(edge.nodeId) match { // already existing nodes are not newly added, therefore do not add a
          case Some(newSource) => addEdges += edge.copy(nodeId = newSource.id, userId = userId)
          case None => ()
        }
      case edge                                                           =>
        // replace node ids to point to our copied nodes
        (replacedNodes.get(edge.sourceId), replacedNodes.get(edge.targetId)) match {
          case (Some(newSource), Some(newTarget)) =>addEdges += edge.copyId(sourceId = newSource.id, targetId = newTarget.id)
          case (None, Some(newTarget)) => addEdges += edge.copyId(sourceId = edge.sourceId, targetId = newTarget.id)
          case (Some(newSource), None) => addEdges += edge.copyId(sourceId = newSource.id, targetId = edge.targetId)
          case (None, None) => ()
        }
    }

    GraphChanges(addNodes = addNodes.result(), addEdges = addEdges.result())
  }

  // Whenever a graphchange is emitted in the frontend, we can enrich the changes here.
  // We get the current graph + the new graph change. For each new parent edge in the graph change,
  // we check if the parent has a template node. If the parent has a template node, we want to
  // append the subgraph (which is spanned from the template node) to the newly inserted child of the parent.
  def enrich(userId: UserId, graph: Graph, viewConfig: Var[UrlConfig], changes: GraphChanges): GraphChanges = {
    scribe.info("Check for automation enrichment of graphchanges: " + changes.toPrettyString(graph))

    val addNodes = mutable.ArrayBuffer[Node]()
    val addEdges = mutable.ArrayBuffer[Edge]()
    val delEdges = mutable.ArrayBuffer[Edge]()

    val automatedNodes = mutable.HashSet[Node]()

    changes.addEdges.foreach {

      case parent: Edge.Child if parent.data.deletedAt.isEmpty => // a new, undeleted parent edge
        scribe.info(s"Inspecting parent edge '$parent' for automation")
        graph.idToIdxFold[Unit](parent.parentId)(addEdges += parent) { parentIdx =>
          val (childNode, shouldAutomate) = {
            graph.idToIdxFold(parent.childId) {
              (changes.addNodes.find(_.id == parent.childId).get, !changes.addEdges.exists { case Edge.Automated(_, parent.childId) => true; case _ => false })
            } { childIdx =>
              (graph.nodes(childIdx), !graph.parentsIdx.contains(childIdx)(parentIdx) && !graph.automatedEdgeReverseIdx.sliceNonEmpty(childIdx))
            }
          }

          if (!shouldAutomate) addEdges += parent // do not automate template nodes
          else {
            var doneSomethingLocally = false
            var doneAutomatedParent = false

            // if this is a stage, we want to apply automation to nested stages as well:
            val parentNode = graph.nodes(parentIdx)
            val targetIdxs: Array[(Int, Set[NodeId])] = if (parentNode.role == NodeRole.Stage) {
              val targetIdxs = Array.newBuilder[(Int, Set[NodeId])]
              targetIdxs += parentIdx -> Set.empty
              dfs.foreachStopLocally(_(parentIdx), dfs.afterStart, graph.parentsIdx, { idx =>
                val node = graph.nodes(idx)
                if (node.role == NodeRole.Stage) {
                  targetIdxs += idx -> Set(node.id)
                  true
                } else false
              })
              targetIdxs.result.reverse
            } else Array(parentIdx -> Set.empty)

            //TODO should we do the copy for all templateNodes of this node in one go? because then we do not duplicate shared nodes of templates
            targetIdxs.foreach { case (targetIdx, ignoreParents) => graph.automatedEdgeIdx.foreachElement(targetIdx) { automatedEdgeIdx =>
              val templateNodeIdx = graph.edgesIdx.b(automatedEdgeIdx)
              val templateNode = graph.nodes(templateNodeIdx)
              if (templateNode.role == childNode.role) {
                scribe.info(s"Found fitting template '$templateNode' for '$childNode'")
                val changes = copySubGraphOfNode(userId, graph, newNode = childNode, templateNode = templateNode, ignoreParents = ignoreParents)
                // if the automated changes re-add the same child edge were are currently replacing, then we want to take the ordering from the new child edge.
                // so an automated node can be drag/dropped to the correct position.
                addEdges ++= changes.addEdges.map {
                  case addParent: Edge.Child if addParent.childId == parent.childId && addParent.parentId == parent.parentId =>
                    addParent.copy(data = addParent.data.copy(ordering = parent.data.ordering))
                  case e => e
                }
                addNodes ++= changes.addNodes.filter(node => !addNodes.exists(_.id == node.id)) // not have same node in addNodes twice
                delEdges ++= changes.delEdges

                doneSomethingLocally = true
                if (targetIdx == parentIdx) doneAutomatedParent = true
              }
            }}

            if (doneSomethingLocally) {
              automatedNodes += childNode
              if (!doneAutomatedParent) {
                addEdges += parent
              }
            } else {
              addEdges += parent
            }
          }

        }
      case e => addEdges += e

    }

    addNodes ++= changes.addNodes.filter(node => !addNodes.exists(_.id == node.id)) // not have same node in addNodes twice
    delEdges ++= changes.delEdges

    automatedNodes.foreach { childNode =>
      UI.toast(
        StringOps.trimToMaxLength(childNode.str, 50),
        title = s"${ childNode.role } was automated:",
        // click = () => viewConfig.update(_.copy(pageChange = PageChange(Page(childNode.id))))
      )
    }

    GraphChanges.from(
      addNodes = addNodes,
      addEdges = addEdges,
      delEdges = delEdges,
    )
  }
}
