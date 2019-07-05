package wust.webApp.state

import rx.Var
import wust.webUtil.UI
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.util.algorithm.dfs
import wust.util.macros.InlineList

import scala.collection.mutable

// This file is about automation. We want to automate changes to the graph and emit additional changes
// whenever an automation is triggered. Currently an automation can only be triggered by adding a
// parent(sourceId, targetId) where targetId has an automated edge to a template. This template will
// then be applied to the new sourceId.

object GraphChangesAutomation {

  val templateVariableRegex = "\\$\\{woost((\\.[^\\.\\}]+)+)\\}".r
  def replaceVariableInText(graph: Graph, node: Node, templateText: String, newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): String = {

    templateVariableRegex.replaceAllIn(templateText, { m =>
      val propertyNames = m.group(1).drop(1).split("\\.")
      val n = propertyNames.length
      var referenceNodesPath: Array[Array[Node]] = new Array[Array[Node]](n + 1)
      referenceNodesPath(0) = Array(node)
      var isCommandMode = true
      var i = 0
      var done = false
      while (!done && i < n) {
        val propertyName = propertyNames(i)
        if (isCommandMode) propertyName match {
          case "field" =>
            referenceNodesPath(i + 1) = referenceNodesPath(i)
            isCommandMode = false
          case "parent" =>
            val references = lookupParentVariable(graph, referenceNodesPath(i), newEdges)
            if (references.isEmpty) done = true
            else referenceNodesPath(i + 1) = references
          case _ =>
            done = true
        } else {
          val references = lookupPropertyVariable(graph, referenceNodesPath(i), propertyName, newEdges)
          if (references.isEmpty) done = true
          else referenceNodesPath(i + 1) = references
          isCommandMode = true
        }

        i += 1
      }

      val lastReferenceNodes = referenceNodesPath(n)
      if (lastReferenceNodes == null || lastReferenceNodes.isEmpty) "#NAME?"
      else lastReferenceNodes.map(_.str).mkString(", ")
    })
  }

  def lookupParentVariable(graph: Graph, nodes: Array[Node], newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(node: Node): Unit = if (InlineList.contains(NodeRole.Task, NodeRole.Message, NodeRole.Project, NodeRole.Note)(node.role)) arr += node

    nodes.foreach { node =>
      newEdges.get(node.id).foreach(_.foreach {
        case (edge: Edge.Child, node) => add(node)
        case (_, _) => ()
      })

      graph.idToIdxForeach(node.id) { nodeIdx =>
        graph.parentsIdx.foreachElement(nodeIdx) { nodeIdx =>
          add(graph.nodes(nodeIdx))
        }
      }
    }

    arr.result.distinct
  }

  def lookupPropertyVariable(graph: Graph, nodes: Array[Node], key: String, newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(edge: Edge.LabeledProperty, node: => Node): Unit = if (edge.data.key == key) arr += node

    nodes.foreach { node =>
      newEdges.get(node.id).foreach(_.foreach {
        case (edge: Edge.LabeledProperty, node) => add(edge, node)
        case (_, _) => ()
      })

      graph.idToIdxForeach(node.id) { nodeIdx =>
        graph.propertiesEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
          add(edge, graph.nodes(graph.edgesIdx.b(edgeIdx)))
        }
      }
    }

