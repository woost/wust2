package wust.webApp.parsers

import cats.data.NonEmptyList
import org.scalatest._
import wust.graph.Page
import wust.ids.{Cuid, NodeId}
import java.util.UUID

import wust.webApp._
import wust.webApp.state.{View, ViewConfig, ViewOperator}

class ViewConfigParsingSpec extends FreeSpec with MustMatchers {
  def createViewConfig(view: View, page: Page, prevView: Option[View]) = ViewConfig(view, page, prevView, None)

  def toStringAndBack(viewConfig: ViewConfig): ViewConfig =
    ViewConfig.fromUrlHash(ViewConfig.toUrlHash(viewConfig))

  def freshNodeId(i:Int) = NodeId(Cuid(i, i))

  "empty String" in {
    val cfg = ViewConfig.fromUrlHash("")
    val expected = createViewConfig(View.Error(""), Page.empty, None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "invalid String" in {
    val cfg = ViewConfig.fromUrlHash("someone said I should write something here")
    val expected = createViewConfig(View.Error(""), Page.empty, None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "from string to viewconfig - row" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph|chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = createViewConfig(
      View.Tiled(ViewOperator.Row, NonEmptyList[View](View.Graph, View.Chat :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "from string to viewconfig - column" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph/chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = createViewConfig(
      View.Tiled(ViewOperator.Column, NonEmptyList[View](View.Graph, View.Chat :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "from string to viewconfig - auto" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph,chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = createViewConfig(
      View.Tiled(ViewOperator.Auto, NonEmptyList[View](View.Graph, View.Chat :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "from string to viewconfig - optional" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph?chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = createViewConfig(
      View.Tiled(ViewOperator.Optional, NonEmptyList[View](View.Graph, View.Chat :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "single view - row" in {
    val orig = createViewConfig(View.Tiled(ViewOperator.Row, NonEmptyList[View](View.Graph, View.Chat :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single view - column" in {
    val orig = createViewConfig(View.Tiled(ViewOperator.Column, NonEmptyList[View](View.Graph, View.Chat :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single view - auto" in {
    val orig = createViewConfig(View.Tiled(ViewOperator.Auto, NonEmptyList[View](View.Graph, View.Chat :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single view - optional" in {
    val orig = createViewConfig(View.Tiled(ViewOperator.Optional, NonEmptyList[View](View.Graph, View.Chat :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single page" in {
    val orig = createViewConfig(View.default, Page(Seq(freshNodeId(1), freshNodeId(2)), Seq(freshNodeId(3), freshNodeId(4))), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "view and page" in {
    val orig = createViewConfig(View.Chat, Page(freshNodeId(5)), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "view and page and prev" in {
    val orig = createViewConfig(View.Signup, Page(freshNodeId(6)), Some(View.Chat))
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }
}
