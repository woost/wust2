package wust.webApp.parsers

import cats.data.NonEmptyList
import wust.graph.{Page,PageMode}
import wust.ids.PostId
import wust.webApp.views.{TiledView, View, ViewConfig, ViewOperator}

private object ViewConfigConstants {
  val parentChildSeparator = ":"
  val idSeparator = ","
  val urlSeparator = "&"
  val viewKey = "view="
  val pageKey = "page="
  val prevViewKey = "prevView="
}
import ViewConfigConstants._

object ViewConfigParser {
  import fastparse.all._
  import ParserElements._

  private def optionSeq[A](list: NonEmptyList[Option[A]]): Option[NonEmptyList[A]] = list.forall(_.isDefined) match {
    case true  => Some(list.map(_.get))
    case false => None
  }

  //TODO: support nested views with different operators and brackets.
  def viewWithOps(operator: ViewOperator): P[View] = P( word.!.rep(min = 1, sep = operator.separator) ~ (urlSeparator | End) )
    .map(_.toList)
    .flatMap {
      case Nil => ??? // cannot happen, because min of repetition is 1
      case view :: Nil =>
        View.viewMap.get(view)
          .fold[Parser[View]](Fail)(v => Pass.map(_ => v))
      case view :: views =>
        optionSeq(NonEmptyList(view, views).map(View.viewMap.get))
          .fold[Parser[View]](Fail)(v => Pass.map(_ => new TiledView(operator, v)))
    }

  val view: P[View] = P( (viewWithOps(ViewOperator.Row) | viewWithOps(ViewOperator.Column) | viewWithOps(ViewOperator.Auto) | viewWithOps(ViewOperator.Optional)) )

  val postIdList: Parser[Seq[PostId]] = P( word.!.rep(min = 1, sep = idSeparator) ).map(_.map(PostId.apply))
  val pageMode: Parser[PageMode] = P ( (PageMode.Default.name | PageMode.Orphans.name).! )
    .map {
      case PageMode.Default.name => PageMode.Default
      case PageMode.Orphans.name => PageMode.Orphans
    }
  val page: P[Page] = P( pageMode ~/ "[" ~/ (postIdList ~ (parentChildSeparator ~ postIdList).?).? ~ "]" ~/ (urlSeparator | End) )
    .map {
      case (mode, None) => Page(parentIds = Nil, mode = mode)
      case (mode, Some((parentIds, None))) => Page(parentIds = parentIds, mode = mode)
      case (mode, Some((parentIds, Some(childrenIds)))) => Page(parentIds = parentIds, childrenIds = childrenIds, mode = mode)
    }

  // TODO: marke order of values flexible
  val viewConfig: P[ViewConfig] = P( viewKey ~/ view ~/ pageKey ~/ page ~/ (prevViewKey ~/ view).? )
    .map { case (view, page, prevView) =>
      ViewConfig(view, page, prevView)
    }
}

object ViewConfigWriter {
  def write(cfg: ViewConfig): String = {
    val viewString = viewKey + cfg.view.key
    val pageString = pageKey + (cfg.page match {
      case Page(parentIds, childrenIds, mode) if parentIds.isEmpty && childrenIds.isEmpty => s"${mode.name}[]"
      case Page(parentIds, childrenIds, mode) if childrenIds.isEmpty => s"${mode.name}[${parentIds.mkString(idSeparator)}]"
      case Page(parentIds, childrenIds, mode) => s"${mode.name}[${parentIds.mkString(idSeparator)}$parentChildSeparator${childrenIds.mkString(idSeparator)}]"
    })
    val prevViewStringWithSep = cfg.prevView.fold("")(v => urlSeparator + prevViewKey + v.key)
    s"$viewString$urlSeparator$pageString$prevViewStringWithSep"
  }
}
