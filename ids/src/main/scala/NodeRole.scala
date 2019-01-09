package wust.ids

import wust.util.macros.SubObjects

sealed trait NodeRole extends Product with Serializable
object NodeRole {
  case object Message extends NodeRole
  case object Property extends NodeRole
  case object Stage extends NodeRole { override def toString: String = "Stage (Column)" }
  case object Task extends NodeRole
  case object Tag extends NodeRole

  @inline def default:NodeRole = Message

  def all: List[NodeRole] = macro SubObjects.list[NodeRole]
}
