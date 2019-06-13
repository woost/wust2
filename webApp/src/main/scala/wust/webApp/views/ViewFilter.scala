package wust.webApp.views

import flatland.ArraySet
import outwatch.dom._
import outwatch.dom.dsl._
import rx.{Ctx, Rx}
import wust.facades.googleanalytics.Analytics
import wust.graph.{Edge, Graph, GraphChanges}
import wust.ids._
import wust.util.macros.InlineList
import wust.webApp.Icons
import wust.webApp.search.Search
import wust.webApp.state.{GlobalState, ScreenSize}
import wust.webUtil.outwatchHelpers._

object ViewFilter {

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

    val isActive = Rx {
      state.graphTransformations().exists(_.isInstanceOf[GraphOperation.ContentContains])
    }

    div(
      Rx { VDomModifier.ifTrue(state.screenSize() == ScreenSize.Small)(display.none) },
      cls := "ui search",
      div(
        backgroundColor := "rgba(0,0,0,0.15)",
        padding := "5px",
        borderRadius := "3px",
        border := "2px solid transparent",
        isActive.map(VDomModifier.ifTrue(_)(
          border := "2px solid rgb(255,255,255)",
        )),
        cls := "ui small inverted transparent icon input",
        input(
          `type` := "text",
          placeholder := "Filter",
          value <-- clearOnPageSwitch(state),
          onInput.value.debounce(500 milliseconds).map{ needle =>
            val baseTransform = state.graphTransformations.now.filterNot(_.isInstanceOf[GraphOperation.ContentContains])
            if(needle.length < 1) baseTransform
            else baseTransform :+ GraphOperation.ContentContains(needle)
          } --> state.graphTransformations
        ),
        i(cls :="search icon", marginRight := "5px"),
      )
    )
  }
  def clearOnPageSwitch(state: GlobalState)(implicit ctx: Ctx.Owner) = {
    val clear = Handler.unsafe[Unit].mapObservable(_ => "")
    state.page.foreach(_ => clear.onNext(()))
    clear
  }
}








