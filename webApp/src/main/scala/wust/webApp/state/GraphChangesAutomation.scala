package wust.webApp.state

import wust.facades.segment.Segment
import org.scalajs.dom
import rx.Var
import wust.graph._
import wust.ids._
import wust.util.StringOps
import wust.util.algorithm.dfs
import wust.util.collection._
import wust.util.macros.InlineList
import wust.webApp.Client
import wust.webApp.parsers.UrlConfigWriter
import wust.webUtil.UI

import scala.collection.mutable
import scala.util.control.NonFatal

// This file is about automation. We want to automate changes to the graph and emit additional changes
// whenever an automation is triggered. Currently an automation can only be triggered by adding a
// parent(sourceId, targetId) where targetId has an automated edge to a template. This template will
// then be applied to the new sourceId.

private object StringWalker {
  def splitWalk(
    string: String,
    separator: Char
  )(
    walk: String => Boolean
  ): Boolean = {
    val n = string.length
    var i = 0
    var accum = ""

    while (i < n) {
      val current = string(i)
      val isLast = i == n - 1

      if (isLast) accum += current

      if ((isLast || current == separator) && walk(accum)) {
        accum = ""
      } else {
        accum += current
      }

      i += 1
    }

    accum.isEmpty
  }

}

object GraphChangesAutomation {

