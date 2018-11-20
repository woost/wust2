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

sealed trait NodeAccess {
  def str: String
}

object NodeAccess {

  case object Inherited extends NodeAccess {
    val str = "inherited"
  }

  case class Level(level: AccessLevel) extends NodeAccess {
    def str: String = level.str
  }

  val Restricted = Level(AccessLevel.Restricted)
  val ReadWrite = Level(AccessLevel.ReadWrite)

  val fromString: PartialFunction[String, NodeAccess] = AccessLevel.fromString.andThen[NodeAccess](Level(_)) orElse {
    case Inherited.str => Inherited
  }
}
