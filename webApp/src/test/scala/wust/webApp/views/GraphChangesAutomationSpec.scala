package wust.webApp.views

import org.scalatest._
import wust.graph._
import wust.ids._
import wust.webApp.state.GraphChangesAutomation


class GraphChangesAutomationSpec extends FreeSpec with MustMatchers {
  def randomPositiveInt() = {
    val r = scala.util.Random.nextInt
    if (r < 0) r * -1 else r
  }
  def freshNodeId() = NodeId(Cuid(0, randomPositiveInt()))
  def copyNodeId(nodeId: NodeId) = NodeId(Cuid(nodeId.right, nodeId.left))
  def copyNode(node: Node.Content) = node.copy(id = copyNodeId(node.id))
  def newNodeContent(str: String, role: NodeRole) = Node.Content(freshNodeId(), NodeData.Markdown(str), role)
  def newNodeUser(str: String) = Node.User(UserId(freshNodeId()), NodeData.User(str, false, 0), NodeMeta.User)
  val copyTime = EpochMilli.now

  val defaultChildData = EdgeData.Child(deletedAt = None, ordering = BigDecimal(0))

  def copySubGraphOfNode(graph: Graph, newNode: Node.Content, templateNode: Node.Content) = GraphChangesAutomation.copySubGraphOfNode(
    UserId(freshNodeId()), graph, newNode, Array(graph.idToIdxOrThrow(templateNode.id)), newId = copyNodeId(_), copyTime = copyTime
  )

