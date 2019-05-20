package wust.webApp.views

import flatland.ArraySet
import fomanticui.SidebarOptions
import googleAnalytics.Analytics
import jquery.JQuerySelection
import monix.reactive.Observable
import monix.reactive.subjects.BehaviorSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import rx.Var.Assignment
import rx.{Ctx, Rx, Var}
import supertagged.TaggedType
import wust.css.ZIndex
import wust.graph.{Edge, Graph, Node}
import wust.ids._
import wust.graph.GraphChanges
import wust.util.algorithm
import wust.util.macros.{InlineList, SubObjects}
import wust.webApp.Ownable
import wust.webApp.Icons
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webApp.outwatchHelpers._
import wust.webApp.search.Search

import scala.collection.breakOut

object ViewFilter {

  private def allTransformations(state: GlobalState)(implicit ctx: Ctx.Owner): List[ViewGraphTransformation] = List(
//    ViewGraphTransformation.Deleted.inGracePeriod(state),
    ViewGraphTransformation.Deleted.onlyDeleted(state),
    ViewGraphTransformation.Deleted.excludeDeleted(state),
//    ViewGraphTransformation.Deleted.noDeletedButGraced(state),
    ViewGraphTransformation.Assignments.onlyAssignedTo(state),
    ViewGraphTransformation.Assignments.onlyNotAssigned(state),
    ViewGraphTransformation.Automated.hideTemplates(state),
    //    Identity(state),
  )

  def moveableWindow(state: GlobalState, position: MoveableElement.Position)(implicit ctx: Ctx.Owner): MoveableElement.Window = {

    val filterTransformations: Seq[ViewGraphTransformation] = allTransformations(state)

    MoveableElement.Window(
      VDomModifier(
        Icons.filter,
        span(marginLeft := "5px", "Filter"),
        state.isFilterActive.map {
          case true => backgroundColor := "green"
          case false => VDomModifier.empty
        }
      ),
      toggle = state.showFilterList,
      initialPosition = position,
      initialWidth = 260,
      initialHeight = 250,
      resizable = false,
      bodyModifier = Ownable { implicit ctx =>
        VDomModifier(
          padding := "5px",
          Rx {
            backgroundColor := state.pageStyle().bgLightColor,
          },

          Components.verticalMenu(
            filterTransformations.map { transformation =>
              Components.MenuItem(
                title = transformation.icon,
                description = transformation.description,
                active = state.graphTransformations.map(_.contains(transformation.transform) ^ transformation.invertedSwitch),
                clickAction = { () =>
                  state.graphTransformations.update { transformations =>
                    if (transformations.contains(transformation.transform)) transformations.filter(_ != transformation.transform)
                    else transformations.filterNot(transformation.disablesTransform.contains) ++ (transformation.enablesTransform :+ transformation.transform)
                  }
                  Analytics.sendEvent("filter", transformation.toString)
                }
              )
            }
          ),
          div(
            cursor.pointer,
            Elements.icon(Icons.noFilter),
            span("Reset ALL filters"),
            onClick(state.defaultTransformations) --> state.graphTransformations,
            onClick foreach { Analytics.sendEvent("filter", "reset") },
          )
        )
      }
    )
  }

  def addLabeledFilterCheckbox(state: GlobalState, filterName: String, header: VDomModifier, description: VDomModifier, transform: UserViewGraphTransformation)(implicit ctx: Ctx.Owner): VNode = {
    val checkbox = addFilterCheckbox(state, filterName, transform)

    div(
      cls := "item",
      div(
        cls := "ui checkbox toggle",
        checkbox,
      ),
      header,
      description
    )
  }

  def addFilterCheckbox(state: GlobalState, filterName: String, transform: UserViewGraphTransformation)(implicit ctx: Ctx.Owner): VNode = {
    val activeFilter = (doActivate: Boolean) =>  if(doActivate) {
      state.graphTransformations.map(_ :+ transform)
    } else {
      state.graphTransformations.map(_.filter(_ != transform))
    }

    input(tpe := "checkbox",
      onChange.checked.map(v => activeFilter(v).now) --> state.graphTransformations,
      onChange.checked foreach { enabled => if(enabled) Analytics.sendEvent("filter", s"$filterName") },
      checked <-- state.graphTransformations.map(_.contains(transform)),
    )
  }

