package wust.webApp.parsers

import cats.data.NonEmptyList
import org.scalatest._
import wust.graph.Page
import wust.ids.{NodeId, Cuid}
import java.util.UUID
import wust.webApp.views._
import wust.webApp.views.graphview.GraphView

class ViewConfigParsingSpec extends FreeSpec with MustMatchers {
  def createViewConfig(view: View, page: Page, prevView: Option[View]) = ViewConfig(view, page, prevView, None)

  def toStringAndBack(viewConfig: ViewConfig): ViewConfig =
    ViewConfig.fromUrlHash(ViewConfig.toUrlHash(viewConfig))

  def freshNodeId(i:Int) = NodeId(Cuid(i, i))

  "empty String" in {
    val cfg = ViewConfig.fromUrlHash("")
    val expected = createViewConfig(new ErrorView(""), Page.empty, None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "invalid String" in {
    val cfg = ViewConfig.fromUrlHash("someone said I should write something here")
    val expected = createViewConfig(new ErrorView(""), Page.empty, None)
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
      new TiledView(ViewOperator.Row, NonEmptyList[View](GraphView, ChatView :: Nil)),
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
      new TiledView(ViewOperator.Column, NonEmptyList[View](GraphView, ChatView :: Nil)),
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
      new TiledView(ViewOperator.Auto, NonEmptyList[View](GraphView, ChatView :: Nil)),
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
      new TiledView(ViewOperator.Optional, NonEmptyList[View](GraphView, ChatView :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.viewKey mustEqual expected.view.viewKey
  }

  "single view - row" in {
    val orig = createViewConfig(new TiledView(ViewOperator.Row, NonEmptyList[View](GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single view - column" in {
    val orig = createViewConfig(new TiledView(ViewOperator.Column, NonEmptyList[View](GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single view - auto" in {
    val orig = createViewConfig(new TiledView(ViewOperator.Auto, NonEmptyList[View](GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single view - optional" in {
    val orig = createViewConfig(new TiledView(ViewOperator.Optional, NonEmptyList[View](GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "single page" in {
    val orig = createViewConfig(ViewList.default, Page(Seq(freshNodeId(1), freshNodeId(2)), Seq(freshNodeId(3), freshNodeId(4))), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "view and page" in {
    val orig = createViewConfig(ChatView, Page(freshNodeId(5)), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }

  "view and page and prev" in {
    val orig = createViewConfig(SignupView, Page(freshNodeId(6)), Some(ChatView))
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.viewKey mustEqual orig.view.viewKey
  }
}
