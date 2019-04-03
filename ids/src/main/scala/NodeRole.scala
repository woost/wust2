package wust.ids

import wust.util.macros.SubObjects

sealed trait NodeRole extends Product with Serializable
object NodeRole {
  sealed trait ContentRole extends NodeRole
  case object Message extends ContentRole
  case object Task extends ContentRole
  case object Note extends ContentRole

  case object Neutral extends NodeRole
  case object Stage extends NodeRole { override def toString: String = "Stage (Column)" }
  case object Tag extends NodeRole
  case object Project extends NodeRole

  @inline def default: NodeRole = Message

  def all: List[NodeRole] = macro SubObjects.list[NodeRole]

  def fromString(str: String): Option[NodeRole] = {
    val lowercaseStr = str.toLowerCase
    all.find(_.productPrefix.toLowerCase == lowercaseStr)
  }
}
