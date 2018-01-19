package wust.frontend.views

import wust.graph.Post

import scala.util.Random

object Heuristic {
  type PostHeuristic = (Set[Post], Int) => Set[Post]
  type TaskHeuristic = List[RestructuringTask] => RestructuringTask
}

case object ChooseTaskHeuristic {
  def random(tasks: List[RestructuringTask]): RestructuringTask = {
    tasks(scala.util.Random.nextInt(tasks.size))
  }

  def defaultHeuristic: Heuristic.TaskHeuristic = random
}

case object ChoosePostHeuristic {

  private[this] def wrapHeuristic(f: List[Post] => List[Post], posts: Set[Post], num: Int): Set[Post] = {
    assert(num <= posts.size, "Cannot pick more elements than there are")
    if(posts.isEmpty) return Set.empty[Post]

    val choice = f(posts.toList)
    choice.take(num).toSet
  }
  def random(posts: Set[Post], num: Int = 1): Set[Post] = {
    def _random(p: List[Post]) = scala.util.Random.shuffle(p)
    wrapHeuristic(_random, posts, num)
  }

  def newest(posts: Set[Post], num: Int = 1): Set[Post] = {
    def _newest(p: List[Post]) = p.sortWith((p1, p2) => p1.created.isBefore(p2.created))
    wrapHeuristic(_newest, posts, num)
  }

  def gaussTime(posts: Set[Post], num: Int = 2): Set[Post] = {
    def _gaussTime(posts: List[Post]) = {
      def clamp(value: Int, minValue: Int, maxValue: Int) = math.min(math.max(value, minValue), maxValue)
      val base = scala.util.Random.shuffle(posts).head

      val sortByTime: List[Post] = posts.sortWith((p1, p2) => p1.created.isBefore(p2.created))

      val baseIndex = sortByTime.indexOf(base)
      val pseudoStdDevPosts = 34.0 * sortByTime.length / 100

      def gauss = math.round(scala.util.Random.nextGaussian * pseudoStdDevPosts).toInt
      val gaussPosts = for(_ <- 0 to num) yield sortByTime(clamp(baseIndex + gauss, 0, sortByTime.length - 1))

      gaussPosts.toList
    }
    wrapHeuristic(_gaussTime, posts, num)


  }

  def defaultHeuristic: Heuristic.PostHeuristic = random
}
