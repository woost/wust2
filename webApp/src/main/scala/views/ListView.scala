package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole, UserId}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import flatland._
import wust.webApp.{BrowserDetect, Icons}
import wust.webApp.dragdrop.{DragContainer, DragItem}
import wust.webApp.outwatchHelpers._
import wust.webApp.state.GlobalState
import wust.webApp.views.Components._
import wust.webApp.views.Elements._
import wust.util.collection._

object ListView {
  import SharedViewElements._

  def apply(state: GlobalState)(implicit ctx: Ctx.Owner): VNode = {
    div(
      overflow.auto,
      padding := "10px",

      addListItemInputField(state),

      Rx {
        val graph = state.graph()
        state.page().parentId.map { pageParentId =>
          val pageParentIdx = graph.idToIdx(pageParentId)
          val workspaces = graph.workspacesForParent(pageParentIdx)
          val userTasks = graph.assignedNodesIdx(graph.idToIdx(state.user().id))

          val allTasks: ArraySet = {
            val taskSet = ArraySet.create(graph.size)
            // we go down from workspaces, since tasks which only live in stages have an invalid encoding and should be ignored
//            println("listview workspaces: " + workspaces.map(i => graph.nodes(i).str).mkString(", "))
            algorithm.depthFirstSearchAfterStartsWithContinue(workspaces,graph.notDeletedChildrenIdx, {nodeIdx =>
              val node = graph.nodes(nodeIdx)
//              println("  listview " + node.str)
              node.role match {
                case NodeRole.Task =>
                  @inline def isCorrectlyEncodedTask = graph.notDeletedParentsIdx.exists(nodeIdx)(parentIdx => workspaces.contains(parentIdx)) //  || taskSet.contains(parentIdx) taskSet.contains activates subtasks
//                  println("    listview is Task, correctly encoded: " + isCorrectlyEncodedTask)
                  if(isCorrectlyEncodedTask) {
                     if(userTasks.contains(nodeIdx)) taskSet += nodeIdx
                    false // true goes deeper and also shows subtasks
                  } else false
                case NodeRole.Stage =>
//                  println("    listview is Stage")
                  true
                case _ => false
              }
            })

            taskSet

          }

//          println("listview allTasks: " + allTasks.collectAllElements.map(i => graph.nodes(i).str).mkString(", "))


          val (doneTasks, todoTasks) = allTasks.partition (graph.isDone)


          def renderUserAvatar(assignments: Seq[Node.User], nodeId: NodeId)= div(
            flexGrow := 1,
            Styles.flex,
            flexWrap.wrap,
            flexDirection.rowReverse,
            assignments.map(userNode => div(
              Styles.flexStatic,
              Avatar.user(userNode.id)(
                marginRight := "2px",
                width := "22px",
                height := "22px",
                cls := "avatar",
                marginBottom := "2px",
              ),
              keyed(userNode.id),
              UI.popup := s"Assigned to ${displayUserName(userNode.data)}. Click to remove.",
              cursor.pointer,
              onClick.stopPropagation(GraphChanges.disconnect(Edge.Assigned)(userNode.id, nodeId)) --> state.eventProcessor.changes,
            )),
          )

          val sortedTodoTasks = TaskOrdering.constructOrderingOf[Int](graph, pageParentId, todoTasks.mapToArray(identity), graph.nodeIds)

          VDomModifier(
            div(
              registerSortableContainer(state, DragContainer.List(pageParentId, sortedTodoTasks.map(graph.nodeIds))),
              sortedTodoTasks.map { nodeIdx =>
                val nodeUsers = graph.assignedUsersIdx(nodeIdx)
                val assignments = nodeUsers.map(userIdx => graph.nodes(userIdx).asInstanceOf[Node.User])
                val userAvatar = renderUserAvatar(assignments, graph.nodeIds(nodeIdx))
                val node = graph.nodes(nodeIdx)
                nodeCardWithCheckbox(state, node, pageParentId :: Nil).apply(
                  cls := "draghandle",
                  margin := "4px",
                  userAvatar,
                  sortableAs(DragItem.List.Item(node.id)),
                  dragTarget(DragItem.List.Item(node.id)),
                )
              }
            ),

            doneTasks.calculateNonEmpty.ifTrue[VDomModifier](hr(border := "1px solid black", opacity := 0.4, margin := "15px")),

            div(
              opacity := 0.5,
              TaskOrdering.constructOrderingOf[Int](graph, pageParentId, doneTasks.map(identity), graph.nodeIds).map { nodeIdx =>
                val node = graph.nodes(nodeIdx)
                val nodeUsers = graph.assignedUsersIdx(nodeIdx)
                val assignments = nodeUsers.map(userIdx => graph.nodes(userIdx).asInstanceOf[Node.User])
                nodeCardWithCheckbox(state, node, pageParentId :: Nil).apply(margin := "4px").apply(renderUserAvatar(assignments, graph.nodeIds(nodeIdx)))
              })
          )
        }

      },
    )
  }


  private def addListItemInputField(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    def submitAction(userId: UserId)(str: String) = {
      val createdNode = Node.MarkdownTask(str)
      val change = GraphChanges.addNode(createdNode)
      state.eventProcessor.enriched.changes.onNext(change)
    }

    val placeHolder = if(BrowserDetect.isMobile) "" else "Press Enter to add."

    val inputFieldFocusTrigger = PublishSubject[Unit]

    if(!BrowserDetect.isMobile) {
      state.page.triggerLater {
        inputFieldFocusTrigger.onNext(Unit) // re-gain focus on page-change
        ()
      }
    }

    div(
      Rx {
        inputRow(state, submitAction(state.user().id),
          preFillByShareApi = true,
          autoFocus = !BrowserDetect.isMobile,
          triggerFocus = inputFieldFocusTrigger,
          placeHolderMessage = Some(placeHolder),
          submitIcon = freeSolid.faPlus
        )(ctx)(Styles.flexStatic)
      }
    )
  }

}
