package wust.backend

import wust.graph.Post
import wust.ids.PostId

import scala.util.Random

case object PostHeuristics {
  private[this] def wrapHeuristic(f: List[PostId] => List[PostId], posts: List[PostId], num: Option[Int]): List[PostId] = {
    assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
    if(posts.isEmpty) return List.empty[PostId]

    val choice = f(posts)
    num match {
      case Some(n) => choice.take(n)
      case None => choice
    }
  }

  def random(posts: List[PostId], num: Option[Int] = Some(1)): List[PostId] = {
    def _random(p: List[PostId]) = Random.shuffle(p)
    wrapHeuristic(_random, posts, num)
  }

}
