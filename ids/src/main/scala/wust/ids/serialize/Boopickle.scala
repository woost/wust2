package wust.ids.serialize

import boopickle.Default._
import wust.ids._

trait Boopickle {

  implicit val CuidPickler: Pickler[Cuid] = generatePickler[Cuid]
  implicit val NodeIdPickler: Pickler[NodeId] = implicitly[Pickler[Cuid]].asInstanceOf[Pickler[NodeId]]
  implicit val UserIdPickler: Pickler[UserId] = implicitly[Pickler[Cuid]].asInstanceOf[Pickler[UserId]]
  implicit val TemplateIdPickler: Pickler[TemplateId] = implicitly[Pickler[Cuid]].asInstanceOf[Pickler[TemplateId]]
  implicit val ParentIdPickler: Pickler[ParentId] = implicitly[Pickler[Cuid]].asInstanceOf[Pickler[ParentId]]
  implicit val ChildIdPickler: Pickler[ChildId] = implicitly[Pickler[Cuid]].asInstanceOf[Pickler[ChildId]]
  implicit val PropertyIdPickler: Pickler[PropertyId] = implicitly[Pickler[Cuid]].asInstanceOf[Pickler[PropertyId]]

  implicit val EpochMilliPickler: Pickler[EpochMilli] = implicitly[Pickler[Long]].asInstanceOf[Pickler[EpochMilli]]
  implicit val DateTimeMilliPickler: Pickler[DateTimeMilli] = implicitly[Pickler[Long]].asInstanceOf[Pickler[DateTimeMilli]]
  implicit val DateMilliPickler: Pickler[DateMilli] = implicitly[Pickler[Long]].asInstanceOf[Pickler[DateMilli]]
  implicit val DurationMilliPickler: Pickler[DurationMilli] = implicitly[Pickler[Long]].asInstanceOf[Pickler[DurationMilli]]

  implicit val accessLevelPickler: Pickler[AccessLevel] = generatePickler[AccessLevel]

  implicit val NodeTypeSelectionPickler: Pickler[NodeTypeSelection] = generatePickler[NodeTypeSelection]
  implicit val NodeDataTypePickler: Pickler[NodeData.Type] = implicitly[Pickler[String]].asInstanceOf[Pickler[NodeData.Type]]
  implicit val NodeDataPickler: Pickler[NodeData] = generatePickler[NodeData]
  implicit val EdgeDataTypePickler: Pickler[EdgeData.Type] = implicitly[Pickler[String]].asInstanceOf[Pickler[EdgeData.Type]]
  implicit val edgeDataPickler: Pickler[EdgeData] = generatePickler[EdgeData]

  implicit val viewPickler: Pickler[View] = generatePickler[View]

  implicit val featurePickler: Pickler[Feature] = generatePickler[Feature]
}
object Boopickle extends Boopickle
