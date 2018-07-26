package wust.webApp.parsers

import java.net.URL

import scala.util.Try
import fastparse.CharPredicates._
import fastparse.utils.{MacroUtils, Utils}

object ParserElements {
  import fastparse.all._

  val maybeWhitespaces = CharsWhileIn(" ", min = 0)

  //TODO better?
  val url = P((("http://" | "https://") ~/ CharsWhile(_ != ' ')).!)
}
