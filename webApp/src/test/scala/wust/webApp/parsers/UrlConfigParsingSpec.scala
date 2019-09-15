package wust.webApp.parsers

import cats.data.NonEmptyList
import org.scalatest._
import wust.graph.Page
import wust.ids.{Cuid, NodeId, View, ViewOperator}
import wust.webApp.state.{PageChange, UrlConfig, UrlRoute}

class UrlConfigParsingSpec extends FreeSpec with MustMatchers {
  def createUrlConfig(view: Option[View], page: Page, prevView: Option[View]) = UrlConfig(view, PageChange(page), prevView, None, None, None)

  def toUrlRouteAndBack(viewPage: UrlConfig): UrlConfig =
    UrlConfigParser.fromUrlRoute(UrlConfigWriter.toUrlRoute(viewPage))

  def freshNodeId(i:Int) = NodeId(Cuid(i, i))

  "from string to viewconfig - row" in {
    pending
    val cuid1 = freshNodeId(1)
    val str = s"view=graph|chat&page=${cuid1.toCuidString}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Row, NonEmptyList[View](View.Graph, View.Thread :: Nil))),
      Page(NodeId(cuid1)), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - column" in {
    pending
    val cuid1 = freshNodeId(1)
    val str = s"view=graph/chat&page=${cuid1.toCuidString}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Column, NonEmptyList[View](View.Graph, View.Thread :: Nil))),
      Page(NodeId(cuid1)), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - auto" in {
    pending
    val cuid1 = freshNodeId(1)
    val str = s"view=graph,chat&page=${cuid1.toCuidString}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Auto, NonEmptyList[View](View.Graph, View.Thread :: Nil))),
      Page(NodeId(cuid1)), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - optional" in {
    pending
    val cuid1 = freshNodeId(1)
    val str = s"view=graph?chat&page=${cuid1.toCuidString}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Optional, NonEmptyList[View](View.Graph, View.Thread :: Nil))),
      Page(NodeId(cuid1)), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "single view - row" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Row, NonEmptyList[View](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single view - column" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Column, NonEmptyList[View](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single view - auto" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Auto, NonEmptyList[View](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single view - optional" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Optional, NonEmptyList[View](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single page" in {
    pending
    val orig = createUrlConfig(Some(View.Chat), Page(freshNodeId(1)), None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "view and page" in {
    pending
    val orig = createUrlConfig(Some(View.Thread), Page(freshNodeId(5)), None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "view and page and prev" in {
    pending
    val orig = createUrlConfig(Some(View.Signup), Page(freshNodeId(6)), Some(View.Thread))
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }
}
