package wust.ids

sealed trait NodeRole
object NodeRole {
  case object Message extends NodeRole
  case object Task extends NodeRole

  @inline def default:NodeRole = Message
}