  def addCurrentlyFilteredTags(state: GlobalState, nodeId: NodeId) = {
    val currentTagFilters:Seq[ParentId] = {
      state.graphTransformations.now.collect {
        case GraphOperation.OnlyTaggedWith(tagId) => ParentId(tagId)
      }
    }
    GraphChanges.addToParents(ChildId(nodeId), currentTagFilters)
  }

  def filterBySearchInputWithIcon(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    import scala.concurrent.duration._

    div(
      Rx { VDomModifier.ifTrue(state.screenSize() == ScreenSize.Small)(display.none) },
      cls := "ui search",
      div(
        state.isFilterActive.map(VDomModifier.ifTrue(_)(
          color := "green",
          outline := "none",
          borderColor := "green",
          boxShadow := "0 0 10px 3px green",
        )),
        cls := "ui icon input",
        input(
          `type` := "text",
          placeholder := "Filter",
          value <-- Elements.clearOnPageSwitch(state),
          onInput.value.debounce(500 milliseconds).map{ needle =>
            val baseTransform = state.graphTransformations.now.filterNot(_.isInstanceOf[GraphOperation.ContentContains])
            if(needle.length < 2) baseTransform
            else baseTransform :+ GraphOperation.ContentContains(needle)
          } --> state.graphTransformations
        ),
        i(cls :="search icon"),
      )
    )
  }

}


case class ViewGraphTransformation(
  state: GlobalState,
  transform: UserViewGraphTransformation,
  icon: VDomModifier,
  description: String,
  enablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation],
  disablesTransform: Seq[UserViewGraphTransformation] = Seq.empty[UserViewGraphTransformation],
  invertedSwitch: Boolean = false, //Filter is active, so enabling it will turn it off
)

object ViewGraphTransformation {
  def identity(state: GlobalState) = ViewGraphTransformation(
    state = state,
    icon = Icons.noFilter,
    description = "Reset ALL filters",
    transform = GraphOperation.Identity,
  )

  object Deleted {
    def onlyDeleted(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.delete,
      description = "Show only deleted items",
      transform = GraphOperation.OnlyDeletedChildren,
      disablesTransform = List(GraphOperation.ExcludeDeletedChildren)
    )
    def excludeDeleted(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.undelete,
      description = "Show deleted items", // Turns this filter off
      transform = GraphOperation.ExcludeDeletedChildren,
      invertedSwitch = true
    )
  }

  object Automated {
    def hideTemplates(state: GlobalState) = ViewGraphTransformation(
      state = state,
      icon = Icons.automate,
      description = "Show automation templates", // Turns this filter off
      transform = GraphOperation.AutomatedHideTemplates,
      invertedSwitch = true
    )
  }

  object Assignments {
    def onlyAssignedTo(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.task,
      description = s"Show items assigned to: Me",
      transform = GraphOperation.OnlyAssignedTo,
    )
    def onlyNotAssigned(state: GlobalState) = ViewGraphTransformation (
      state = state,
      icon = Icons.task,
      description = "Show unassigned items",
      transform = GraphOperation.OnlyNotAssigned,
    )
  }

}

sealed trait UserViewGraphTransformation {
  def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): GraphOperation.EdgeFilter
}
object GraphOperation {
  type EdgeFilter = Option[Int => Boolean] // edgeidx => boolean

  def filter(graph: Graph, pageId: NodeId, userId: UserId, filters: Seq[UserViewGraphTransformation]): Graph = {
    val pageIdx = graph.idToIdx(pageId)
    val userIdx = graph.idToIdxOrThrow(userId)
    val edgeFilters: Seq[Int => Boolean] = filters.flatMap(_.filterWithViewData(pageIdx, userIdx, graph))
    if (edgeFilters.isEmpty) graph else {
      val newEdges = Array.newBuilder[Edge]
      flatland.loop(graph.edges.length) { i =>
        if (edgeFilters.forall(_(i))) newEdges += graph.edges(i)
      }

      graph.copyOnlyEdges(newEdges.result)
    }
  }

