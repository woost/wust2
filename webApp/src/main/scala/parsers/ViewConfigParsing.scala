package wust.webApp.parsers

import cats.data.NonEmptyList
import wust.graph.{Page, PageMode}
import wust.ids.{Cuid, NodeId}
import wust.webApp.state.{View, ViewConfig, ViewOperator, ShareOptions}

private object ViewConfigConstants {
  val pageSeparator = ":"
  val idSeparator = ","
  val urlSeparator = "&"
  val viewKey = "view="
  val pageKey = "page="
  val prevViewKey = "prevView="
  val shareKey = "share?"
}
import ViewConfigConstants._

object ViewConfigParser {
  import fastparse.all._
  import ParserElements._

  private def optionSeq[A](list: NonEmptyList[Option[A]]): Option[NonEmptyList[A]] =
    list.forall(_.isDefined) match {
      case true  => Some(list.map(_.get))
      case false => None
    }

  private val wordPart = (c: Char) => CharPredicates.isLetter(c) || CharPredicates.isDigit(c)

  //TODO: support nested views with different operators and brackets.
  def viewWithOps(operator: ViewOperator): P[View] =
    P(CharsWhile(wordPart).!.rep(min = 1, sep = operator.separator) ~ (urlSeparator | End))
      .map(_.toList)
      .flatMap {
        case Nil => ??? // cannot happen, because min of repetition is 1
        case view :: Nil =>
          View.map.get(view).fold[Parser[View]](Fail)(v => Pass.map(_ => v))
        case view :: views =>
          optionSeq(NonEmptyList(view, views).map(View.map.get))
            .fold[Parser[View]](Fail)(v => Pass.map(_ => View.Tiled(operator, v)))
      }

  val view: P[View] = P(
    viewWithOps(ViewOperator.Row) |
      viewWithOps(ViewOperator.Column) |
      viewWithOps(ViewOperator.Auto) |
      viewWithOps(ViewOperator.Optional))

  val nodeIdList: Parser[Seq[NodeId]] =
    P(CharsWhile(wordPart).!.rep(min = 1, sep = idSeparator)).map(_.map(cuid => NodeId(Cuid.fromBase58(cuid))))
  val pageMode: Parser[PageMode] =
    P((PageMode.Default.name | PageMode.Orphans.name).!)
      .map {
        case PageMode.Default.name => PageMode.Default
        case PageMode.Orphans.name => PageMode.Orphans
      }
  val page: P[Page] = P(
    pageMode ~/ (pageSeparator ~/ nodeIdList ~ (pageSeparator ~ nodeIdList).?).? ~/ (urlSeparator | End)
  ).map {
    case (mode, None) => Page(parentIds = Nil, mode = mode)
    case (mode, Some((parentIds, None))) =>
      Page(parentIds = parentIds, mode = mode)
    case (mode, Some((parentIds, Some(childrenIds)))) =>
      Page(parentIds = parentIds, childrenIds = childrenIds, mode = mode)
  }

  val shareOptions: P[ShareOptions] = P( "title=" ~/ CharsWhile(wordPart, min = 0).! ~/ urlSeparator ~/ "text=" ~/ CharsWhile(wordPart, min = 0).! ~/ urlSeparator ~/ "url=" ~/ (url.!).? ~/ (urlSeparator | End) )
      .map { case (title, text, url) =>
          ShareOptions(title, text, url.getOrElse(""))
      }

  // TODO: marke order of values flexible
  val viewConfig: P[ViewConfig] =
    P(viewKey ~/ view ~/ pageKey ~/ page ~/ (prevViewKey ~/ view).? ~/ (shareKey ~/ shareOptions).?)
      .map {
        case (view, page, prevView, shareOptions) =>
          ViewConfig(view, page, prevView, shareOptions)
      }
}

object ViewConfigWriter {
  def write(cfg: ViewConfig): String = {
    val viewString = viewKey + cfg.view.viewKey
    val pageString = pageKey + (cfg.page match {
      case Page(parentIds, childrenIds, mode) if parentIds.isEmpty && childrenIds.isEmpty =>
        s"${mode.name}"
      case Page(parentIds, childrenIds, mode) if childrenIds.isEmpty =>
        s"${mode.name}${pageSeparator}${parentIds.map(_.toBase58).mkString(idSeparator)}"
      case Page(parentIds, childrenIds, mode) =>
        s"${mode.name}${pageSeparator}${parentIds
          .map(_.toBase58)
          .mkString(idSeparator)}${pageSeparator}${childrenIds.map(_.toBase58).mkString(idSeparator)}"
    })
    val prevViewStringWithSep =
      cfg.prevView.fold("")(v => urlSeparator + prevViewKey + v.viewKey)
    s"$viewString$urlSeparator$pageString$prevViewStringWithSep"
  }
}
