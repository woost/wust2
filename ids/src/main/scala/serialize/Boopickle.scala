package wust.ids.serialize

import wust.ids._
import boopickle.Default._
import supertagged._

trait Boopickle {

  implicit val CuidPickler: Pickler[Cuid] = generatePickler[Cuid]

  implicit def liftPicklerTagged[T, U](implicit f: Pickler[T]): Pickler[T @@ U] = f.asInstanceOf[Pickler[T @@ U]]
  implicit def liftPicklerOverTagged[R, T <: TaggedType[R], U](implicit f: Pickler[T#Type]): Pickler[T#Type @@ U] = f.asInstanceOf[Pickler[T#Type @@ U]]

  implicit val accessLevelPickler: Pickler[AccessLevel] = generatePickler[AccessLevel]
  implicit val joinDatePickler: Pickler[JoinDate] = generatePickler[JoinDate]

  implicit val postContentPickler: Pickler[NodeData] = generatePickler[NodeData]
  implicit val connectionContentPickler: Pickler[EdgeData] = generatePickler[EdgeData]
}
object Boopickle extends Boopickle