  case class OnlyTaggedWith(tagId: NodeId) extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.flatMap { _ =>
        graph.idToIdx(tagId).map { tagIdx =>
          edgeIdx => graph.edges(edgeIdx) match {
            case _: Edge.Child =>
              val childIdx = graph.edgesIdx.b(edgeIdx)
              val node = graph.nodes(childIdx)
              if(InlineList.contains[NodeRole](NodeRole.Message, NodeRole.Task)(node.role)) {
                if(graph.tagParentsIdx.contains(childIdx)(tagIdx)) true
                else {
                  if(graph.descendantsIdxExists(tagIdx)(_ == childIdx)) true
                  else false
                }
              } else true
            case _ => true
          }
        }
      }
    }
  }

  case object OnlyDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.map { _ =>
        edgeIdx => graph.edges(edgeIdx) match {
          case _: Edge.Child =>
            val parentIdx = graph.edgesIdx.a(edgeIdx)
            val childIdx = graph.edgesIdx.b(edgeIdx)
            graph.isDeletedNowIdx(childIdx, parentIdx)
          case _ => true
        }
      }
    }
  }

  case object ExcludeDeletedChildren extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      pageIdx.map { _ =>
        edgeIdx => graph.edges(edgeIdx) match {
          case _: Edge.Child  =>
            val parentIdx = graph.edgesIdx.a(edgeIdx)
            val childIdx = graph.edgesIdx.b(edgeIdx)
            !graph.isDeletedNowIdx(childIdx, parentIdx)
          case _ => true
        }
      }
    }
  }

  case object AutomatedHideTemplates extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      val templateNodes = ArraySet.create(graph.nodes.length)
      graph.automatedEdgeIdx.foreach(_.foreach( edgeIdx =>
        templateNodes += graph.edgesIdx.b(edgeIdx)

      ))
      Some(edgeIdx => graph.edges(edgeIdx) match {
        case _: Edge.Child =>
          val childIdx = graph.edgesIdx.b(edgeIdx)
          !templateNodes.contains(childIdx)
        case _ => true
      })
    }
  }

  case object OnlyAssignedTo extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      val assignedNodes = ArraySet.create(graph.nodes.length)
      graph.assignedNodesIdx.foreachElement(userIdx)(assignedNodes += _)
      Some(edgeIdx => graph.edges(edgeIdx) match {
        case _: Edge.Child =>
          val childIdx = graph.edgesIdx.b(edgeIdx)
          graph.nodes(childIdx).role != NodeRole.Task || assignedNodes.contains(childIdx)
        case _ => true
      })
    }
  }

  case object OnlyNotAssigned extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      val assignedNodes = ArraySet.create(graph.nodes.length)
      graph.assignedNodesIdx.foreachElement(userIdx)(assignedNodes += _)
      Some(edgeIdx => graph.edges(edgeIdx) match {
        case _: Edge.Child =>
          val childIdx = graph.edgesIdx.b(edgeIdx)
          graph.nodes(childIdx).role != NodeRole.Task || !assignedNodes.contains(childIdx)
        case _ => true
      })
    }
  }

  case object Identity extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      None
    }
  }

  case class ContentContains(needle: String) extends UserViewGraphTransformation {
    def filterWithViewData(pageIdx: Option[Int], userIdx: Int, graph: Graph): EdgeFilter = {
      //TODO better without descendants? one dfs?

      pageIdx.map { _ =>
        val foundChildren = ArraySet.create(graph.nodes.length)
        flatland.loop(graph.nodes.length) { nodeIdx =>
          if (graph.parentsIdx.sliceNonEmpty(nodeIdx) && Search.singleByString(needle, graph.nodes(nodeIdx), 0.75).isDefined) {
            foundChildren += nodeIdx
          }
        }

        edgeIdx => graph.edges(edgeIdx) match {
          case _: Edge.Child =>
            val childIdx = graph.edgesIdx.b(edgeIdx)
            if (foundChildren.contains(childIdx)) true
            else {
              val hasDescendant = graph.descendantsIdxExists(childIdx)(foundChildren.contains)
              if (hasDescendant) foundChildren += childIdx // cache the result of this partial dfs
              hasDescendant
            }
          case _ => true
        }
      }
    }
  }
}
