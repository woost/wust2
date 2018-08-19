package wust.webApp.parsers

object ParserElements {
  import fastparse.all._

  val maybeWhitespaces = CharsWhileIn(" ", min = 0)

  //TODO better?
  val url = P((("http://" | "https://") ~/ CharsWhile(_ != ' ')).!)
}
