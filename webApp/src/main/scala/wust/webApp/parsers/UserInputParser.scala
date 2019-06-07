package wust.webApp.parsers

object UserInputParser {

  implicit class RichString(val input: String) extends AnyVal {
    def commaSeparatedToSeq[A](f: String => A): Seq[A] = {
      input.split(",").toSeq.map(_.trim).map(s => f(s))
    }
    def commaSeparatedSeq: Seq[String] = {
      commaSeparatedToSeq(identity)
    }
  }

}
