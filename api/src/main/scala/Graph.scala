package api

import pharg._

package object graph {
  type Id = Long
  type Graph = DirectedGraphData[Long, Post, Connects]

  case class Post(title: String)
  case class Connects(classification: String)

  // DirectedGraphData[Long, Post, Connects](Set(1,2), Set(Edge(1,2)), Map(1 -> Post("bala")), Map(Edge(1,2) -> Connects(...)))
}
