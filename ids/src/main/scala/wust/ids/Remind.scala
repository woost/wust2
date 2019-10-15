package wust.ids

sealed trait RemindCondition
object RemindCondition {
  sealed trait Date
  object Date {
    case class Absolute(time: EpochMilli) extends Date
    case class Relative(propertyKey: String, offset: DurationMilli) extends Date
  }
  // sealed trait PropertyFill
  // object PropertyFill {
  //   case object All extends PropertyFill
  //   case object Any extends PropertyFill
  //   case class AllOf(propertyKeys: List[String]) extends PropertyFill
  //   case class AnyOf(propertyKeys: List[String]) extends PropertyFill
  // }

  case class AtDate(date: Date) extends RemindCondition
  // case class OnPropertyFill(propertyFill: PropertyFill) extends RemindCondition
  // case class InStage(nodeId: NodeId) extends RemindCondition
}

sealed trait RemindMedium
object RemindMedium {
  case object Email extends RemindMedium
  case object Push extends RemindMedium

  val asString: RemindMedium => String = {
    case RemindMedium.Email => "email"
    case RemindMedium.Push => "push"
  }

  val fromString: PartialFunction[String, RemindMedium] = {
    case "email" => RemindMedium.Email
    case "push" => RemindMedium.Push
  }
}

sealed trait RemindTarget
object RemindTarget {
  case object User extends RemindTarget
  case object Assignee extends RemindTarget

  val asString: RemindTarget => String = {
    case RemindTarget.User => "user"
    case RemindTarget.Assignee => "assignee"
  }

  val fromString: PartialFunction[String, RemindTarget] = {
    case "user" => RemindTarget.User
    case "assignee" => RemindTarget.Assignee
  }
}
