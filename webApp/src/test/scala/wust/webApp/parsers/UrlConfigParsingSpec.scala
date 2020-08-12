package wust.webApp.parsers

import cats.data.NonEmptyList
import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.freespec.AnyFreeSpec
import wust.graph.Page
import wust.ids.{Cuid, NodeId, View, ViewOperator}
import wust.webApp.state.{PageChange, UrlConfig, UrlRoute}

class UrlConfigParsingSpec extends AnyFreeSpec with must.Matchers {
  def createUrlConfig(view: Option[View], page: Page, prevView: Option[View]) = UrlConfig.default.copy(view = view, pageChange = PageChange(page), redirectTo = prevView)

  def toUrlRouteAndBack(viewConfig: UrlConfig): UrlConfig =
    UrlConfigParser.fromUrlRoute(UrlConfigWriter.toUrlRoute(viewConfig))

  def freshNodeId(i:Int) = NodeId(Cuid(i, i))

  "from string to viewconfig - row old" in {
    val cuid1 = freshNodeId(1)
    val str = s"view=graph|chat&page=${cuid1.toBase58}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Row, NonEmptyList[View.Visible](View.Graph, View.Chat :: Nil))),
      Page(cuid1), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - row" in {
    val cuid1 = freshNodeId(1)
    val str = s"view=graph+chat&page=${cuid1.toBase58}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Row, NonEmptyList[View.Visible](View.Graph, View.Chat :: Nil))),
      Page(cuid1), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - column" in {
    val cuid1 = freshNodeId(1)
    val str = s"view=graph/chat&page=${cuid1.toBase58}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Column, NonEmptyList[View.Visible](View.Graph, View.Chat :: Nil))),
      Page(cuid1), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - auto" in {
    val cuid1 = freshNodeId(1)
    val str = s"view=graph,chat&page=${cuid1.toBase58}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Auto, NonEmptyList[View.Visible](View.Graph, View.Chat :: Nil))),
      Page(cuid1), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "from string to viewconfig - optional" in {
    val cuid1 = freshNodeId(1)
    val str = s"view=graph?chat&page=${cuid1.toBase58}"
    val cfg = UrlConfigParser.fromUrlRoute(UrlRoute(None, Some(str)))
    val expected = createUrlConfig(
      Some(View.Tiled(ViewOperator.Optional, NonEmptyList[View.Visible](View.Graph, View.Chat :: Nil))),
      Page(cuid1), None)
    cfg.pageChange mustEqual expected.pageChange
    cfg.view.get.viewKey mustEqual expected.view.get.viewKey
  }

  "single view - row" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Row, NonEmptyList[View.Visible](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single view - column" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Column, NonEmptyList[View.Visible](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single view - auto" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Auto, NonEmptyList[View.Visible](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single view - optional" in {
    val orig = createUrlConfig(Some(View.Tiled(ViewOperator.Optional, NonEmptyList[View.Visible](View.Graph, View.Thread :: Nil))), Page.empty, None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "single page" in {
    val orig = createUrlConfig(Some(View.Chat), Page(freshNodeId(1)), None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "view and page" in {
    val orig = createUrlConfig(Some(View.Thread), Page(freshNodeId(5)), None)
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }

  "view and page and prev" in {
    val orig = createUrlConfig(Some(View.Signup), Page(freshNodeId(6)), Some(View.Thread))
    val cfg = toUrlRouteAndBack(orig)
    cfg.pageChange mustEqual orig.pageChange
    cfg.view.get.viewKey mustEqual orig.view.get.viewKey
  }
}
