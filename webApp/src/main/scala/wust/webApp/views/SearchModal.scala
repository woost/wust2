package wust.webApp.views

import flatland.ArraySet
import monix.reactive.Observable
import monix.reactive.subjects.PublishSubject
import org.scalajs.dom
import outwatch.dom._
import outwatch.dom.dsl._
import outwatch.ext.monix._
import rx._
import wust.graph._
import wust.webApp._
import wust.webApp.search.Search
import wust.webApp.state._
import wust.webApp.views.Components._
import wust.webUtil.{Elements, ModalConfig}
import wust.webUtil.outwatchHelpers._

object SearchModal {

  def config(node: Node)(implicit ctx: Ctx.Owner): ModalConfig = {

    sealed trait SearchInput
    object SearchInput {
      final case class Global(query: String) extends SearchInput
      final case class Local(query: String) extends SearchInput
    }

    val searchLocal = PublishSubject[String]
    val searchGlobal = PublishSubject[String]
    val searchInputProcess = PublishSubject[String]

    def renderSearchResult(needle: String, haystack: List[Node], globalSearchScope: Boolean) = {
      val searchRes = Search.byString(needle, haystack, Some(100), 0.75).map{ nodeRes =>
        div(
          cls := "ui approve item",
          fontWeight.normal,
          cursor.pointer,
          padding := "3px",
          Components.nodeCard( nodeRes._1),
          onClick.stopPropagation.useLazy(GlobalState.urlConfig.now.focus(Page(nodeRes._1.id))) --> GlobalState.urlConfig,
          onClick.stopPropagation.use(()) --> GlobalState.uiModalClose
        )
      }

      div(
        s"Found ${searchRes.length} result(s) in ${if (globalSearchScope) "all channels" else "the current workspace"} ",
        padding := "5px 0",
        fontWeight.bold,
        height := s"${dom.window.innerHeight / 2}px",
        div(
          height := "100%",
          overflow.auto,
          searchRes,
        ),
      //TODO: Implement backend search
      //        button(
      //          cls := "ui button",
      //          marginTop := "10px",
      //          display := (if(globalSearchScope) "none" else "block"),
      //          "Search in all channels",
      //          onClick(needle) --> searchGlobal
      //        )
      )
    }

    val searches = Observable(searchLocal.map(SearchInput.Local), searchGlobal.map(SearchInput.Global))
      .merge
      .distinctUntilChanged(cats.Eq.fromUniversalEquals)

    val searchResult: Observable[VDomModifier] = searches.map {
      case SearchInput.Local(query) if query.nonEmpty =>
        val graph = GlobalState.graph.now
        val nodes = graph.nodes.toList
        val nodeIdx = graph.idToIdxOrThrow(node.id)
        val descendants = ArraySet.create(graph.nodes.length)
        graph.descendantsIdxForeach(nodeIdx)(descendants += _)

        val channelDescendants = nodes.filter(n => graph.idToIdxFold(n.id)(false)(descendants(_)))
        renderSearchResult(query, channelDescendants, false)
      case SearchInput.Global(query) if query.nonEmpty =>
        ???
      case _ => VDomModifier.empty
    }

    def header(implicit ctx: Ctx.Owner) = Modal.defaultHeader(
      
      node,
      modalHeader = div(
        cls := "ui search",
        div(
          cls := "ui input action",
          input(
            cls := "prompt",
            placeholder := "Enter search text",
            Elements.valueWithEnter --> searchLocal,
            onChange.value --> searchInputProcess
          ),
          div(
            cursor.pointer,
            cls := "ui primary icon button approve",
            Elements.icon(Icons.search),
            span(cls := "text", "Search", marginLeft := "5px"),
            onClick.stopPropagation(searchInputProcess) --> searchLocal
          ),
        ),
      ),
      icon = Icons.search
    )

    def description(implicit ctx: Ctx.Owner) = VDomModifier(
      cls := "scrolling",
      div(
        cls := "ui fluid search-result",
        searchResult,
      )
    )

    ModalConfig(
      header = header,
      description = description,
      modalModifier = cls := "form",
      contentModifier = VDomModifier.empty
    )
  }
}
