package wust.ids

import com.sun.org.apache.xerces.internal.xs.datatypes.ObjectList
import wust.util.macros.SubObjects

sealed trait NodeRole
object NodeRole {
  case object Message extends NodeRole
  case object Info extends NodeRole
  case object Task extends NodeRole

  @inline def default:NodeRole = Message

  def all: List[NodeRole] = macro SubObjects.list[NodeRole]
}
