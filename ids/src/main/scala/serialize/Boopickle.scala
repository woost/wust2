package wust.ids.serialize

import wust.ids._
import boopickle.Default._
import io.treev.tag._

trait Boopickle {

  private def picklerTaggedType[T: Pickler, Type <: TaggedType[T]#Type]: Pickler[Type] = transformPickler[Type, T](_.asInstanceOf[Type])(identity)

  // cannot resolve automatically for any T, so need specialized implicit defs
  implicit def picklerStringTaggedType[Type <: TaggedType[String]#Type]: Pickler[Type] = picklerTaggedType[String, Type]
  implicit def picklerLongTaggedType[Type <: TaggedType[Long]#Type]: Pickler[Type] = picklerTaggedType[Long, Type]
  implicit def picklerIntTaggedType[Type <: TaggedType[Int]#Type]: Pickler[Type] = picklerTaggedType[Int, Type]

  implicit val accessLevelPickler: Pickler[AccessLevel] = generatePickler[AccessLevel]
  implicit val joinDatePickler: Pickler[JoinDate] = generatePickler[JoinDate]

  implicit val postContentPickler: Pickler[PostContent] = generatePickler[PostContent]
  implicit val connectionContentPickler: Pickler[ConnectionContent] = generatePickler[ConnectionContent]
}
object Boopickle extends Boopickle

