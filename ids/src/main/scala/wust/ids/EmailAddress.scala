package wust.ids

case class EmailAddress private(value: String) extends AnyVal
object EmailAddress {
  def apply(value: String): EmailAddress = new EmailAddress(value.toLowerCase)
}