  val templateVariableRegex = "\\$(@)?\\{woost((\\.[^\\.\\}]+)+)\\}".r
  def replaceVariableInText(userId: UserId, graph: Graph, node: Node, templateText: String, newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]], newEdgesReverse: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): (String, Array[Edge]) = try {

    val extraEdges = Array.newBuilder[Edge]

    sealed trait CommandMode
    case object CommandSelection extends CommandMode
    case object FieldLookup extends CommandMode

    val newStr = templateVariableRegex.replaceAllIn(templateText, { m =>
      val modifier = m.group(1)
      val isMentionMode = modifier == "@"

      val or1Regex = "^or\\(\\s*\"([^\"]*)\"\\s*\\)$".r
      val id1Regex = "^id\\(\\s*\"([^\"]*)\"\\s*\\)$".r
      val join1Regex = "^join\\(\\s*\"([^\"]*)\"\\s*\\)$".r
      val join3Regex = "^join\\(\\s*\"([^\"]*)\",\\s*\"([^\"]*)\",\\s*\"([^\"]*)\"\\s*\\)$".r

      var currentReferenceNodes: Array[Node] = Array(node)
      var commandMode: CommandMode = CommandSelection
      var nodeStringOption = Option.empty[Node => String]
      var joinSeparatorOption = Option.empty[String]
      var nodeStringPrefix = ""
      var nodeStringPostfix = ""
      var defaultValue = Option.empty[String]
      var isFirst = true

      val result = StringWalker.splitWalk(m.group(2).drop(1), separator = '.') { propertyName =>

        val result = commandMode match {

          case CommandSelection => propertyName match {
            case "myself" | "yourself" if isFirst =>
              currentReferenceNodes = graph.nodesById(userId).toArray
              true
            case "id" if nodeStringOption.isEmpty =>
              nodeStringOption = Some(node => node.id.toBase58)
              true
            case "url" if nodeStringOption.isEmpty =>
              nodeStringOption = Some(node => {
                val targetUrlConfig = UrlConfig.default.focus(Page(node.id))
                s"${dom.window.location.origin}${UrlConfigWriter.toString(targetUrlConfig)}"
              })
              true
            case "fileUrl" if nodeStringOption.isEmpty =>
              currentReferenceNodes = currentReferenceNodes.filter(_.data match { case _: NodeData.File => true; case _ => false })
              nodeStringOption = Some(node => s"${Client.wustFilesUrl.getOrElse("")}/${node.data.as[NodeData.File].key}")
              true
            case name if name.startsWith("join(") && joinSeparatorOption.isEmpty =>
              join1Regex.findFirstMatchIn(name) match {
                case Some(m) =>
                  joinSeparatorOption = Some(m.group(1))
                  true
                case None =>
                  join3Regex.findFirstMatchIn(name) match {
                    case Some(m) =>
                      joinSeparatorOption = Some(m.group(1))
                      nodeStringPrefix = m.group(2)
                      nodeStringPostfix = m.group(3)
                      true
                    case None =>
                      false
                  }
              }
            case name if name.startsWith("or(") =>
              or1Regex.findFirstMatchIn(name) match {
                case Some(m) =>
                  if (defaultValue.isEmpty && currentReferenceNodes.isEmpty) {
                    defaultValue = Some(m.group(1))
                  }
                  true
                case None =>
                  false
              }
            case name if name.startsWith("id(") && isFirst =>
              id1Regex.findFirstMatchIn(name) match {
                case Some(m) =>
                  currentReferenceNodes = Cuid.fromBase58String(m.group(1)).toOption.flatMap { cuid =>
                    graph.nodesById(NodeId(cuid))
                  }.toArray
                  true
                case None =>
                  false
              }
            case "original" if nodeStringOption.isEmpty =>
              // do nothing, just make woost.original available
              // TODO: just this really be the actionable node? currently just an alias for the node itself, to enable woost.original
              true
            case "reference" if nodeStringOption.isEmpty =>
              UI.toast("Please use ${woost.original} instead of ${woost.reference}", "Deprecated Variable", level = UI.ToastLevel.Warning)
              true
            case "field" if nodeStringOption.isEmpty =>
              commandMode = FieldLookup
              true
            case "reverseField" if nodeStringOption.isEmpty =>
              currentReferenceNodes = lookupPropertyReverseVariable(graph, currentReferenceNodes, newEdges, newEdgesReverse)
              true
            case "parent" if nodeStringOption.isEmpty =>
              currentReferenceNodes = lookupParentVariable(graph, currentReferenceNodes, newEdges, newEdgesReverse)
              true
            case "child" if nodeStringOption.isEmpty =>
              currentReferenceNodes = lookupChildVariable(graph, currentReferenceNodes, newEdges, newEdgesReverse)
              true
            case "assignee" if nodeStringOption.isEmpty =>
              currentReferenceNodes = lookupAssigneeVariable(graph, currentReferenceNodes, newEdges, newEdgesReverse)
              true
            case _ =>
              false
          }

          case FieldLookup =>
            currentReferenceNodes = lookupPropertyVariable(graph, currentReferenceNodes, propertyName, newEdges, newEdgesReverse)
            commandMode = CommandSelection
            true

        }

        if (isFirst) isFirst = !result
        result
      }

      def syntaxError = "#NAME?"
      def missingValueError = "#REF!"
      if (!result) syntaxError
      else if (currentReferenceNodes.isEmpty) defaultValue.getOrElse(missingValueError)
      else {
        def nodeString(node: Node): String = templateVariableRegex.replaceAllIn(nodeStringOption.fold(node.str)(_(node)), missingValueError)
        val joinSeparator = joinSeparatorOption.getOrElse(", ")
        if (isMentionMode) {
          currentReferenceNodes.foreach { refNode =>
            extraEdges += Edge.Mention(node.id, EdgeData.Mention(InputMention.nodeToMentionsString(refNode)), refNode.id)
          }
          currentReferenceNodes.map(n => nodeStringPrefix + "@" + nodeString(n) + nodeStringPostfix).mkString(joinSeparator)
        } else {
          currentReferenceNodes.map(n => nodeStringPrefix + nodeString(n) + nodeStringPostfix).mkString(joinSeparator)
        }
      }
    })

    (newStr, extraEdges.result)
  } catch { case NonFatal(t) =>
    scribe.info("Failed to replace variables, due to an exception", t)
    (templateText, Array.empty)
  }

  def lookupAssigneeVariable(graph: Graph, nodes: Array[Node], newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]], newEdgesReverse: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(node: Node): Unit = if (InlineList.contains(NodeRole.Task, NodeRole.Message, NodeRole.Project, NodeRole.Note)(node.role)) arr += node

    nodes.foreach { node =>
      newEdges.get(node.id).foreach(_.foreach {
        case (edge: Edge.Assigned, targetNode) => add(targetNode)
        case _ => ()
      })

      graph.idToIdxForeach(node.id) { nodeIdx =>
        graph.assignedUsersIdx.foreachElement(nodeIdx) { nodeIdx =>
          add(graph.nodes(nodeIdx))
        }
      }
    }

    arr.result.distinct
  }

  def lookupChildVariable(graph: Graph, nodes: Array[Node], newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]], newEdgesReverse: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(node: Node): Unit = if (InlineList.contains(NodeRole.Task, NodeRole.Message, NodeRole.Project, NodeRole.Note)(node.role)) arr += node

    nodes.foreach { node =>
      newEdges.get(node.id).foreach(_.foreach {
        case (edge: Edge.Child, childNode) => add(childNode)
        case _ => ()
      })

      graph.idToIdxForeach(node.id) { nodeIdx =>
        graph.childrenIdx.foreachElement(nodeIdx) { nodeIdx =>
          add(graph.nodes(nodeIdx))
        }
      }
    }

    arr.result.distinct
  }

  def lookupParentVariable(graph: Graph, nodes: Array[Node], newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]], newEdgesReverse: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(node: Node): Unit = if (InlineList.contains(NodeRole.Task, NodeRole.Message, NodeRole.Project, NodeRole.Note)(node.role)) arr += node

    nodes.foreach { node =>
      newEdgesReverse.get(node.id).foreach(_.foreach {
        case (edge: Edge.Child, parentNode) => add(parentNode)
        case _ => ()
      })

      graph.idToIdxForeach(node.id) { nodeIdx =>
        graph.parentsIdx.foreachElement(nodeIdx) { nodeIdx =>
          add(graph.nodes(nodeIdx))
        }
      }
    }

    arr.result.distinct
  }

  def lookupPropertyReverseVariable(graph: Graph, nodes: Array[Node], newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]], newEdgesReverse: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(node: Node): Unit = arr += node

    nodes.foreach { node =>
      newEdgesReverse.get(node.id).foreach(_.foreach {
        case (edge: Edge.LabeledProperty, sourceNode) => add(sourceNode)
        case _ => ()
      })

      graph.idToIdxForeach(node.id) { nodeIdx =>
        graph.propertiesEdgeReverseIdx.foreachElement(nodeIdx) { edgeIdx =>
          add(graph.nodes(graph.edgesIdx.a(edgeIdx)))
        }
      }
    }

    arr.result.distinct
  }

  def lookupPropertyVariable(graph: Graph, nodes: Array[Node], key: String, newEdges: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]], newEdgesReverse: mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]): Array[Node] = {
    val arr = Array.newBuilder[Node]

    @inline def add(edge: Edge.LabeledProperty, node: => Node): Unit = if (edge.data.key == key) arr += node

    nodes.foreach { node =>
      newEdges.get(node.id).foreach(_.foreach {
        case (edge: Edge.LabeledProperty, node) => add(edge, node)
        case _ => ()
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
  def copySubGraphOfNode(userId: UserId, graph: Graph, newNode: Node, templateNodeIdxs: Array[Int], ignoreParents: mutable.HashSet[NodeId] = mutable.HashSet.empty, newId: NodeId => NodeId = _ => NodeId.fresh, copyTime: EpochMilli = EpochMilli.now, toastEnabled: Boolean = true, isFullCopy: Boolean = false, overwriteNeutrals: Boolean = true): GraphChanges = {
    copySubGraphOfNodeWithIsTouched(userId, graph, newNode, templateNodeIdxs, ignoreParents, newId, copyTime, toastEnabled, isFullCopy, overwriteNeutrals)._2
  }

  def copySubGraphOfNodeWithIsTouched(userId: UserId, graph: Graph, newNode: Node, templateNodeIdxs: Array[Int], ignoreParents: mutable.HashSet[NodeId] = mutable.HashSet.empty, newId: NodeId => NodeId = _ => NodeId.fresh, copyTime: EpochMilli = EpochMilli.now, toastEnabled: Boolean = true, isFullCopy: Boolean = false, overwriteNeutrals: Boolean = true): (Boolean, GraphChanges) = if (templateNodeIdxs.isEmpty) (false, GraphChanges.empty) else {
    scribe.info(s"Copying sub graph of node $newNode")

    val newNodeIdx = graph.idToIdx(newNode.id)

    // all potentially automated nodes that already exists. The map maps
    // template node ids to existing nodes that are already part of newNode.
    val alreadyExistingNodes = groupByBuilder[NodeId, Node]
    // all potentially automated stages that already exists. The map maps
    // parents node ids and their stage-name to an existing stage that is
    // already part of newNode.
    val alreadyExistingStages = new mutable.HashMap[(NodeId, String), Node]
    // all potentially automated neutral properties that already exist. The map maps
    // parents node ids and their property name to an existing stage that is
    // already part of newNode.
    val alreadyExistingNeutrals = new mutable.HashMap[(NodeId, String), Node]

    def addAlreadyExistingByName(nodeIdx: Int): Unit = {
      val node = graph.nodes(nodeIdx)
      node.role match {
        case NodeRole.Stage => graph.notDeletedParentsIdx.foreachElement(nodeIdx) { parentIdx =>
          alreadyExistingStages += (graph.nodeIds(parentIdx), node.str) -> node
        }
        case NodeRole.Neutral => graph.propertiesEdgeReverseIdx.foreachElement(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
          val nodeWithFieldIdx = graph.edgesIdx.a(edgeIdx)
          val nodeId = graph.nodeIds(nodeWithFieldIdx)
          if (overwriteNeutrals)
            alreadyExistingNeutrals += (nodeId, edge.data.key) -> node
          else
            alreadyExistingNodes += nodeId -> node
        }
        case _ => ()
      }
    }

    def getAlreadyExistingNodes(nodeIdx: Int): Option[Seq[Node]] = {
      val node = graph.nodes(nodeIdx)
      val result = alreadyExistingNodes.result.get(node.id)
      if (result.isDefined) return result

      node.role match {
        case NodeRole.Stage => graph.notDeletedParentsIdx.foreachElement(nodeIdx) { parentIdx =>
          alreadyExistingNodes.result.get(graph.nodeIds(parentIdx)).foreach(_.foreach { existingParentNode =>
            alreadyExistingStages.get(existingParentNode.id -> node.str) match {
              case Some(result) => return Some(Seq(result))
              case None => ()
            }
          })
        }
        case NodeRole.Neutral if overwriteNeutrals => graph.propertiesEdgeReverseIdx.foreachElement(nodeIdx) { edgeIdx =>
          val edge = graph.edges(edgeIdx).as[Edge.LabeledProperty]
          val nodeWithFieldIdx = graph.edgesIdx.a(edgeIdx)
          alreadyExistingNodes.result.get(graph.nodeIds(nodeWithFieldIdx)).foreach(_.foreach { existingNodeWithField =>
              alreadyExistingNeutrals.get(existingNodeWithField.id -> edge.data.key) match {
                case Some(result) => return Some(Seq(result))
                case None => ()
              }
          })
        }
        case _ => ()
      }

      None
    }

    // all nodes within the template node graph, that should be replaced by new
    // nodes. one template can yield multiple nodes. the map maps template node
    // ids to its counterparts in the newNode graphq
    val replacedNodes = groupByBuilder[NodeId, Node]
    // all templates that are referenced via a ReferencesTemplate edge
    val referencedTemplateIds = new mutable.HashSet[NodeId]
    // all nodes that reference a template, reverse set of referencedTemplates
    val referencingNodeIds = new mutable.HashSet[NodeId]
    // all nodes we are adding
    val plannedAddNodes = Array.newBuilder[(Node, Node, NodeData)]
    // all node ids we want to copy (subset of plannedAddNodes._2)
    val copyNodeIds = new mutable.HashSet[NodeId]
    // all edges we are adding
    val addEdges = Array.newBuilder[Edge]

    var newNodeIsTouched = false

    // how to apply a template to an existing node
    def applyTemplateToNode(newNode: Node, templateNode: Node, useTemplateData: Boolean = false): Unit = newNode match {
      // add defined views of template to new node
      case newNode: Node.Content =>
        val newViews = (newNode.views, templateNode.views) match {
          case (Some(currentViews), Some(templateViews)) => Some((currentViews ::: templateViews).distinct)
          case (currentViews, None) => currentViews
          case (None, templateViews) => templateViews
        }
        val newRole = templateNode.role
        val updatedNewNode = newNode.copy(role = newRole, views = newViews)
        val templateData = if (useTemplateData) templateNode.data else updatedNewNode.data
        plannedAddNodes += ((newNode, updatedNewNode, templateData))
      case _ => ()
    }

    // how to create a node from a template
    def copyAndTransformNode(node: Node.Content): Node.Content = {
      val newNodeId = newId(node.id)
      val copyNode = node.copy(id = newNodeId)
      plannedAddNodes += ((copyNode, copyNode, copyNode.data))
      copyNodeIds += copyNode.id
      copyNode
    }

    @inline def manualSuccessorsSize = graph.contentsEdgeIdx.size
    @inline def manualSuccessors(followLinks: Boolean)(nodeIdx: Int, f: Int => Unit): Unit = {
      graph.contentsEdgeIdx.foreachElement(nodeIdx) { edgeIdx =>
        val descendantIdx = graph.edgesIdx.b(edgeIdx)
        graph.nodes(descendantIdx) match {
          case descendant: Node.Content =>
            val shouldContinue = graph.edges(edgeIdx) match {
              case e: Edge.LabeledProperty =>
                // only follow neutral nodes, i.e. properties. Links are normal nodes with a proper node role
                followLinks || descendant.role == NodeRole.Neutral
              case e: Edge.Child =>
                // only follow not-deleted children except for refercing nodes that might want to delete the referenced node.
                e.data.deletedAt.forall(_ isAfter copyTime) || graph.idToIdxFold(e.childId)(false)(graph.referencesTemplateEdgeIdx.sliceNonEmpty(_))
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
    newNodeIdx.foreach(newNodeIdx => dfs.withManualSuccessors(_(newNodeIdx), manualSuccessorsSize, idx => f => manualSuccessors(true)(idx, f), { descendantIdx =>
      val descendant = graph.nodes(descendantIdx).as[Node.Content]
      graph.derivedFromTemplateEdgeIdx.foreachElement(descendantIdx) { edgeIdx =>
        val interfaceIdx = graph.edgesIdx.b(edgeIdx)
        val interfaceId = graph.nodeIds(interfaceIdx)
        alreadyExistingNodes += interfaceId -> descendant
        if (newNode.id == descendant.id) alreadyExistingNodes += newNode.id -> newNode
      }

      addAlreadyExistingByName(descendantIdx)
    }))

    // Get all descendants of the templateNode. For each descendant, we check
    // whether we already have an existing node implementing this template
    // descendant. If we do, we just keep this node, else we will create a copy
    // of the descendant node. We want to have a copy of each descendant of the
    // template. A descendant of a template may reference another template node,
    // that means that it is a placeholder for that referenced node and everything
    // done to it should be done to the reference template node.  we need to do
    // this for all template nodes that were passed in.
    // do the dfs for all template node and copy/update/register all nodes that should be created and may already exists in newNode.
    val unresolvedReferencingIdxs = mutable.ArrayBuffer[Int]()
    def resolveTemplatesSubGraph(startIdxs: (Int => Unit) => Unit) =
      dfs.withManualSuccessorsStopLocally(startIdxs, manualSuccessorsSize, idx => f => manualSuccessors(false)(idx, f), { descendantIdx =>
        val descendant = graph.nodes(descendantIdx).as[Node.Content]
        val descendantReferences = graph.referencesTemplateEdgeIdx(descendantIdx)

        // for each descendant of the template, we need to check whether the node already exists or whether we need to create it.
        // A template can have ReferencesTemplate engines. That means sourceId wants to automated any node derived from targetId.
        if (isFullCopy && !templateNodeIdxs.exists(_ == descendantIdx)) { // full copy is different for all nodes below the templates, just copy
          val copyNode = copyAndTransformNode(descendant)
          alreadyExistingNodes += descendant.id -> copyNode
          replacedNodes += descendant.id -> copyNode
          true
        } else if (descendantReferences.isEmpty) { // if there is no references template
          if (templateNodeIdxs.exists(_ == descendantIdx)) { // if this is actually a templateNode and we have no references
            if (descendant.role == newNode.role) { // without explicit reference, we only apply to the same noderole.
              newNodeIsTouched = true
              referencedTemplateIds += descendant.id
              if (!isFullCopy) addEdges += Edge.DerivedFromTemplate(nodeId = newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
              alreadyExistingNodes += newNode.id -> newNode
              alreadyExistingNodes += descendant.id -> newNode
              replacedNodes += descendant.id -> newNode
              applyTemplateToNode(newNode = newNode, templateNode = descendant)
              true
            } else false
          } else getAlreadyExistingNodes(descendantIdx) match { // this is not a template node, search for already existing node.
            case Some(implementationNodes) => implementationNodes.forall { implementationNode =>
              if (implementationNode.id != descendant.id) {
                if (!isFullCopy || !overwriteNeutrals) addEdges += Edge.DerivedFromTemplate(nodeId = implementationNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
                replacedNodes += descendant.id -> implementationNode
                val useTemplateData = overwriteNeutrals && descendant.role == NodeRole.Neutral // only edit already existing properties with template string (neutral)
                applyTemplateToNode(newNode = implementationNode, templateNode = descendant, useTemplateData = useTemplateData)
                true
              } else false
            }
            case None =>
              val copyNode = copyAndTransformNode(descendant)
              if (!isFullCopy) addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
              alreadyExistingNodes += descendant.id -> copyNode
              replacedNodes += descendant.id -> copyNode
              true
          }
        } else { // we have template references
          val result = descendantReferences forall { edgeIdx =>
            val descendantReferenceEdge = graph.edges(edgeIdx).as[Edge.ReferencesTemplate]
            val descendantReferenceIdx = graph.edgesIdx.b(edgeIdx)
            val descendantReference = graph.nodes(descendantReferenceIdx).as[Node.Content]

            // check if the descendantRefence already has a counterpart in the newNode graph
            getAlreadyExistingNodes(descendantReferenceIdx) match {
              case Some(implementationNodes) =>
                referencedTemplateIds += descendantReference.id

                implementationNodes.forall { implementationNode =>
                  if (implementationNode.id == newNode.id) newNodeIsTouched = true

                  if (implementationNode.id != descendant.id) { // guard against a self-loop, because this somehow happened once and makes no sense. TODO: we should be able to remove this, but needs thorough testing.
                    if (descendantReferenceEdge.data.isCreate) { //create should only be done once, copy onlf it our own template is not defined
                      getAlreadyExistingNodes(descendantIdx) match {
                        case Some(alreadyCopiedDescendants) if alreadyCopiedDescendants.exists(_.id == implementationNode.id) =>
                          if (!isFullCopy) addEdges += Edge.DerivedFromTemplate(nodeId = implementationNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
                          alreadyExistingNodes += descendant.id -> implementationNode
                          replacedNodes += descendant.id -> implementationNode
                          applyTemplateToNode(newNode = implementationNode, templateNode = descendant)
                        case _ =>
                          val copyNode = copyAndTransformNode(implementationNode.as[Node.Content])
                          if (!isFullCopy) addEdges += Edge.DerivedFromTemplate(nodeId = copyNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
                          alreadyExistingNodes += descendant.id -> copyNode
                          replacedNodes += descendant.id -> copyNode
                          applyTemplateToNode(newNode = copyNode, templateNode = descendant, useTemplateData = descendantReferenceEdge.data.isRename)
                      }
                    } else {
                      val canUseTemplateData = getAlreadyExistingNodes(descendantIdx) match {
                        case Some(alreadyReferencedDescendants) => !alreadyReferencedDescendants.exists(_.id == implementationNode.id)
                        case _ => true
                      }
                      if (!isFullCopy) addEdges += Edge.DerivedFromTemplate(nodeId = implementationNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(descendant.id))
                      alreadyExistingNodes += descendant.id -> implementationNode
                      replacedNodes += descendant.id -> implementationNode
                      applyTemplateToNode(newNode = implementationNode, templateNode = descendant, useTemplateData = canUseTemplateData && descendantReferenceEdge.data.isRename)
                    }

                    true
                  } else false
                }
              case None =>
                false
            }
          }

          if (result) referencingNodeIds += descendant.id
          else unresolvedReferencingIdxs += descendantIdx

          result
        }
      })

    resolveTemplatesSubGraph(templateNodeIdxs.foreach)
    // retry once for unresolved references, because of dependencies, TODO: would really need depdencency graph...
    if (unresolvedReferencingIdxs.nonEmpty) resolveTemplatesSubGraph(unresolvedReferencingIdxs.foreach)

    // all new edges that we are creating that might be need for resolving variables in new nodes.
    val newResolvableEdges = new mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]
    val newResolvableEdgesReverse = new mutable.HashMap[NodeId, mutable.ArrayBuffer[(Edge, Node)]]
    def updateNewResolvableEdges(edge: Edge, sourceNode: => Node, targetNode: => Node): Unit = edge match {
      case _: Edge.LabeledProperty | _: Edge.Child | _: Edge.Assigned =>
        newResolvableEdges.getOrElseUpdate(edge.sourceId, new mutable.ArrayBuffer[(Edge, Node)]) += (edge -> targetNode)
        newResolvableEdgesReverse.getOrElseUpdate(edge.targetId, new mutable.ArrayBuffer[(Edge, Node)]) += (edge -> sourceNode)
      case _ => ()
    }

    // Go through all edges and create new edges pointing to the replacedNodes, so
    // that we copy the edge structure that the template node had.
    graph.edges.foreach {
      //TODO: we might need them if we want to copy automations..., need to figure out which ones are from this template and which ones are from the others...
      case _: Edge.DerivedFromTemplate if !isFullCopy                                    => () // do not copy derived info, we get new derive infos for new nodes
      case _: Edge.ReferencesTemplate  if !isFullCopy                                   => () // do not copy reference info, we only want this on the template node

      case _: Edge.Member  if isFullCopy                                   => () // do not copy member if doing full copy

      case edge: Edge.Automated if !isFullCopy && (referencedTemplateIds.contains(edge.templateNodeId) || referencingNodeIds.contains(edge.templateNodeId)) => () // do not copy automation edges of template, otherwise the newNode would become a template.
      case edge: Edge.Child if !isFullCopy && (edge.data.deletedAt.exists(EpochMilli.now.isAfter) && !referencingNodeIds.contains(edge.childId) || templateNodeIdxs.exists(idx => graph.nodeIds(idx) == edge.childId) && ignoreParents(edge.parentId)) => () // do not copy deleted parent edges except when we delete a referenced template, do not copy child edges for ignore parents. This for special cases where we just want to copy the node but not where it is located.
      case edge: Edge.Author            => () // do not copy author edges
      case edge: Edge.Read              => () // do not copy read edges
      case edge                                                           =>

        // replace node ids to point to our copied nodes
        (replacedNodes.result.get(edge.sourceId), replacedNodes.result.get(edge.targetId)) match {
          case (Some(newSources), Some(newTargets)) if newSources.nonEmpty && newTargets.nonEmpty => newSources.foreach { newSource =>
            newTargets.foreach { newTarget =>
              val newEdge = edge.copyId(sourceId = newSource.id, targetId = newTarget.id)
              addEdges += newEdge
              updateNewResolvableEdges(newEdge, newSource, newTarget)
            }
          }
          case (None, Some(newTargets)) if newTargets.nonEmpty => newTargets.foreach { newTarget =>
            val newEdge = edge.copyId(sourceId = edge.sourceId, targetId = newTarget.id)
            addEdges += newEdge
            updateNewResolvableEdges(newEdge, graph.nodesByIdOrThrow(edge.sourceId), newTarget)
          }
          case (Some(newSources), None) if newSources.nonEmpty => newSources.foreach { newSource =>
            val newEdge = edge.copyId(sourceId = newSource.id, targetId = edge.targetId)
            addEdges += newEdge
            updateNewResolvableEdges(newEdge, newSource, graph.nodesByIdOrThrow(edge.targetId))
          }
          case (_, _) => ()
        }
    }

    // track validness of changes
    var isValid = true

    // for all added nodes, we are resolving variables in the content, we can
    // only do so after knowing all new edges
    val addAndEditNodes = Array.newBuilder[Node]
    plannedAddNodes.result().foreach {
      case (oldNode: Node.Content, node: Node.Content, templateData) =>
        val newData = templateData match {

          case data: NodeData.EditableText =>
            val (newStr, extraEdges) = replaceVariableInText(userId, graph, node, data.str, newResolvableEdges, newResolvableEdgesReverse)
            data.updateStr(newStr) match {
              case Some(newData) =>
                addEdges ++= extraEdges
                newData
              case None => data
            }

          case NodeData.RelativeDate(duration) => NodeData.DateTime(DateTimeMilli(copyTime plus duration))

          case data: NodeData.Content => data

          case _ => node.data
        }

        val newNode = node.copy(data = newData)
        if (copyNodeIds.contains(newNode.id) || oldNode != newNode) {
          addAndEditNodes += newNode
        }
      case (_, node, _) =>
        isValid = false
        scribe.warn(s"Invalid node created during automation, we should only create content nodes: $node")
    }

    // sanity check that we do not append things to the templates, we never want this. if this happened, our automation has a bug.
    val allAddEdges = addEdges.result()
    allAddEdges.foreach {
      case edge: Edge.DerivedFromTemplate =>
        if (edge.sourceId == edge.targetId ||
            referencingNodeIds.contains(edge.sourceId) ||
            referencedTemplateIds.contains(edge.sourceId)
        ) {
          scribe.warn(s"Invalid edge created during automation: $edge")
          isValid = false
        }
      case edge =>
        if (edge.sourceId == edge.targetId ||
            referencingNodeIds.contains(edge.sourceId) ||
            referencingNodeIds.contains(edge.targetId) ||
            referencedTemplateIds.contains(edge.sourceId) ||
            referencedTemplateIds.contains(edge.targetId)
        ) {
          scribe.warn(s"Invalid edge created during automation: $edge")
          isValid = false
        }
    }

    if (isValid) {
      // is touched means that the newNode was actually automated. We might call
      // this method with a list of templateNodes that all have an edge
      // ReferencesTemplate. Then newNode was not actually automated, but only a
      // subtree of newNodeq
      (newNodeIsTouched, GraphChanges(addNodes = addAndEditNodes.result, addEdges = allAddEdges).consistent)
    } else {
      scribe.warn("Cannot apply automation, BUG!")
      if (toastEnabled) UI.toast("Sorry, one of your automations could not be run", "Automation Error", level = UI.ToastLevel.Error)
      (false, GraphChanges.empty)
    }
  }

  // Whenever a graphchange is emitted in the frontend, we can enrich the changes here.
  // We get the current graph + the new graph change. For each new parent edge in the graph change,
  // we check if the parent has a template node. If the parent has a template node, we want to
  // append the subgraph (which is spanned from the template node) to the newly inserted child of the parent.
  def enrich(userId: UserId, graph: Graph, viewConfig: Var[UrlConfig], changes: GraphChanges, visitedAutomateParent: Set[NodeId] = Set.empty): GraphChanges = if (GlobalState.automationIsDisabled.now) changes else {
    scribe.info("Check for automation enrichment of graphchanges: " + changes.toPrettyString(graph))

    case class CopyArgs(newNode: Node.Content, templateNodeIdxs: Array[Int], ignoreParents: mutable.HashSet[NodeId], childEdges: Array[Edge.Child]) {
      def merge(args: CopyArgs) = {
        require(args.newNode == newNode, "CopyArgs can only be merged for the same newNode")
        CopyArgs(newNode, (templateNodeIdxs ++ args.templateNodeIdxs).distinct, ignoreParents ++ args.ignoreParents, (childEdges ++ args.childEdges).distinct)
      }
    }

    val groupedAutomatedNodeWithTemplates = new mutable.HashMap[NodeId, mutable.ArrayBuffer[CopyArgs]]
    def updateGroupedAutomatedNodeWithTemplates(args: CopyArgs): Unit = {
      val buffer = groupedAutomatedNodeWithTemplates.getOrElseUpdate(args.newNode.id, new mutable.ArrayBuffer[CopyArgs])
      buffer += args
    }

    val addNodes = mutable.ArrayBuffer[Node]()
    val addEdges = mutable.ArrayBuffer[Edge]()
    val delEdges = mutable.ArrayBuffer[Edge]()

    val automatedNodes = mutable.HashSet[Node]()

    val newAutomateParentChild = new mutable.HashSet[NodeId]

    changes.addEdges.foreach {

      case parent: Edge.Child if parent.data.deletedAt.isEmpty && !visitedAutomateParent.contains(parent.parentId) => // a new, undeleted parent edge
        scribe.info(s"Inspecting parent edge '$parent' for automation")
        graph.idToIdxFold[Unit](parent.parentId)(addEdges += parent) { parentIdx =>
          val (childNode, shouldAutomate) = {
            graph.idToIdxFold(parent.childId) {
              (changes.addNodes.find(_.id == parent.childId).get.as[Node.Content], !changes.addEdges.exists { case Edge.Automated(_, parent.childId) => true; case _ => false })
            } { childIdx =>
              (graph.nodes(childIdx).as[Node.Content], !graph.parentsIdx.contains(childIdx)(parentIdx) && !graph.automatedEdgeReverseIdx.sliceNonEmpty(childIdx))
            }
          }

          if (!shouldAutomate) addEdges += parent // do not automate template nodes
          else {
            newAutomateParentChild += parent.parentId

            // if this is a stage, we want to apply automation to nested stages as well:
            var foundTemplateNode = false
            var forceKeepParent = false
            val ignoreParents = mutable.HashSet[NodeId]()
            val templateNodeIdxs = Array.newBuilder[Int]
            val parentNode = graph.nodes(parentIdx)
            def addTemplateIdx(targetIdx: Int): Unit = graph.automatedEdgeIdx.foreachElement(targetIdx) { automatedEdgeIdx =>
              val templateNodeIdx = graph.edgesIdx.b(automatedEdgeIdx)
              val templateNode = graph.nodes(templateNodeIdx).as[Node.Content]
              def hasReferencedTemplate = graph.referencesTemplateEdgeIdx.sliceNonEmpty(templateNodeIdx)
              if (templateNode.role == childNode.role || hasReferencedTemplate) {
                scribe.info(s"Found fitting template '$templateNode' for '$childNode'")
                templateNodeIdxs += templateNodeIdx
                foundTemplateNode = true
              }
            }

            addTemplateIdx(parentIdx)
            if (parentNode.role == NodeRole.Stage) {
              // special case: if the role of the parent is stage, we need to handle nested stages, so we do a dfs on the parents until we find a non stage.
              // We want to apply automations of nested stages and therefore need to gather all templates from stage parents.
              forceKeepParent = !foundTemplateNode // keep adding this parent edge in case we just automate a parent stage
              dfs.foreachStopLocally(_(parentIdx), dfs.afterStart, graph.parentsIdx, { idx =>
                val node = graph.nodes(idx)
                if (node.role == NodeRole.Stage) {
                  addTemplateIdx(idx)
                  ignoreParents += node.id
                  true
                } else false
              })
            }

            if (foundTemplateNode) {
              if (forceKeepParent) addEdges += parent
              updateGroupedAutomatedNodeWithTemplates(CopyArgs(childNode, templateNodeIdxs.result, ignoreParents, Array(parent)))
            } else addEdges += parent
          }
        }
      case e => addEdges += e

    }

    groupedAutomatedNodeWithTemplates.values.foreach { allArgs =>
      val args = allArgs.reduce(_ merge _)
      val (isTouched, changes) = copySubGraphOfNodeWithIsTouched(userId, graph, newNode = args.newNode, templateNodeIdxs = args.templateNodeIdxs, ignoreParents = args.ignoreParents)
      // if the automated changes re-add the same child edge were are currently replacing, then we want to take the ordering from the new child edge.
      // so an automated node can be drag/dropped to the correct position.
      if (isTouched) {
        automatedNodes += args.newNode
        addEdges ++= changes.addEdges.map {
          case addParent: Edge.Child =>
            args.childEdges.find(edge => addParent.childId == edge.childId && addParent.parentId == edge.parentId).fold(addParent) { edge =>
              addParent.copy(data = addParent.data.copy(ordering = edge.data.ordering))
            }
          case e => e
        }
      } else {
        addEdges ++= changes.addEdges
        addEdges ++= args.childEdges
      }
      addNodes ++= changes.addNodes.filter(node => !addNodes.exists(_.id == node.id)) // not have same node in addNodes twice
      delEdges ++= changes.delEdges
    }

    addNodes ++= changes.addNodes.filter(node => !addNodes.exists(_.id == node.id)) // not have same node in addNodes. TODO: efficient!
    delEdges ++= changes.delEdges

    automatedNodes.foreach { childNode =>
      Segment.trackEvent("TriggeredAutomation")
      UI.toast(
        StringOps.trimToMaxLength(childNode.str, 50),
        title = s"${ childNode.role } was automated:",
        // click = () => viewConfig.update(_.copy(pageChange = PageChange(Page(childNode.id))))
        customIcon = Some("robot")
      )
    }

    val newChanges = GraphChanges.from(addNodes = addNodes, addEdges = addEdges, delEdges = delEdges)
    // recursive if they were automation, we might need to do another one based on the automated changes.
    if (automatedNodes.nonEmpty) enrich(userId, graph.applyChanges(newChanges), viewConfig, newChanges, visitedAutomateParent ++ newAutomateParentChild)
    else newChanges
  }

  def resyncWithTemplates(userId: UserId, graph: Graph, nodeId: NodeId): GraphChanges = graph.idToIdxFold(nodeId)(GraphChanges.empty) { nodeIdx =>
    val templateNodeIdxs = graph.automatedEdgeIdx.map(nodeIdx) { (edgeIdx) =>
      graph.edgesIdx.b(edgeIdx)
    }

    var changes = GraphChanges.empty
    graph.notDeletedChildrenIdx.foreachElement(nodeIdx) { childIdx =>
      if (!templateNodeIdxs.contains(childIdx)) { // not for templates...
        val newNode = graph.nodes(childIdx)
        changes = changes merge copySubGraphOfNode(userId, graph, newNode = newNode, templateNodeIdxs = templateNodeIdxs, overwriteNeutrals = false)
      }
    }

    changes
  }
}
