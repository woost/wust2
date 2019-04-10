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

  case class Child(deletedAt: Option[EpochMilli], ordering: Option[BigDecimal]) extends Named with EdgeData {
    override def toString: String = s"Child(${
      List(
        deletedAt.map(deletedAt => s"deletedAt=${deletedAt.humanReadable}"),
        ordering.map(ordering => s"ordering=$ordering")
      ).flatten.mkString(", ")
    })"
  }
  object Child extends Child(None, None) {
    def apply(timestamp: EpochMilli): Child = Child(Some(timestamp), None)
    def apply(ordering: BigDecimal): Child = Child(None, Some(ordering))
    def apply(timestamp: EpochMilli, ordering: BigDecimal): Child = Child(Some(timestamp), Some(ordering))
  }

  case object Automated extends Named with EdgeData
  case class DerivedFromTemplate(timestamp: EpochMilli) extends Named with EdgeData
  object DerivedFromTemplate extends Named

  sealed trait PropertyKey
  case object Assigned extends Named with EdgeData with PropertyKey

  case class LabeledProperty(key: String) extends Named with EdgeData with PropertyKey
  object LabeledProperty extends Named {
    def attachment = LabeledProperty("Attachment")
    def reference = LabeledProperty("Reference")
    def description = LabeledProperty("Description")
  }

  case object Notify extends Named with EdgeData
  case object Pinned extends Named with EdgeData
  case object Invite extends Named with EdgeData

  case class Expanded(isExpanded: Boolean) extends Named with EdgeData
}
