package wust.graph

import wust.ids._

sealed trait PageMode { val name = toString.toLowerCase }
object PageMode {
  case object Default extends PageMode
  case object Orphans extends PageMode
}
case class Page(parentIds: Seq[PostId], childrenIds: Seq[PostId] = Nil, mode: PageMode = PageMode.Default)

object Page {
  val empty = Page(Seq.empty)
  def ofUser(user: User) = Page(Seq(user.channelPostId))
  def apply(parentId:PostId):Page = Page(Seq(parentId))
}