    arr.result.distinct
  }

  // copy the whole subgraph of the templateNode and append it to newNode.
  // templateNode is a placeholder and we want make changes such newNode looks like a copy of templateNode.
  def copySubGraphOfNode(userId: UserId, graph: Graph, newNode: Node, templateNode: Node, ignoreParents: Set[NodeId] = Set.empty, newId: NodeId => NodeId = _ => NodeId.fresh, copyTime: EpochMilli = EpochMilli.now): GraphChanges = {
    scribe.info(s"Copying sub graph of node $newNode with template $templateNode")

    // we expect the template to be in the graph. the newNode may or may not be in the graph
    val templateNodeIdx = graph.idToIdxOrThrow(templateNode.id)
    val newNodeIdx = graph.idToIdx(newNode.id)

    val alreadyExistingNodes = new mutable.HashMap[NodeId, Node]
    val replacedNodes = new mutable.HashMap[NodeId, mutable.ArrayBuffer[Node]]
    def updateReplacedNodes(nodeId: NodeId, node: Node): Unit = {
      val buffer = replacedNodes.getOrElseUpdate(nodeId, new mutable.ArrayBuffer[Node])
      buffer += node
    }

    val referencedTemplateIds = new mutable.HashSet[NodeId]
    val referencingNodeIds = new mutable.HashSet[NodeId]
    val addNodeIds = new mutable.HashSet[NodeId]
    val addNodes = Array.newBuilder[Node]
    val addEdges = Array.newBuilder[Edge]

    newNode match {
      // add defined views of template to new node
      case newNode: Node.Content if templateNode.views.isDefined =>
        addNodes += newNode.copy(views = templateNode.views)
        addNodeIds += newNode.id
      case _ => ()
    }

    updateReplacedNodes(templateNode.id, newNode)
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
              case e: Edge.Child => e.data.deletedAt.forall(_ isAfter copyTime) || graph.idToIdxFold(e.childId)(false)(graph.referencesTemplateEdgeIdx.sliceNonEmpty(_)) // only follow not-deleted children except for refercing nodes that might want to delete the referenced node.
              case _ => false
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
        alreadyExistingNodes += interfaceId -> descendant
      }
    }))

    // Get all descendants of the templateNode. For each descendant, we check
    // whether we already have an existing node implementing this template
    // descendant. If we do, we just keep this node, else we will create a copy
    // of the descendant node. We want to have a copy of each descendant of the
    // template. A descendant of a template may reference another template node,
    // that means that it is a placeholder for that referenced node and everything
    // done to it should be done to the reference template node.
    dfs.withManualSuccessors(_(templateNodeIdx), manualSuccessorsSize, idx => f => manualSuccessors(idx, f), { descendantIdx =>
      val descendant = graph.nodes(descendantIdx).as[Node.Content]
      val descendantReferences = graph.referencesTemplateEdgeIdx.map(descendantIdx)(edgeIdx => graph.nodes(graph.edgesIdx.b(edgeIdx)).as[Node.Content])

      if (descendantReferences.isEmpty) alreadyExistingNodes.get(descendant.id) match {
        case Some(implementationNode) =>
          if (implementationNode.id != descendant.id) {
            addEdges += Edge.DerivedFromTemplate(nodeId = implementationNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
            updateReplacedNodes(descendant.id, implementationNode)
          }
        case None =>
          val copyNode = copyAndTransformNode(descendant)
          addNodes += copyNode
          addNodeIds += copyNode.id
          addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
          updateReplacedNodes(descendant.id, copyNode)
      } else {
        referencingNodeIds += descendant.id
        descendantReferences foreach { descendantReference =>
          referencedTemplateIds += descendantReference.id
          alreadyExistingNodes.get(descendantReference.id) match {
            case Some(implementationNode) =>
              if (implementationNode.id != descendant.id) {
                addEdges += Edge.DerivedFromTemplate(nodeId = implementationNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
                updateReplacedNodes(descendant.id, implementationNode)
              }
            case None =>
              val copyNode = copyAndTransformNode(descendantReference)
              addNodes += copyNode
              addNodeIds += copyNode.id
              addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendantReference.id))
              addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
              updateReplacedNodes(descendantReference.id, copyNode)
              updateReplacedNodes(descendant.id, copyNode)
          }
        }
      }
    })

    val newReplacableEdges = new mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]
    def updateNewReplacableEdges(edge: Edge, sourceNode: => Node, targetNode: => Node): Unit = edge match {
      case edge: Edge.LabeledProperty =>
        val buffer = newReplacableEdges.getOrElseUpdate(edge.sourceId, new mutable.ArrayBuffer[(Edge, Node)])
        buffer += (edge -> targetNode)
      case edge: Edge.Child =>
        val buffer = newReplacableEdges.getOrElseUpdate(edge.targetId, new mutable.ArrayBuffer[(Edge, Node)])
        buffer += (edge -> sourceNode)
      case _ => ()
    }

    // Go through all edges and create new edges pointing to the replacedNodes, so
    // that we copy the edge structure that the template node had.
    graph.edges.foreach {
      case _: Edge.DerivedFromTemplate                                    => () // do not copy derived info, we get new derive infos for new nodes
      case _: Edge.ReferencesTemplate                                     => () // do not copy reference info, we only want this on the template node
      case edge: Edge.Automated if edge.templateNodeId == templateNode.id => () // do not copy automation edges of template, otherwise the newNode would become a template.
      case edge: Edge.Child if edge.data.deletedAt.exists(EpochMilli.now.isAfter) && !referencingNodeIds.contains(edge.childId) || referencedTemplateIds.contains(edge.childId) || templateNode.id == edge.childId && ignoreParents(edge.parentId) => () // do not copy deleted parent edges except when we delete a referenced template, do not copy child edges from template references, do not copy child edges for ignore parents. This for special cases where we just want to copy the node but not where it is located.
      case edge: Edge.Author if edge.nodeId == templateNode.id            => () // do not copy author of template itself
      case edge: Edge.Read if edge.nodeId == templateNode.id              => () // do not copy read of template itself
      case edge: Edge.Author                                              => // need to keep date of authorship, but change author. We will have an author edge for every change that was done to this node
        // replace node ids to point to our copied nodes
        replacedNodes.get(edge.nodeId).foreach(_ foreach { // already existing nodes are not newly added, therefore do not add a
          case newSource if addNodeIds.contains(newSource.id) => addEdges += edge.copy(nodeId = newSource.id, userId = userId)
          case _ => ()
        })
      case edge                                                           =>

        // replace node ids to point to our copied nodes
        (replacedNodes.get(edge.sourceId), replacedNodes.get(edge.targetId)) match {
          case (Some(newSources), Some(newTargets)) if newSources.nonEmpty && newTargets.nonEmpty => newSources.foreach { newSource =>
            newTargets.foreach { newTarget =>
              val newEdge = edge.copyId(sourceId = newSource.id, targetId = newTarget.id)
              addEdges += newEdge
              updateNewReplacableEdges(newEdge, newSource, newTarget)
            }
          }
          case (None, Some(newTargets)) if newTargets.nonEmpty => newTargets.foreach { newTarget =>
            val newEdge = edge.copyId(sourceId = edge.sourceId, targetId = newTarget.id)
            addEdges += newEdge
            updateNewReplacableEdges(newEdge, graph.nodesByIdOrThrow(edge.sourceId), newTarget)
          }
          case (Some(newSources), None) if newSources.nonEmpty => newSources.foreach { newSource =>
            val newEdge = edge.copyId(sourceId = newSource.id, targetId = edge.targetId)
            addEdges += newEdge
            updateNewReplacableEdges(newEdge, newSource, graph.nodesByIdOrThrow(edge.targetId))
          }
          case (_, _) => ()
        }
    }

    val addAndEditNodes: Array[Node] = addNodes.result().map {
      case node: Node.Content => node.data match {
        case data: NodeData.EditableText =>
          val newStr = replaceVariableInText(graph, node, data.str, newReplacableEdges)
          data.updateStr(newStr).fold(node)(data => node.copy(data = data))
        case _ => node
      }
      case node => node
    }

    GraphChanges(addNodes = addAndEditNodes, addEdges = addEdges.result()).consistent
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

  def resyncWithTemplates(userId: UserId, graph: Graph, nodeId: NodeId): GraphChanges = graph.idToIdxFold(nodeId)(GraphChanges.empty) { nodeIdx =>
    val node = graph.nodes(nodeIdx)
    graph.derivedFromTemplateEdgeIdx.foldLeft(nodeIdx)(GraphChanges.empty) { (changes, edgeIdx) =>
      val templateIdx = graph.edgesIdx.b(edgeIdx)
      val templateNode = graph.nodes(templateIdx)
      changes merge copySubGraphOfNode(userId, graph, newNode = node, templateNode = templateNode)
    }
  }
}
