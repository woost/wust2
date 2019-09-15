package wust.webApp.parsers

import acyclic.file
import cats.data.NonEmptyList
import kantan.regex._
import kantan.regex.implicits._
import wust.api.Authentication
import wust.graph.Page
import wust.ids.{Cuid, NodeId, View, ViewOperator, ViewName}
import wust.util.collection.BasicMap
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
  def update(config: UrlConfig, text: String): DecodeResult[UrlConfig]
}
private object UrlOption {

  object systemView extends UrlOption {
    val key = "system"

    private def decodeSystemView(s: String): DecodeResult[View.System] =
      View.map.get(s) match {
        case Some(view: View.System) => Right(view)
        case _               => Left(DecodeError.TypeError(s"Expected View.System, but got: '$view'"))
      }

    val regex = Regex[String](rx"^(\w/)+$$")
      .map(_.flatMap { viewStr =>
        decodeSystemView(viewStr)
      })

    def update(config: UrlConfig, text: String): DecodeResult[UrlConfig] =
      parseSingle(regex, text).map { view =>
        config.copy(systemView = Some(view))
      }
  }

  object view extends UrlOption {
    val key = "view"

    private def decodeViewName(s: String): DecodeResult[ViewName] =
      Right(ViewName(s))

    val regex = Regex[String](rx"^(\w/)+$$")
      .map(_.flatMap { viewStr =>
        decodeViewName(viewStr)
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
}

object UrlConfigParser {

  private val allOptionsRegex = Regex[(String,String)](rx"([^&=]+)=([^&]*)&?")
  private val allOptionsMap = Map(
    UrlOption.view.key -> UrlOption.view,
    UrlOption.systemView.key -> UrlOption.systemView,
    UrlOption.page.key -> UrlOption.page,
    UrlOption.invitation.key -> UrlOption.invitation,
    UrlOption.focusId.key -> UrlOption.focusId,
  )

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
    val viewString = cfg.view.map(view => UrlOption.view.key + "=" + view)
    val pageString = cfg.pageChange.page.parentId map { parentId =>
        UrlOption.page.key + "=" + s"${parentId.toBase58}"
    }
    val systemViewString =
      cfg.systemView.map(view => UrlOption.systemView.key + "=" + view)
    val hash = List(viewString, pageString, systemViewString).flatten.mkString("&")
    // we do not write invitation and focusId: this only reading on url change.
    UrlRoute(search = None, hash = Some(hash))
  }
  @inline def toUrlRoute(config: UrlConfig): UrlRoute = UrlConfigWriter.write(config)
}
