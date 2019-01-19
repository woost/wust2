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

  case class Parent(deletedAt: Option[EpochMilli], ordering: Option[BigDecimal]) extends Named with EdgeData {
    override def toString: String = s"Parent(${
      List(
        deletedAt.map(deletedAt => s"deletedAt=${deletedAt.humanReadable}"),
        ordering.map(ordering => s"ordering=$ordering")
      ).flatten.mkString(", ")
    })"
  }
  object Parent extends Parent(None, None) {
    def apply(timestamp: EpochMilli): Parent = Parent(Some(timestamp), None)
    def apply(ordering: BigDecimal): Parent = Parent(None, Some(ordering))
    def apply(timestamp: EpochMilli, ordering: BigDecimal): Parent = Parent(Some(timestamp), Some(ordering))
  }

  case object Notify extends Named with EdgeData

  // content types
  case object Pinned extends Named with EdgeData

  case object Invite extends Named with EdgeData

  case object Expanded extends Named with EdgeData

  case object Automated extends Named with EdgeData
  case class DerivedFromTemplate(timestamp: EpochMilli) extends Named with EdgeData
  object DerivedFromTemplate extends Named

  sealed trait PropertyKey
  case object Assigned extends Named with EdgeData with PropertyKey

  case class LabeledProperty(key: String) extends Named with EdgeData with PropertyKey
  object LabeledProperty extends Named

}
