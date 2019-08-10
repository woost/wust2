package wust.webApp.views

import flatland.ArraySet
import outwatch.dom._
import outwatch.dom.dsl._
import rx._
import wust.facades.googleanalytics.Analytics
import wust.graph.{ Edge, Graph, GraphChanges }
import wust.ids._
import wust.util.macros.InlineList
import wust.webApp.Icons
import wust.webApp.search.Search
import wust.webApp.state.{ GlobalState, ScreenSize }
import wust.webUtil.outwatchHelpers._
import wust.webApp.state.FeatureState

object ViewFilter {

  def addFilterCheckbox(filterName: String, transform: UserViewGraphTransformation)(implicit ctx: Ctx.Owner): VNode = {
    val activeFilter = (doActivate: Boolean) => if (doActivate) {
      GlobalState.graphTransformations.map(_ :+ transform)
    } else {
      GlobalState.graphTransformations.map(_.filter(_ != transform))
    }

    input(
      tpe := "checkbox",
      onChange.checked.map(v => activeFilter(v).now) --> GlobalState.graphTransformations,
      onChange.checked foreach { enabled =>
        if (enabled) {
          transform match {
            case onlyTaggedWith: GraphOperation.OnlyTaggedWith =>
              val isNestedTag = GlobalState.graph.now.children(onlyTaggedWith.tagId).exists(parentId => GlobalState.graph.now.nodesById(parentId).exists(_.role == NodeRole.Tag))
              if (isNestedTag)
                FeatureState.use(Feature.FilterByTagWithSubTag)
              else
                FeatureState.use(Feature.FilterByTag)
            case _ =>
          }
        }
      },
      checked <-- GlobalState.graphTransformations.map(_.contains(transform)),
    )
  }

  def addCurrentlyFilteredTags(nodeId: NodeId) = {
    val currentTagFilters: Seq[ParentId] = {
      GlobalState.graphTransformations.now.collect {
        case GraphOperation.OnlyTaggedWith(tagId) => ParentId(tagId)
      }
    }
    GraphChanges.addToParents(ChildId(nodeId), currentTagFilters)
  }

  def filterBySearchInputWithIcon(implicit ctx: Ctx.Owner) = {
    import scala.concurrent.duration._

    val isActive = Rx {
      GlobalState.graphTransformations().exists(_.isInstanceOf[GraphOperation.ContentContains])
    }
    val focused = Var(false)

    div(
      Rx { VDomModifier.ifTrue(GlobalState.screenSize() == ScreenSize.Small)(display.none) },
      cls := "ui search",
      div(
        backgroundColor := "rgba(0,0,0,0.15)",
        padding := "5px",
        borderRadius := "3px",
        border := "2px solid transparent",
        Rx{
          if (focused()) width := "150px"
          else width := "75px"
        },
        isActive.map(VDomModifier.ifTrue(_)(
          border := "2px solid rgb(255,255,255)",
        )),
        cls := "ui small inverted transparent icon input",
        input(
          `type` := "text",
          placeholder := "Filter",
          value <-- clearOnPageSwitch,
          onFocus(true) --> focused,
          onBlur(false) --> focused,
          onInput.value.debounce(500 milliseconds).map{ needle =>
            val baseTransform = GlobalState.graphTransformations.now.filterNot(_.isInstanceOf[GraphOperation.ContentContains])
            if (needle.length < 1) baseTransform
            else baseTransform :+ GraphOperation.ContentContains(needle)
          } --> GlobalState.graphTransformations
        ),
        i(cls := "search icon", marginRight := "5px"),
      )
    )
  }
  def clearOnPageSwitch(implicit ctx: Ctx.Owner) = {
    val clear = Handler.unsafe[Unit].mapObservable(_ => "")
    GlobalState.page.foreach(_ => clear.onNext(()))
    clear
  }
}
