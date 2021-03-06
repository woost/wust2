package wust.webApp.views

import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.freespec.AnyFreeSpec
import wust.graph._
import wust.ids._
import wust.webApp.state.GraphChangesAutomation

import scala.collection.breakOut


class GraphChangesAutomationSpec extends AnyFreeSpec with must.Matchers {
  def randomPositiveInt() = {
    val r = scala.util.Random.nextInt
    if (r < 0) r * -1 else r
  }
  def freshNodeId() = NodeId(Cuid(0, randomPositiveInt()))
  def copyNodeId(nodeId: NodeId) = NodeId(Cuid(nodeId.right, nodeId.left))
  def copyNode(node: Node.Content) = node.copy(id = copyNodeId(node.id))
  def newNodeContent(str: String, role: NodeRole) = Node.Content(freshNodeId(), NodeData.Markdown(str), role)
  def newNodeUser(str: String) = Node.User(UserId(freshNodeId()), NodeData.User(str, false, 0, None), NodeMeta.User)
  val copyTime = EpochMilli.now

  val defaultChildData = EdgeData.Child(deletedAt = None, ordering = BigDecimal(0))

  def copySubGraphOfNode(graph: Graph, newNode: Node.Content, templateNode: Node.Content): GraphChanges = copySubGraphOfNode(graph, newNode, Seq(templateNode))
  def copySubGraphOfNode(graph: Graph, newNode: Node.Content, templateNodes: Seq[Node.Content]): GraphChanges = {
    val newIdMap = scala.collection.mutable.HashMap[NodeId, Int]()
    GraphChangesAutomation.copySubGraphOfNode(
      UserId(freshNodeId()), graph, newNode, templateNodes.map(node => graph.idToIdxOrThrow(node.id))(breakOut), newId = copyNodeId(_), copyTime = copyTime, toastEnabled = false
    )
  }

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
    changes.addEdges mustEqual Array.empty
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
    changes.addEdges mustEqual Array.empty
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
    changes.addEdges mustEqual Array.empty
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
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(), TemplateId(templateNode1.id)),
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

  "reference node inside template with rename" in {
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
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(isRename = true), TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      node2.copy(id = node1.id), copyNode(childNode2)
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

  "reference node inside template with create" in {
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
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(isCreate = true), TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node1), copyNode(childNode2)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(copyNodeId(node1.id)), defaultChildData, ChildId(copyNodeId(childNode2.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node1.id))),
      Edge.DerivedFromTemplate(copyNodeId(node1.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(copyNodeId(childNode2.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(childNode2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "reference node inside template with create and rename" in {
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
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(isCreate = true, isRename = true), TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(node2.copy(id = node1.id)), copyNode(childNode2)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(copyNodeId(node1.id)), defaultChildData, ChildId(copyNodeId(childNode2.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(copyNodeId(node1.id))),
      Edge.DerivedFromTemplate(copyNodeId(node1.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(copyNodeId(childNode2.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(childNode2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "reference node inside template with multiple targets" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node1a = newNodeContent("node1a", NodeRole.Task)
    val node1b = newNodeContent("node1b", NodeRole.Task)
    val node2 = newNodeContent("node2", NodeRole.Task)
    val childNode1a = newNodeContent("child1a", NodeRole.Task)
    val childNode1b = newNodeContent("child1b", NodeRole.Task)
    val childNode2 = newNodeContent("child2", NodeRole.Task)
    val templateNode1a = newNodeContent("template-node1a", NodeRole.Task)
    val templateNode1b = newNodeContent("template-node1b", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node1a, node1b, node2, childNode1a, childNode1b, childNode2, templateNode1a, templateNode1b
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1a.id)),
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1b.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node2.id)),
        Edge.Child(ParentId(node1a.id), defaultChildData, ChildId(childNode1a.id)),
        Edge.Child(ParentId(node1b.id), defaultChildData, ChildId(childNode1b.id)),
        Edge.Child(ParentId(node2.id), defaultChildData, ChildId(childNode2.id)),
        Edge.DerivedFromTemplate(node1a.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode1a.id)),
        Edge.DerivedFromTemplate(node1b.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode1b.id)),
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(), TemplateId(templateNode1a.id)),
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(), TemplateId(templateNode1b.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(childNode2)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(node1a.id), defaultChildData, ChildId(copyNodeId(childNode2.id))),
      Edge.Child(ParentId(node1b.id), defaultChildData, ChildId(copyNodeId(childNode2.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1a.id)),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1b.id)),
      Edge.DerivedFromTemplate(node1a.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(node1b.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
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
        Edge.ReferencesTemplate(templateNode.id, EdgeData.ReferencesTemplate(), TemplateId(templateNode0.id)),
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

  "reference node with not-reusing task of same name" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node1 = newNodeContent("node1", NodeRole.Task)
    val node2 = newNodeContent("node2", NodeRole.Task)
    val task1 = newNodeContent("task", NodeRole.Task)
    val task2 = newNodeContent("task", NodeRole.Task)
    val taskNode = newNodeContent("task-node", NodeRole.Task)
    val templateNode1 = newNodeContent("template-node1", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node1, node2, task1, task2, templateNode1, taskNode
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node2.id)),
        Edge.Child(ParentId(node1.id), defaultChildData, ChildId(task1.id)),
        Edge.Child(ParentId(node2.id), defaultChildData, ChildId(task2.id)),
        Edge.Child(ParentId(task2.id), defaultChildData, ChildId(taskNode.id)),
        Edge.DerivedFromTemplate(node1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode1.id)),
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(), TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(task2), copyNode(taskNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(copyNodeId(task2.id)), defaultChildData, ChildId(copyNodeId(taskNode.id))),
      Edge.Child(ParentId(node1.id), defaultChildData, ChildId(copyNodeId(task2.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
      Edge.DerivedFromTemplate(node1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(copyNodeId(taskNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(taskNode.id)),
      Edge.DerivedFromTemplate(copyNodeId(task2.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(task2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "reference node with reusing stage of same name" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val node1 = newNodeContent("node1", NodeRole.Task)
    val node2 = newNodeContent("node2", NodeRole.Task)
    val stage1 = newNodeContent("stage", NodeRole.Stage)
    val stage2 = newNodeContent("stage", NodeRole.Stage)
    val stageNode = newNodeContent("stage-node", NodeRole.Task)
    val templateNode1 = newNodeContent("template-node1", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, node1, node2, stage1, stage2, templateNode1, stageNode
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(node2.id)),
        Edge.Child(ParentId(node1.id), defaultChildData, ChildId(stage1.id)),
        Edge.Child(ParentId(node2.id), defaultChildData, ChildId(stage2.id)),
        Edge.Child(ParentId(stage2.id), defaultChildData, ChildId(stageNode.id)),
        Edge.DerivedFromTemplate(node1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode1.id)),
        Edge.ReferencesTemplate(node2.id, EdgeData.ReferencesTemplate(), TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(stageNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(stage1.id), defaultChildData, ChildId(copyNodeId(stageNode.id))),
      Edge.Child(ParentId(node1.id), defaultChildData, ChildId(stage1.id)),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(node1.id)),
      Edge.DerivedFromTemplate(node1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(node2.id)),
      Edge.DerivedFromTemplate(copyNodeId(stageNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(stageNode.id)),
      Edge.DerivedFromTemplate(stage1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(stage2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "reference node with reusing stage of same name as direct child" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val stage1 = newNodeContent("stage", NodeRole.Stage)
    val stage2 = newNodeContent("stage", NodeRole.Stage)
    val stageNode = newNodeContent("stage-node", NodeRole.Task)
    val templateNode1 = newNodeContent("template-node1", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, stage1, stage2, stageNode
      ),

      edges = Array(
        Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(stage1.id)),
        Edge.Child(ParentId(templateNode.id), defaultChildData, ChildId(stage2.id)),
        Edge.Child(ParentId(stage2.id), defaultChildData, ChildId(stageNode.id)),
        Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode1.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      copyNode(stageNode)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.Child(ParentId(stage1.id), defaultChildData, ChildId(copyNodeId(stageNode.id))),
      Edge.Child(ParentId(newNode.id), defaultChildData, ChildId(stage1.id)),
      Edge.DerivedFromTemplate(copyNodeId(stageNode.id), EdgeData.DerivedFromTemplate(copyTime), TemplateId(stageNode.id)),
      Edge.DerivedFromTemplate(stage1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(stage2.id)),
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

  "neutral properties with same name" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val neutralNode1 = newNodeContent("neutral1", NodeRole.Neutral)
    val neutralNode2 = newNodeContent("neutral2", NodeRole.Neutral)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, neutralNode1, neutralNode2
      ),

      edges = Array(
        Edge.LabeledProperty(newNode.id, EdgeData.LabeledProperty("copy", showOnCard = false), PropertyId(neutralNode1.id)),
        Edge.LabeledProperty(templateNode.id, EdgeData.LabeledProperty("copy", showOnCard = true), PropertyId(neutralNode2.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes must contain theSameElementsAs Array(
      neutralNode2.copy(id = neutralNode1.id)
    )
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.LabeledProperty(newNode.id, EdgeData.LabeledProperty("copy", showOnCard = true), PropertyId(neutralNode1.id)),
      Edge.DerivedFromTemplate(neutralNode1.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(neutralNode2.id)),
      Edge.DerivedFromTemplate(newNode.id, EdgeData.DerivedFromTemplate(copyTime), TemplateId(templateNode.id)),
    )
  }

  "linked properties with same name" in {
    val newNode = newNodeContent("new-node", NodeRole.Task)
    val templateNode = newNodeContent("template", NodeRole.Task)
    val linkNode1 = newNodeContent("link1", NodeRole.Task)
    val linkNode2 = newNodeContent("link2", NodeRole.Task)
    val graph = Graph(
      nodes = Array(
        templateNode, newNode, linkNode1, linkNode2
      ),

      edges = Array(
        Edge.LabeledProperty(newNode.id, EdgeData.LabeledProperty("link", showOnCard = false), PropertyId(linkNode1.id)),
        Edge.LabeledProperty(templateNode.id, EdgeData.LabeledProperty("link", showOnCard = true), PropertyId(linkNode2.id)),
      )
    )

    val changes = copySubGraphOfNode(graph, newNode, templateNode)

    changes.addNodes mustEqual Array.empty
    changes.delEdges mustEqual Array.empty
    changes.addEdges must contain theSameElementsAs Array(
      Edge.LabeledProperty(newNode.id, EdgeData.LabeledProperty("link", showOnCard = true), PropertyId(linkNode2.id)),
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
