package wust.webApp.views

import cats.data.NonEmptyList
import org.scalatest._
import wust.graph.Page
import wust.ids.PostId
import wust.webApp.views.graphview.GraphView

class ViewConfigParsingSpec extends FreeSpec with MustMatchers {
  def toStringAndBack(viewConfig: ViewConfig): ViewConfig =
    ViewConfig.fromUrlHash(Some(ViewConfig.toUrlHash(viewConfig)))

  "empty String" in {
    ViewConfig.fromUrlHash(Some("")) mustEqual ViewConfig.default
  }

  "invalid String" in {
   ViewConfig.fromUrlHash(Some("someone said I should write something here")) mustEqual ViewConfig.default
  }

  "none" in {
    ViewConfig.fromUrlHash(None) mustEqual ViewConfig.default
  }

  "from string to viewconfig - row" in {
    val str = "view=graph|chat&page=abc,def"
    val cfg = ViewConfig.fromUrlHash(Some(str))
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Row, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(PostId("abc"), PostId("def"))))
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "from string to viewconfig - column" in {
    val str = "view=graph/chat&page=abc,def"
    val cfg = ViewConfig.fromUrlHash(Some(str))
    val expected = ViewConfig.apply(
      new TiledView(ViewOperator.Column, NonEmptyList[View](new GraphView, ChatView :: Nil)),
      Page(Seq(PostId("abc"), PostId("def"))))
    cfg.page mustEqual expected.page
    cfg.view.key mustEqual expected.view.key
  }

  "single view - row" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Row, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single view - column" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Column, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single view - auto" in {
    val orig = ViewConfig.apply(new TiledView(ViewOperator.Auto, NonEmptyList[View](new GraphView, ChatView :: Nil)), Page.empty)
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "single page" in {
    val orig = ViewConfig.apply(View.default, Page(Seq(PostId("a"), PostId("b")), Seq(PostId("x"), PostId("y"))))
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }

  "view and page" in {
    val orig = ViewConfig.apply(ChatView, Page(PostId("dietrich")))
    val cfg = toStringAndBack(orig)
    cfg.page mustEqual orig.page
    cfg.view.key mustEqual orig.view.key
  }
}
