package wust.ids

sealed trait AccessLevel {
  def str: String
}

object AccessLevel {
  case object Restricted extends AccessLevel {
    val str = "restricted"
  }

  case object ReadWrite extends AccessLevel {
    val str = "readwrite"
  }

  val fromString: PartialFunction[String, AccessLevel] = {
    case Restricted.str => Restricted
    case ReadWrite.str  => ReadWrite
  }
}

sealed trait NodeAccess

object NodeAccess {

  case object Inherited extends NodeAccess

  case class Level(level: AccessLevel) extends NodeAccess
}
