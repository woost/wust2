package wust.webApp.views

import cats.data.NonEmptyList
import fastparse.all
import wust.graph.Page
import wust.ids.PostId

import scala.collection.breakOut

private object ViewConfigConstants {
  val parentChildSeparator = ":"
  val idSeparator = ","
  val urlSeparator = "&"
  val viewKey = "view="
  val pageKey = "page="
}
import ViewConfigConstants._

object ViewConfigParser {
  import fastparse.all._

  val word: P[String] = P( ElemsWhileIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9'), min = 1).! )

  //TODO: support nested views with different operators and brackets.
  def viewWithOps(operator: ViewOperator): P[View] = P( word.!.rep(min = 1, sep = operator.separator) ~ (urlSeparator | End) )
    .map(_.toList)
    .map {
      //TODO: better errors for unknown views, error instead?
      case Nil => ??? // cannot happen, because min of repetition is 1
      case view :: Nil => View.viewMap(view)
      case view :: views => new TiledView(operator, NonEmptyList(View.viewMap(view), views.map(View.viewMap)))
    }

  val view: P[View] = P( viewKey ~/ (viewWithOps(ViewOperator.Row) | viewWithOps(ViewOperator.Column) | viewWithOps(ViewOperator.Auto)) )

  val postIdList: Parser[Seq[PostId]] = P( word.!.rep(min = 1, sep = idSeparator) ).map(_.map(PostId.apply))
  val page: P[Page] = P( pageKey ~/ (postIdList ~ (parentChildSeparator ~ postIdList).?).? ~ (urlSeparator | End) )
    .map {
      case None => Page.empty
      case Some((parentIds, None)) => Page(parentIds = parentIds)
      case Some((parentIds, Some(childrenIds))) => Page(parentIds = parentIds, childrenIds = childrenIds)
    }

  // TODO: marke order of values flexible
  val viewConfig: P[ViewConfig] = P( view ~/ page )
    .map { case (view, page) =>
      ViewConfig(view, page)
    }
}

object ViewConfigWriter {
  def write(viewConfig: ViewConfig): String = {
    val viewString = viewConfig.view.key
    val pageString = viewConfig.page match {
      case Page(parentIds, childrenIds) if parentIds.isEmpty && childrenIds.isEmpty => ""
      case Page(parentIds, childrenIds) if childrenIds.isEmpty => s"${parentIds.mkString(idSeparator)}"
      case Page(parentIds, childrenIds) => s"${parentIds.mkString(idSeparator)}$parentChildSeparator${childrenIds.mkString(idSeparator)}"
    }
    s"$viewKey$viewString$urlSeparator$pageKey$pageString"
  }
}
