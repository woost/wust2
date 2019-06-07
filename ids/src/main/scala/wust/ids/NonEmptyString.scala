package wust.ids

class NonEmptyString private(val string: String) extends AnyVal {
  override def toString = string
}
object NonEmptyString {
  def apply(string: String): Option[NonEmptyString] = if (string.isEmpty) None else Some(new NonEmptyString(string))
}