  "empty template node" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        newNode, templateNode
      ),

      edges = Array.empty
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)
    changes.addEdges must contain theSameElementsAs Array(
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
    changes.delEdges mustEqual Array.empty
    changes.addNodes mustEqual Array.empty
  }

  "template node with view" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateViews = List(View.Chat, View.Kanban)
    val templateNode = newNodeContent("template", NodeRole.Task).copy(views = Some(templateViews))
    val graph = Graph(
      nodes = Array(
        newNode, templateNode
      ),

      edges = Array.empty
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)
    changes.addEdges must contain theSameElementsAs Array(
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
    changes.delEdges mustEqual Array.empty
    changes.addNodes must contain theSameElementsAs Array(
      newNode.copy(views = Some(templateViews))
    )
  }

  "empty template node with some graph" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val otherNode = newNodeContent("other", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        newNode, templateNode, otherNode,
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(otherNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)
    changes.addEdges must contain theSameElementsAs Array(
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
    changes.delEdges mustEqual Array.empty
    changes.addNodes mustEqual Array.empty
  }

  "self-looping" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        newNode, templateNode
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(newNode.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(templateNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes mustEqual Array.empty
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(newNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "template loop" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node = newNodeContent("node", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        newNode, templateNode, node
      ),

      edges = Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node.id)),
        Edge.Child(ParentId(node.id), defaultChildData, ChildId(templateNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(copyNodeId(node.id)), defaultChildData, ChildId(newNode.id)),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node.id))),
      Edge.DerivedFromTemplate(copyNodeId(node.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "template node inside new node" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(templateNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes mustEqual Array.empty
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(newNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "new node inside template" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode
      ),

      edges = Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(newNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes mustEqual Array.empty
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(newNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "user inside template" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val userNode = newNodeUser("user")
    val otherNode = newNodeContent("other", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, userNode, otherNode
      ),

      edges = Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(userNode.id: NodeId)),
        Edge.Child(ParentId(userNode.id: NodeId), defaultChildData, ChildId(otherNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes mustEqual Array.empty
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(userNode.id: NodeId)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "node inside template" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node = newNodeContent("node", NodeRole.Task)
    val otherNode = newNodeContent("other", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node, otherNode
      ),

      edges = Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node.id)),
        Edge.Child(ParentId(node.id), defaultChildData, ChildId(otherNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node), copyNode(otherNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node.id))),
      Edge.Child(ParentId(copyNodeId(node.id)), defaultChildData, ChildId(copyNodeId(otherNode.id))),
      Edge.DerivedFromTemplate(copyNodeId(node.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node.id)),
      Edge.DerivedFromTemplate(copyNodeId(otherNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(otherNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "reference node inside template" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node1 = newNodeContent("node1", NodeRole.Task)
    val node2 = newNodeContent("node2", NodeRole.Task)
    val childNode1 = newNodeContent("child1", NodeRole.Task)
    val childNode2 = newNodeContent("child2", NodeRole.Task)
    val templateNode1 = newNodeContent("template-node1", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node1, node2, childNode1, childNode2, templateNode1
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node2.id)),
        Edge.Child(ParentId(node1.id), defaultChildData, ChildId(childNode1.id)),
        Edge.Child(ParentId(node2.id), defaultChildData, ChildId(childNode2.id)),
        Edge.DerivedFromTemplate(node1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode1.id)),
        Edge.ReferencesTemplate(node2.id, TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(childNode2)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(node1.id), defaultChildData, ChildId(copyNodeId(childNode2.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
      Edge.DerivedFromTemplate(node1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(copyNodeId(childNode2.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(childNode2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "reference node at top level" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node1 = newNodeContent("node1", NodeRole.Task)
    val node2 = newNodeContent("node2", NodeRole.Task)
    val childNode1 = newNodeContent("child1", NodeRole.Task)
    val childNode2 = newNodeContent("child2", NodeRole.Task)
    val templateNode0 = newNodeContent("template-node0", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node1, node2, childNode1, childNode2, templateNode0
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node2.id)),
        Edge.Child(ParentId(node1.id), defaultChildData, ChildId(childNode1.id)),
        Edge.Child(ParentId(node2.id), defaultChildData, ChildId(childNode2.id)),
        Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode0.id)),
        Edge.ReferencesTemplate(templateNode.id, TemplateId(templateNode0.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node2), copyNode(childNode2)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(copyNodeId(node2.id)), defaultChildData, ChildId(copyNodeId(childNode2.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node2.id))),
      Edge.DerivedFromTemplate(copyNodeId(node2.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(copyNodeId(childNode2.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(childNode2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "properties of template" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val linkedNode = newNodeContent("linked", NodeRole.Task)
    val neutralNode = newNodeContent("neutral", NodeRole.Neutral)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, linkedNode, neutralNode
      ),

      edges = Array(
        Edge.LabeledProperty(templateNode.id, EdgeData.LabeledProperty("link"), PropertyId(linkedNode.id)),
        Edge.LabeledProperty(templateNode.id, EdgeData.LabeledProperty("copy"), PropertyId(neutralNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(neutralNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.LabeledProperty(newNode.id, EdgeData.LabeledProperty("link"), PropertyId(linkedNode.id)),
      Edge.LabeledProperty(newNode.id, EdgeData.LabeledProperty("copy"), PropertyId(copyNodeId(neutralNode.id))),
      Edge.DerivedFromTemplate(copyNodeId(neutralNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(neutralNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "properties of template child" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node = newNodeContent("node", NodeRole.Task)
    val linkedNode = newNodeContent("linked", NodeRole.Task)
    val neutralNode = newNodeContent("neutral", NodeRole.Neutral)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node, linkedNode, neutralNode
      ),

      edges = Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node.id)),
        Edge.LabeledProperty(node.id, EdgeData.LabeledProperty("link"), PropertyId(linkedNode.id)),
        Edge.LabeledProperty(node.id, EdgeData.LabeledProperty("copy"), PropertyId(neutralNode.id)),
        Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node), copyNode(neutralNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node.id))),
      Edge.LabeledProperty(copyNodeId(node.id), EdgeData.LabeledProperty("link"), PropertyId(linkedNode.id)),
      Edge.LabeledProperty(copyNodeId(node.id), EdgeData.LabeledProperty("copy"), PropertyId(copyNodeId(neutralNode.id))),
      Edge.DerivedFromTemplate(copyNodeId(node.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node.id)),
      Edge.DerivedFromTemplate(copyNodeId(neutralNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(neutralNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "parents of template" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node = newNodeContent("node", NodeRole.Task)
    val otherNode = newNodeContent("other", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node, otherNode
      ),

      edges = Array(
        Edge.Child(ParentId(node.id), defaultChildData, ChildId(templateNode.id)),
        Edge.Child(ParentId(node.id), defaultChildData, ChildId(otherNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes mustEqual Array.empty
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(node.id), defaultChildData, ChildId(newNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "multiple applications" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node = newNodeContent("node", NodeRole.Task)
    val otherNode = newNodeContent("other", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node, otherNode
      ),

      edges = Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node.id)),
        Edge.Child(ParentId(node.id), defaultChildData, ChildId(otherNode.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node), copyNode(otherNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node.id))),
      Edge.Child(ParentId(copyNodeId(node.id)), defaultChildData, ChildId(copyNodeId(otherNode.id))),
      Edge.DerivedFromTemplate(copyNodeId(node.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node.id)),
      Edge.DerivedFromTemplate(copyNodeId(otherNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(otherNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )

    val graph2 = graph applyChanges changes
    val changes2 = copySubGraphOfNode(graph2, newNode, templateNode)

    changes2.addNodes mustEqual Array.empty
    changes2.delEdges mustEqual Array.empty
    changes2.addEdges must contain theSameElementsAs Array( // TODO: not really idempotent as of changes (but the result is still the same). it readds already existing edges of the newNode.
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node.id))),
      Edge.Child(ParentId(copyNodeId(node.id)), defaultChildData, ChildId(copyNodeId(otherNode.id))),
      Edge.DerivedFromTemplate(copyNodeId(node.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node.id)),
      Edge.DerivedFromTemplate(copyNodeId(otherNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(otherNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )

    val nextNode = newNodeContent("node2", NodeRole.Task)
    val linkedNode = newNodeContent("linked", NodeRole.Task)
    val neutralNode = newNodeContent("neutral", NodeRole.Neutral)
    val graph3 = Graph(
      nodes = graph2.nodes ++ Array(
        nextNode, linkedNode, neutralNode
      ),
      edges = graph2.edges ++ Array(
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(nextNode.id)),
        Edge.LabeledProperty(node.id, EdgeData.LabeledProperty("link"), PropertyId(linkedNode.id)),
        Edge.LabeledProperty(node.id, EdgeData.LabeledProperty("copy"), PropertyId(neutralNode.id)),
      )
    )

    val changes3 = copySubGraphOfNode(graph3, newNode, templateNode)

    changes3.addNodes must contain theSameElementsAs Array(
      copyNode(neutralNode), copyNode(nextNode)
    )
    changes3.delEdges mustEqual Array.empty
    changes3.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node.id))),
      Edge.Child(ParentId(copyNodeId(node.id)), defaultChildData, ChildId(copyNodeId(otherNode.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(nextNode.id))),
      Edge.LabeledProperty(copyNodeId(node.id), EdgeData.LabeledProperty("link"), PropertyId(linkedNode.id)),
      Edge.LabeledProperty(copyNodeId(node.id), EdgeData.LabeledProperty("copy"), PropertyId(copyNodeId(neutralNode.id))),
      Edge.DerivedFromTemplate(copyNodeId(nextNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(nextNode.id)),
      Edge.DerivedFromTemplate(copyNodeId(neutralNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(neutralNode.id)),
      Edge.DerivedFromTemplate(copyNodeId(node.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node.id)),
      Edge.DerivedFromTemplate(copyNodeId(otherNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(otherNode.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }
}
