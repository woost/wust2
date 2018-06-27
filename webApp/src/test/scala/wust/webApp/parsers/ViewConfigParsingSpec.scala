package wust.webApp.parsers

import cats.data.NonEmptyList
import org.scalatest._
import wust.graph.Page
import wust.ids.{NodeId, Cuid}
import java.util.UUID
import wust.webApp.views._
import wust.webApp.views.graphview.GraphView

class ViewConfigParsingSpec extends FreeSpec with MustMatchers {
  def toStringAndBack(viewConfig: ViewConfig): ViewConfig =
    ViewConfig.fromUrlHash(ViewConfig.toUrlHash(viewConfig))

  def freshNodeId(i:Int) = NodeId(Cuid(i, i))

  "empty String" in {
    val cfg = ViewConfig.fromUrlHash("")
    val expected = ViewConfig(new ErrorView(""), Page.empty, None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "invalid String" in {
    val cfg = ViewConfig.fromUrlHash("someone said I should write something here")
    val expected = ViewConfig(new ErrorView(""), Page.empty, None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - row" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph|chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Row, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - column" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph/chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Column, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - auto" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph,chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Auto, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - optional" in {
    pending
    val cuid1 = freshNodeId(1)
    val cuid2 = freshNodeId(2)
    val str = s"view=graph?chat&page=${cuid1.toCuidString},${cuid2.toCuidString}"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Optional, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(NodeId(cuid1), NodeId(cuid2))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "single view - row" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Row, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single view - column" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Column, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single view - auto" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Auto, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single view - optional" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Optional, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty, None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single page" in {
    val orig = ViewConfig.apply(View.default, Page(Seq(freshNodeId(1), freshNodeId(2)), Seq(freshNodeId(3), freshNodeId(4))), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "view and page" in {
    val orig = ViewConfig.apply(ChatView, Page(freshNodeId(5)), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "view and page and prev" in {
    val orig = ViewConfig.apply(SignupView, Page(freshNodeId(6)), Some(ChatView))
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }
}
