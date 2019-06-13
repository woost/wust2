package wust.ids

import supertagged._

sealed trait EdgeData {
  val tpe: EdgeData.Type

  @inline def as[T <: EdgeData]: T = asInstanceOf[T]
}
object EdgeData {
  object Type extends TaggedType[String]
  type Type = Type.Type

  abstract class Named(implicit name: sourcecode.Name) {
    val tpe = Type(name.value)
  }

  // system convention
  final case class Author(timestamp: EpochMilli) extends Named with EdgeData {
    override def toString = s"Author(${timestamp.humanReadable})"
  }
  object Author extends Named

  final case class Member(level: AccessLevel) extends Named with EdgeData
  object Member extends Named

  final case class Child(deletedAt: Option[EpochMilli], ordering: BigDecimal) extends Named with EdgeData {
    override def toString: String = s"Child(${deletedAt.map(_.humanReadable)}, $ordering)"
  }
  object Child extends Named {
    @inline def apply(ordering: BigDecimal): Child = Child(None, ordering)
    @inline def apply(timestamp: EpochMilli, ordering: BigDecimal): Child = Child(Some(timestamp), ordering)
  }

  case object Automated extends Named with EdgeData
  final case class DerivedFromTemplate(timestamp: EpochMilli) extends Named with EdgeData
  object DerivedFromTemplate extends Named

  sealed trait PropertyKey
  case object Assigned extends Named with EdgeData with PropertyKey

  case class LabeledProperty(key: String) extends Named with EdgeData with PropertyKey
  object LabeledProperty extends Named {
    def attachment = LabeledProperty("Attachment")
    def reference = LabeledProperty("Reference")
    def description = LabeledProperty("Description")
    def dueDate = LabeledProperty("Due Date")
  }

  final case class Read(timestamp: EpochMilli) extends Named with EdgeData

  case object Notify extends Named with EdgeData
  case object Pinned extends Named with EdgeData
  case object Invite extends Named with EdgeData

  final case class Expanded(isExpanded: Boolean) extends Named with EdgeData
}
