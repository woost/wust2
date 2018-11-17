package wust.webApp.views

import fontAwesome.{freeRegular, freeSolid}
import monix.reactive.subjects.PublishSubject
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.css.Styles
import wust.graph._
import wust.ids.{NodeData, NodeId, NodeRole}
import wust.sdk.BaseColors
import wust.sdk.NodeColor._
import wust.util._
import wust.util.ArraySet
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
        withLoadingAnimation(state) {
          val page = state.page()

          val graph = {
            val g = state.graph()
            val transitivePageChildren = page.parentId.toSeq.flatMap(g.notDeletedDescendants)
            g.filterIds(page.parentId.toSet ++ transitivePageChildren.toSet ++ transitivePageChildren.flatMap(id => g.authors(id).map(_.id)))
          }

          val doneIdx: Int = graph.nodes.findIdx(node => node.str.trim.toLowerCase == "done").getOrElse(-2) // TODO: this also finds sub-columns named done. Prevent this and find only toplevel columns?
          val doneNode = doneIdx match {
            case -2 => None
            case idx => Some(graph.nodes(idx))
          }

          val pageParentArraySet = graph.createArraySet(page.parentId) // TODO: remove, since set only contains max one item

          val allTasks: ArraySet = graph.subset { nodeIdx =>
            val node = graph.nodes(nodeIdx)

            @inline def isDoneNode = nodeIdx == doneIdx
            @inline def isContent = node.isInstanceOf[Node.Content]
            @inline def isTask = node.role.isInstanceOf[NodeRole.Task.type]
            @inline def noPage = pageParentArraySet.containsNot(nodeIdx)

            !isDoneNode && isContent && isTask && noPage
          }


          val (todoTasks, doneTasks) = {
            doneIdx match {
              case -2 => (allTasks, ArraySet.create(graph.nodes.length))
              case doneIdx =>
                allTasks.partition { nodeIdx =>
                  @inline def isDone = graph.parentsIdx.exists(nodeIdx)(_ == doneIdx)

                  !isDone
                }
            }
          }

          page.parentId.map{ pageParentId =>
            VDomModifier(
            div(todoTasks.map{nodeIdx =>
              val node = graph.nodes(nodeIdx)
              nodeCard(node).apply(margin := "4px").prepend(
                Styles.flex,
                alignItems.flexStart,
                div(
                  cls := "ui checkbox fitted",
                  marginTop := "5px",
                  marginLeft := "5px",
                  marginRight := "3px",
                  input(
                    tpe := "checkbox",
                    onChange.checked foreach { checked =>
                      if(checked) {
                        val (doneNodeId, doneNodeAddChange) = doneNode match {
                          case None                   =>
                            val freshDoneNode = Node.MarkdownTask("Done")
                            val expand = GraphChanges.connect(Edge.Expanded)(state.user.now.id, freshDoneNode.id)
                            (freshDoneNode.id, GraphChanges.addNodeWithParent(freshDoneNode, pageParentId) merge expand)
                          case Some(existingDoneNode) => (existingDoneNode.id, GraphChanges.empty)
                        }
                        val changes = doneNodeAddChange merge GraphChanges.changeTarget(Edge.Parent)(node.id :: Nil, state.graph.now.parents(node.id), doneNodeId :: Nil)
                        state.eventProcessor.changes.onNext(changes)
                      }
                    }
                  ),
                  label()
                )
              )}),
            doneTasks.calculateNonEmpty.ifTrue[VDomModifier](hr(border := "1px solid black", opacity := 0.4, margin := "15px")),
            div(
              opacity := 0.5,
              doneTasks.map{nodeIdx =>
                val node = graph.nodes(nodeIdx)
                nodeCard(node).apply(margin := "4px").prepend(
                  Styles.flex,
                  alignItems.flexStart,
                  div(
                    cls := "ui checkbox fitted",
                    marginTop := "5px",
                    marginLeft := "5px",
                    marginRight := "3px",
                    input(
                      tpe := "checkbox",
                      checked := true,
                      onChange.checked foreach { checked =>
                        if(!checked) {
                          val changes = GraphChanges.changeTarget(Edge.Parent)(node.id :: Nil, doneNode.get.id :: Nil, pageParentId :: Nil)
                          state.eventProcessor.changes.onNext(changes)
                        }
                      }
                    ),
                    label()
                  )
                )}))
          }
        }
      },
    )
  }

  private def addListItemInputField(state: GlobalState)(implicit ctx:Ctx.Owner) = {
    def submitAction(str:String) = {
      val change = GraphChanges.addMarkdownTask(str)
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

    inputField(state, submitAction,
      preFillByShareApi = true,
      autoFocus = !BrowserDetect.isMobile,
      triggerFocus = inputFieldFocusTrigger,
      placeHolderMessage = Some(placeHolder),
      submitIcon = freeSolid.faPlus
    )(ctx)(Styles.flexStatic)
  }

}
