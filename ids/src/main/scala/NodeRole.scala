package wust.ids

import wust.util.macros.SubObjects

sealed trait NodeRole
object NodeRole {
  case object Message extends NodeRole
  case object Task extends NodeRole
  case object Stage extends NodeRole

  @inline def default:NodeRole = Message

  def all: List[NodeRole] = macro SubObjects.list[NodeRole]
}
