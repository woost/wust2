package wust.graph

import wust.ids._

sealed trait PageMode { val name = toString.toLowerCase }
object PageMode {
  case object Default extends PageMode
  case object Orphans extends PageMode
}
case class Page(parentIds: Seq[NodeId], childrenIds: Seq[NodeId] = Nil, mode: PageMode = PageMode.Default)

object Page {
  val empty = Page(Seq.empty)
  def ofUser(user: UserInfo) = Page(Seq(user.channelNodeId))
  def apply(parentId:NodeId):Page = Page(Seq(parentId))
}
