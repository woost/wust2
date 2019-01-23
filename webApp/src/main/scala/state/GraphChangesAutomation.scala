package wust.webApp.state

import java.util

import akka.stream.scaladsl.Source
import flatland.{ArraySet, ArrayStackInt}
import monix.reactive.Observable
import rx.Var
import wust.graph._
import wust.ids.{EdgeData, EpochMilli, NodeId}
import wust.util.macros.InlineList
import wust.util.{StringOps, algorithm}
import wust.webApp.views.UI

import scala.collection.{breakOut, mutable}

// This file is about automation. We want to automate changes to the graph and emit additional changes
// whenever an automation is triggered. Currently an automation can only be triggered by adding a
// parent(sourceId, targetId) where targetId has an automated edge to a template. This template will
// then be applied to the new sourceId.

object GraphChangesAutomation {

  // copy the whole subgraph of the templateNode and append it to newNode.
  // templateNode is a placeholder and we want make changes such newNode looks like a copy of templateNode.
  def copySubGraphOfNode(graph: Graph, newNode: Node, templateNode: Node): GraphChanges = {
    scribe.info(s"Copying sub graph of node $newNode with template $templateNode")

    val templateNodeIdx = graph.idToIdxOrThrow(templateNode.id)
    val newNodeIdx = graph.idToIdxGet(newNode.id)
    val copyTime = EpochMilli.now

    val alreadyExistingNodes = new mutable.HashMap[NodeId, Node]
    val replacedNodes = new mutable.HashMap[NodeId, Node]

    val addNodes = mutable.HashSet.newBuilder[Node]
    val addEdges = mutable.HashSet.newBuilder[Edge]

    replacedNodes += templateNode.id -> newNode

    // If the newNode is already in the graph, then get all descendants. For
    // each descendant check whether it is derived from template node - i.e. the
    // newNode was already automated with this template before. If it was, we
    // do not want to copy again in the automation but reuse the node that was
    // copied in the previous automation run.
    newNodeIdx.foreach(newNodeIdx => graph.descendantsIdx(newNodeIdx).foreach { descendantIdx =>
      val descendant = graph.nodes(descendantIdx)
      graph.derivedFromTemplateEdgeIdx.foreachElement(descendantIdx) { edgeIdx =>
        val interfaceIdx = graph.edgesIdx.b(edgeIdx)
        val interfaceId = graph.nodeIds(interfaceIdx)
        alreadyExistingNodes.update(interfaceId, descendant)
      }
    })

    // Get all descendants of the templateNode. For each descendant, we check
    // whether we already have an existing node implementing this template
    // descendant. If we do, we just keep this node, else we will create a copy
    // of the descendant node. We want to have a copy of each descendant of the
    // template.
    graph.descendantsIdx(templateNodeIdx).foreach { descendantIdx =>
      graph.nodes(descendantIdx) match {
        case node: Node.Content =>
          alreadyExistingNodes.get(node.id) match {
            case Some(implementationNode) =>
              replacedNodes += node.id -> implementationNode
            case None =>
              val copyNode = node.copy(id = NodeId.fresh)
              addNodes += copyNode
              addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), node.id)
              replacedNodes += node.id -> copyNode
          }
        case _ => ()
      }
    }

    // Go through all edges and create new edges pointing to the replacedNodes, so
    // that we copy the edge structure that the template node had.
    graph.edges.foreach {
      case _: Edge.Author                                                 => () // do not copy authors, we want the new authors of the one who triggered this change.
      case _: Edge.DerivedFromTemplate                                    => () // do not copy derived info, we get new derive infos for new nodes
      case edge: Edge.Automated if edge.templateNodeId == templateNode.id => () // do not copy automation edges of template, otherwise the newNode would become a template.
      case edge                                                           =>
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
  def enrich(graph: Graph, viewConfig: Var[ViewConfig], changes: GraphChanges): GraphChanges = {
    scribe.info("Check for automation enrichment of graphchanges: " + changes.toPrettyString(graph))

    val addNodes = mutable.HashSet.newBuilder[Node]
    val addEdges = mutable.HashSet.newBuilder[Edge]
    val delEdges = mutable.HashSet.newBuilder[Edge]

    changes.addEdges.foreach {

      case parent: Edge.Parent if parent.data.deletedAt.isEmpty && !graph.parents(parent.childId).contains(parent.parentId) => // a new, undeleted parent edge
        scribe.info(s"Inspecting parent edge '$parent' for automation")
        val parentIdx = graph.idToIdx(parent.parentId)
        if (parentIdx < 0) addEdges += parent
        else {
          val (childNode, childIsTemplate) = {
            val childIdx = graph.idToIdx(parent.childId)
            if (childIdx < 0) (changes.addNodes.find(_.id == parent.childId).get, changes.addEdges.exists { case Edge.Automated(_, parent.childId) => true; case _ => false })
            else (graph.nodes(childIdx), graph.automatedEdgeReverseIdx.sliceNonEmpty(childIdx))
          }

          if (childIsTemplate) addEdges += parent // do not automate template nodes
          else {
            val automatedEdges = graph.automatedEdgeIdx(parentIdx)
            var doneSomething = false
            //TODO should we do the copy for all templateNodes of this node in one go? because then we do not duplicate shared nodes of templates
            automatedEdges.foreach { automatedEdgeIdx =>
              val templateNodeIdx = graph.edgesIdx.b(automatedEdgeIdx)
              val templateNode = graph.nodes(templateNodeIdx)
              if (templateNode.role == childNode.role) {
                doneSomething = true
                scribe.info(s"Found fitting template '$templateNode' for '$childNode'")
                val changes = copySubGraphOfNode(graph, newNode = childNode, templateNode = templateNode)
                addNodes ++= changes.addNodes
                addEdges ++= changes.addEdges
                delEdges ++= changes.delEdges
              }
            }

            if (doneSomething) {
              UI.toast(StringOps.trimToMaxLength(childNode.str, 100), title = s"New ${ childNode.role } is automated", click = () => viewConfig.update(_.focus(Page(childNode.id))))
            } else {
              addEdges += parent
            }
          }

        }
      case e => addEdges += e

    }

    addNodes ++= changes.addNodes
    delEdges ++= changes.delEdges

    GraphChanges(
      addNodes = addNodes.result(),
      addEdges = addEdges.result(),
      delEdges = delEdges.result()
    )
  }
}
