package wust.webApp.parsers

import java.net.URL

import scala.util.Try

object ParserElements {
  import fastparse.all._

  val word: P[String] = P( ElemsWhileIn(('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9'), min = 1).! )

  val whitespaceChar = CharPred(_.isWhitespace)

  //TODO better?
  val url = P( whitespaceChar.rep ~ ("http://" | "https://").! ~ CharPred(s => !s.isWhitespace).rep(min = 1).! )
    .flatMap { case (proto, rest) =>
      val url = proto + rest
      url
    }
}
