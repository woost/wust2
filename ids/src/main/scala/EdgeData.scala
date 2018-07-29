package wust.ids

import supertagged._

sealed trait EdgeData {
  val tpe: EdgeData.Type
}
object EdgeData {
  object Type extends TaggedType[String]
  type Type = Type.Type

  abstract class Named(implicit name: sourcecode.Name) {
    val tpe = Type(name.value)
  }

  // system convention
  case class Author(timestamp: EpochMilli) extends Named with EdgeData {
    override def toString = s"Author(${timestamp.humanReadable})"
  }
  object Author extends Named

  case class Member(level: AccessLevel) extends Named with EdgeData
  object Member extends Named

  case object Parent extends Named with EdgeData

  case class DeletedParent(timestamp: EpochMilli) extends Named with EdgeData {
    override def toString = s"DeletedParent(${timestamp.humanReadable})"
  }
  object DeletedParent extends Named

  case object Notify extends Named with EdgeData

  // content types
  case class Label(name: String) extends Named with EdgeData
  object Label extends Named

  // case class Number(content: String, weight: Double) extends Named with ConnectionData
  // object Number extends Named
}
