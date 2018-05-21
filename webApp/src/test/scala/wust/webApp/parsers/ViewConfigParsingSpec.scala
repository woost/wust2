package wust.webApp.parsers

import cats.data.NonEmptyList
import org.scalatest._
import wust.graph.Page
import wust.ids.PostId
import wust.webApp.views._
import wust.webApp.views.graphview.GraphView

class ViewConfigParsingSpec extends FreeSpec with MustMatchers {
  def toStringAndBack(viewConfig: ViewConfig): ViewConfig =
    ViewConfig.fromUrlHash(ViewConfig.toUrlHash(viewConfig))

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
    val str = "view=graph|chat&page=abc,def"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Row, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(PostId("abc"), PostId("def"))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - column" in {
    pending
    val str = "view=graph/chat&page=abc,def"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Column, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(PostId("abc"), PostId("def"))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - auto" in {
    pending
    val str = "view=graph,chat&page=abc,def"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Auto, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(PostId("abc"), PostId("def"))), None)
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - optional" in {
    pending
    val str = "view=graph?chat&page=abc,def"
    val cfg = ViewConfig.fromUrlHash(str)
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Optional, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(PostId("abc"), PostId("def"))), None)
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
    val orig = ViewConfig.apply(View.default, Page(Seq(PostId("a"), PostId("b")), Seq(PostId("x"), PostId("y"))), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "view and page" in {
    val orig = ViewConfig.apply(ChatView, Page(PostId("dietrich")), None)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "view and page and prev" in {
    val orig = ViewConfig.apply(SignupView, Page(PostId("dietrich")), Some(ChatView))
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }
}
