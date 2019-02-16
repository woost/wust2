package wust.webApp.parsers

import cats.data.NonEmptyList
import wust.graph.Page
import wust.ids.{Cuid, NodeId, View, ViewOperator}
import wust.webApp.state._
import kantan.regex._
import kantan.regex.implicits._
import kantan.regex.generic._

private object ParsingHelpers {
  def decodeSeq[A](list: Seq[DecodeResult[A]]): DecodeResult[Seq[A]] =
    list.forall(_.isRight) match {
      case true  => Right(list.map(_.right.get))
      case false => Left(DecodeError.TypeError("Multiple errors occurred: " + list.collect { case Left(v) => v }.mkString(",")))
    }

  def parseSingle[A](r: Regex[DecodeResult[A]], text: String): DecodeResult[A] = {
    val results = r.eval(text).toList
    if (results.size == 1) results.head
    else if (results.isEmpty) Left(DecodeError.TypeError("No results, but one expected"))
    else Left(DecodeError.TypeError("Multiple results, but only one expected: " + results.mkString(",")))
  }
}
import ParsingHelpers._

private sealed trait UrlOption {
  def update(config: UrlConfig, text: String): DecodeResult[UrlConfig]
}
private object UrlOption {

  object view extends UrlOption {
    val key = "view"

    private def decodeVisibleView(s: String): DecodeResult[View.Visible] =
      decodeView(s).flatMap {
        case view: View.Visible => Right(view)
        case view => Left(DecodeError.TypeError(s"Expected View.Visible, but got: '$view'"))
      }

    private def decodeView(s: String): DecodeResult[View] =
      View.map.get(s).fold[DecodeResult[View]](Left(DecodeError.TypeError(s"Unknown view '$s")))(Right(_))

    val regex = Regex[(String, Option[String])](rx"^(\w+)((\||,|\?|/)\w+)*?$$")
      .map(_.flatMap { case (view, opsViews) =>
        opsViews.fold(decodeView(view)) { opsViews =>
          val opString = opsViews.head.toString
          val views = opsViews.split("\\||,|\\?|/").filter(_.nonEmpty)
          ViewOperator.fromString.lift(opString) match {
            case Some(op) =>
              decodeVisibleView(view).flatMap { view =>
                decodeSeq(views.map(decodeVisibleView)).map { views =>
                  View.Tiled(op, NonEmptyList(view, views.toList))
                }
              }
            case None => Left(DecodeError.TypeError(s"Unknown operator '$opString'"))
          }
        }
      })

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { view =>
        config.copy(view = Some(view))
      }
  }

  object page extends UrlOption {
    val key = "page"

      val regex = Regex[String](rx"^(\w+)$$")
      .map(_.map { case parentId =>
          Page(NodeId(Cuid.fromBase58(parentId)))
      })

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { page =>
        config.copy(pageChange = PageChange(page))
      }
  }

  object redirectTo extends UrlOption {
    val key = "redirectTo"

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(view.regex, text).map { view =>
        config.copy(redirectTo = Some(view))
      }
  }

  object share extends UrlOption {
    val key = "share"

    val regex = Regex[ShareOptions](rx"^title:([^,]*),text:([^:]*),url:(.*)$$")

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { shareOptions =>
        config.copy(shareOptions = Some(shareOptions))
      }
  }

  object invitation extends UrlOption {
    val key = "invitation"

    val regex = Regex[String](rx"^(.+)$$")

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { invitation =>
        config.copy(invitation = Some(invitation))
      }
  }
}

object UrlConfigParser {

  private val allOptionsRegex = Regex[(String,String)](rx"([^&=]+)=([^&]*)&?")
  private val allOptionsMap = Map(
    UrlOption.view.key -> UrlOption.view,
    UrlOption.page.key -> UrlOption.page,
    UrlOption.redirectTo.key -> UrlOption.redirectTo,
    UrlOption.share.key -> UrlOption.share,
    UrlOption.invitation.key -> UrlOption.invitation,
  )

  def parse(text: String): UrlConfig = {
    val matched = decodeSeq(allOptionsRegex.eval(text).toList)
    val result = matched.map(_.foldLeft[UrlConfig](UrlConfig.default) { case (cfg, (key, value)) =>
      allOptionsMap.get(key) match {
        case Some(option) => option.update(cfg, value) match {
          case Right(cfg) => cfg
          case Left(err)  =>
            scribe.warn(s"Cannot parse option '$key' in url, will be ignored: ${ err.getMessage }")
            cfg
        }
        case None         =>
          scribe.warn(s"Unknown key '$key' in url, will be ignored.")
          cfg
      }
    })

    result match {
      case Right(cfg) => cfg
      case Left(err)  =>
        scribe.warn(s"Cannot parse url, falling back to default view config: ${ err.getMessage }")
        UrlConfig.default
    }
  }
}

object UrlConfigWriter {
  def write(cfg: UrlConfig): String = {
    val viewString = cfg.view.map(view => UrlOption.view.key + "=" + view.viewKey)
    val pageString = cfg.pageChange.page.parentId map { parentId =>
        UrlOption.page.key + "=" + s"${parentId.toBase58}"
    }
    val redirectToString =
      cfg.redirectTo.map(view => UrlOption.redirectTo.key + "=" + view.viewKey)
    List(viewString, pageString, redirectToString).flatten.mkString("&")
  }
}