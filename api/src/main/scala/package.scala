package wust

import boopickle.Default._
import scalaz.Tag
import wust.ids._

package object api {
  implicit def PostIdPickler = transformPickler[PostId, IdType](PostId _)(Tag.unwrap _)
  implicit def GroupIdPickler = transformPickler[GroupId, IdType](GroupId _)(Tag.unwrap _)
  implicit def UserIdPickler = transformPickler[UserId, IdType](UserId _)(Tag.unwrap _)
}
