package wust.graph

import wust.ids._

case class Page(parentIds: Seq[PostId], childrenIds: Seq[PostId] = Nil)

object Page {
  val empty = Page(Seq.empty)
  def ofUser(user: User) = Page(Seq(user.channelPostId))
  def apply(parentId:PostId):Page = Page(Seq(parentId))
}



