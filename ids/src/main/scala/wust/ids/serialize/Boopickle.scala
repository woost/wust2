package wust.ids.serialize

import boopickle.Default._
import supertagged._
import wust.ids._

trait Boopickle {

  implicit val CuidPickler: Pickler[Cuid] = generatePickler[Cuid]

  implicit def liftPicklerTagged[T, U](implicit f: Pickler[T]): Pickler[T @@ U] =
    f.asInstanceOf[Pickler[T @@ U]]
  implicit def liftPicklerOverTagged[R, T <: TaggedType[R], U](
      implicit f: Pickler[T#Type]
  ): Pickler[T#Type @@ U] = f.asInstanceOf[Pickler[T#Type @@ U]]

  implicit val EmailAddressPickler: Pickler[EmailAddress] = stringPickler.xmap(EmailAddress(_))(_.value)

  implicit val accessLevelPickler: Pickler[AccessLevel] = generatePickler[AccessLevel]

  implicit val NodeTypeSelectionPickler: Pickler[NodeTypeSelection] = generatePickler[NodeTypeSelection]
  implicit val postContentPickler: Pickler[NodeData] = generatePickler[NodeData]
  implicit val connectionContentPickler: Pickler[EdgeData] = generatePickler[EdgeData]

  implicit val viewPickler: Pickler[View] = generatePickler[View]

  implicit val featurePickler: Pickler[Feature] = generatePickler[Feature]
}
object Boopickle extends Boopickle
