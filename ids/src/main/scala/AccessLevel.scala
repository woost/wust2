package wust.ids

sealed trait AccessLevel {
  def str: String
}
object AccessLevel {
  case object Read extends AccessLevel
  {
    def str = "read"
  }
  case object ReadWrite extends AccessLevel
  {
    def str = "readwrite"
  }

  val from: PartialFunction[String, AccessLevel] = {
    case "read" => Read
    case "readwrite" => ReadWrite
  }
}
