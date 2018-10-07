package wust.webApp.views

import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.api.ApiEvent
import wust.css.Styles
import wust.graph._
import wust.ids._
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState

import collection.breakOut

object IncrementalChatView {

  def binarySearch(size:Int, access: Int => Long, search: Long):Int = {
    // https://ai.googleblog.com/2006/06/extra-extra-read-all-about-it-nearly.html
    var low = 0
    var high = size - 1

    while(low <= high) {
      val midpoint = low + (high - low) / 2 // avoids overflow
      val elem = access(midpoint)
      if(elem < search) low = midpoint + 1
      else if(elem > search) high = midpoint - 1
      else return midpoint // target found
    }

    -(low + 1) // target not found
  }

  case class LinearChatHistory(groups:Vector[MessageGroup])
  case class MessageGroup(author: Option[Node.User], messages: Vector[Message])

  case class MessageParent(node:Node.Content, author:Option[Node.User])
  case class Message(node:Node.Content, author:Option[Node.User], created: EpochMilli, parents:Vector[MessageParent] = Vector.empty)


  sealed trait ChatHistoryChange
  object ChatHistoryChange {
    case class Replace(history: LinearChatHistory) extends ChatHistoryChange
    case class UpdateMessage(message: Message) extends ChatHistoryChange
    case class AddMessage(message: Message) extends ChatHistoryChange
  }


  def applyHistoryChange(history:LinearChatHistory, change: ChatHistoryChange):LinearChatHistory = {
    import history._
    change match {
      case ChatHistoryChange.Replace(newHistory)       => newHistory
      case ChatHistoryChange.UpdateMessage(newMessage) =>
        val groupIndex = binarySearch(groups.size, i => groups(i).messages.head.created, newMessage.created)
        val group = groups(groupIndex)
        val messageIndex = binarySearch(group.messages.size, i => group.messages(i).created, newMessage.created)
        val targetMessage = group.messages(messageIndex)
        assert(targetMessage.node.id == newMessage.node.id)

        val newGroup = group.copy(messages = group.messages.updated(messageIndex, newMessage))
        val newHistory = copy(groups = groups.updated(groupIndex, newGroup))
        newHistory
    }
  }

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {

    def graphToLinearChatHistory(graph:Graph):LinearChatHistory = {
      //TODO: react on page change? Or is graph enough?
      val page = state.page.now
      val nodeIds = graph.lookup.chronologicalNodesAscending.collect {
        case n: Node.Content if !(page.parentIdSet contains n.id) => graph.lookup.idToIdx(n.id)
      }
      val groups:Vector[MessageGroup] = nodeIds.map{ nodeIdx =>
        val node = graph.lookup.nodes(nodeIdx).asInstanceOf[Node.Content]
        val author = graph.lookup.authorsIdx(nodeIdx).headOption.map(i => graph.lookup.nodes(i).asInstanceOf[Node.User])
        MessageGroup(author, Vector(nodeIdToMessage(node.id)))
      }(breakOut)
      LinearChatHistory(groups)
    }

    def nodeIdToMessage(nodeId:NodeId):Message = {
      val page = state.page.now
      val graph = state.graph.now

      val nodeIdx = graph.lookup.idToIdx(nodeId)

      val node = graph.lookup.nodes(nodeIdx).asInstanceOf[Node.Content]
      val author = graph.lookup.authorsIdx(nodeIdx).headOption.map(i => graph.lookup.nodes(i).asInstanceOf[Node.User])
      val created = graph.lookup.nodeCreated(nodeIdx)
      val parents:Vector[MessageParent] = (graph.parents(nodeId) ++ graph.deletedParents(nodeId) -- page.parentIds).map{id =>
        val node = graph.nodesById(id).asInstanceOf[Node.Content]
        val author = graph.authors(node.id).headOption
        MessageParent(node, author)
      }(breakOut)
      Message(node, author, created, parents)
    }

//    val (incrementalChatHistory,_) = state.incGraph.mapIncremental[LinearChatHistory, Seq[ChatHistoryChange]](graphToLinearChatHistory,{(oldHistory, changeEvent) =>
//      changeEvent match {
//        case ApiEvent.ReplaceGraph(newGraph) =>
//          val newHistory = graphToLinearChatHistory(newGraph)
//          (newHistory, Seq(ChatHistoryChange.Replace(newHistory)))
//        case ApiEvent.NewGraphChanges(_, inconsistentGraphChanges) =>
//          val graphChanges = inconsistentGraphChanges.consistent
//          val page = state.page.now
//          val graph = state.graph.now // should we get the graph from somewhere else?
//
//          val addedMessageIds = graphChanges.addNodes.map(_.id).diff(page.parentIdSet)
//          val addMessages = addedMessageIds.map(id => ChatHistoryChange.AddMessage(nodeIdToMessage(id)))
//
//          val updatedMessageIds = graphChanges.addEdges.flatMap(e => List(e.sourceId, e.targetId)) ++ graphChanges.delEdges.flatMap(e => List(e.sourceId, e.targetId)) -- addedMessageIds
//          val updateMessages = updatedMessageIds.map(id => ChatHistoryChange.UpdateMessage(nodeIdToMessage(id)))
//
//          val actions = (addMessages ++ updateMessages).toSeq
//          val newState = actions.foldLeft(oldHistory){ (history, action) => applyHistoryChange(history, action) }
//          (newState, actions)
//      }
//    })

    def renderChatHistory(history:LinearChatHistory):VNode = {
      div(
        border := "2px solid black",
        padding := "5px", margin := "5px",
        history.groups.map(group => div(
          border := "2px solid steelblue",
          padding := "5px", margin := "5px",
          group.author.map(a => Avatar.apply(a)(width := "100px")),
          group.messages.map(message => div(
            border := "2px solid mistyrose",
            padding := "5px", margin := "5px",
            message.created,
            Rendered.renderNodeData(message.node.data)
          ))
        ))
      )
    }

//    val commands = incrementalChatHistory.outAction.map{ case ChatHistoryChange.Replace => }

    div(
      Styles.flex,
      flexDirection.column,
      alignItems.stretch,
      alignContent.stretch,
      height := "100%",
      div(
        Styles.flex,
        flexDirection.column,
        height := "100%",
        position.relative,
        Rx{renderChatHistory(graphToLinearChatHistory(state.graph()))}
      ),
    )
  }
}
