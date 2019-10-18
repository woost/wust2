package wust.webApp.parsers

import acyclic.file
import cats.data.NonEmptyList
import kantan.regex._
import kantan.regex.implicits._
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{Cuid, NodeId, View, ViewOperator}
import wust.util.collection._
import wust.webApp.state._

import scala.scalajs.js

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
import wust.webApp.parsers.ParsingHelpers._

private sealed trait UrlOption {
  def key: String
  def update(config: UrlConfig, text: String): DecodeResult[UrlConfig]
}
private object UrlOption {

  object view extends UrlOption {
    val key = "view"

    private def decodeVisibleView(s: String): DecodeResult[View.Visible] =
      decodeView(s).flatMap {
        case view: View.Visible => Right(view)
        case view               => Left(DecodeError.TypeError(s"Expected View.Visible, but got: '$view'"))
      }

    private def decodeView(s: String): DecodeResult[View] = {
      val split = s.split(":")
      val viewName = split(0)
      val parameters = split.drop(1).toList
      val result = View.map.get(viewName).flatMap(_ (parameters))
      result.fold[DecodeResult[View]](Left(DecodeError.TypeError(s"Unknown view '$s")))(Right(_))
    }

    val regex = Regex[String](rx"^(\w|\:|\||,|\?|/|\+)+$$")
      .map(_.flatMap { viewStr =>
        val views = viewStr.split("\\||,|\\?|/|\\+").filter(_.nonEmpty)
        val view = views.head
        val opsViews = views.drop(1)
        if(opsViews.isEmpty) decodeView(view)
        else {
          val opString = viewStr.substring(view.length, view.length + 1) // TODO only support one operator. nested tiling?
          ViewOperator.fromString.lift(opString) match {
            case Some(op) =>
              decodeVisibleView(view).flatMap { view =>
                decodeSeq(opsViews.map(decodeVisibleView)).map { views =>
                  View.Tiled(op, NonEmptyList(view, views.toList))
                }
              }
            case None     => Left(DecodeError.TypeError(s"Unknown operator '$opString'"))
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
      .map(_.flatMap { parentId =>
        Cuid.fromBase58String(parentId).map(id => Page(NodeId(id)))
          .left.map(DecodeError.TypeError(_))
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

  object invitation extends UrlOption {
    val key = "invitation"

    val regex = Regex[String](rx"^(.+)$$")

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { invitation =>
        config.copy(invitation = Some(Authentication.Token(invitation)))
      }
  }

  object focusId extends UrlOption {
    val key = "focus"

    val regex = Regex[String](rx"^(\w+)$$")
      .map(_.flatMap { focusId =>
        Cuid.fromBase58String(focusId).map(id => NodeId(id))
          .left.map(DecodeError.TypeError(_))
      })

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { focusId =>
        config.copy(focusId = Some(focusId))
      }
  }

  object presentationMode extends UrlOption {
    val key = "presentation"

    val regex = Regex[String](rx"^(.+)$$")

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).flatMap { name =>
        PresentationMode.fromString(name)
          .map(mode => config.copy(mode = mode))
          .toRight(DecodeError.TypeError(s"Not a valid presentation mode: $text"))
      }
  }

  object infoContent extends UrlOption {
    val key = "info"

    val regex = Regex[String](rx"^(.+)$$")

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).flatMap { name =>
        InfoContent.fromString(name)
          .map(info => config.copy(info = Some(info)))
          .toRight(DecodeError.TypeError(s"Not a valid info content: $text"))
      }
  }
}

object UrlConfigParser {

  private val allOptionsRegex = Regex[(String,String)](rx"([^&=]+)=([^&]*)&?")
  private val allOptionsMap = List(
    UrlOption.view,
    UrlOption.page,
    UrlOption.redirectTo,
    UrlOption.invitation,
    UrlOption.focusId,
    UrlOption.presentationMode,
    UrlOption.infoContent
  ).by(_.key)

  def parse(route: UrlRoute): UrlConfig = {
    val searchOptions = BasicMap.ofString[String]()
    route.search.foreach { search =>
      decodeSeq(allOptionsRegex.eval(search).toSeq).foreach { res =>
        res.foreach { case (key, value) =>
          searchOptions += key -> js.URIUtils.decodeURIComponent(value)
        }
      }
    }

    val result = route.hash.fold[DecodeResult[UrlConfig]](Right(UrlConfig.default)) { hash =>
      val matched = decodeSeq(allOptionsRegex.eval(hash).toList)
      matched.map(_.foldLeft[UrlConfig](UrlConfig.default) { case (cfg, (key, value)) =>
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
    }

    result match {
      case Right(cfg) => {
        //Keep in sync with site.webmanifest where mapping of share url is defined
        (searchOptions.get("share-title"), searchOptions.get("share-text"), searchOptions.get("share-url")) match {
          case (Some(title), text, urlOption) => cfg.copy(shareOptions = Some(ShareOptions(title = title, text = text.getOrElse(""), url = urlOption.getOrElse(""))))
          case _ => cfg
        }
      }
      case Left(err)  =>
        scribe.warn(s"Cannot parse url, falling back to default view config: ${ err.getMessage }")
        UrlConfig.default
    }
  }
  def fromUrlRoute(route: UrlRoute): UrlConfig = UrlConfigParser.parse(route)
}

object UrlConfigWriter {
  def write(cfg: UrlConfig): UrlRoute = {
    val viewString = cfg.view.map(view => UrlOption.view.key + "=" + view.viewKey)
    val pageString = cfg.pageChange.page.parentId map { parentId =>
        UrlOption.page.key + "=" + s"${parentId.toBase58}"
    }
    val redirectToString = cfg.redirectTo.map(view => UrlOption.redirectTo.key + "=" + view.viewKey)
    val mode = PresentationMode.toString(cfg.mode).map(m => UrlOption.presentationMode.key + "=" + m)
    val hash = List(viewString, pageString, redirectToString, mode).flatten.mkString("&")
    // we do not write invitation and focusId: this only reading on url change.
    UrlRoute(search = None, hash = Some(hash))
  }
  def urlRouteToString(route: UrlRoute): String = {
    val search = route.search.getOrElse("/") // not empty string, otherwise the old search part is not replaced in the url.
    val hash = route.hash.fold("")("#" + _)
    search + hash
  }
  @inline def toUrlRoute(config: UrlConfig): UrlRoute = UrlConfigWriter.write(config)
  @inline def toString(config: UrlConfig): String = urlRouteToString(toUrlRoute(config))
}
