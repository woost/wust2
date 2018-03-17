package wust.graph

import wust.ids._

case class Page(parentIds: Seq[PostId],
childrenIds: Seq[PostId] = Nil)

object Page {
  val empty = new Page(Seq.empty)
  def apply(parentId:PostId):Page = Page(Seq(parentId))
}



