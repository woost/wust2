package wust.frontend.views

import wust.ids._
import wust.frontend.Client
import wust.graph.{Graph, Post}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

object Heuristic {
  type PostHeuristic = (Set[Post], Option[Int]) => List[Post]
  type GraphHeuristic = Graph => List[Post]
  type TaskHeuristic = List[RestructuringTaskObject] => RestructuringTaskObject
}

case object ChooseTaskHeuristic {
    def random(tasks: List[RestructuringTaskObject]): RestructuringTaskObject = {
      tasks(Random.nextInt(tasks.size))
    }

  def defaultHeuristic: Heuristic.TaskHeuristic = random
}

case object ChoosePostHeuristic {

  private[this] def wrapHeuristic(f: List[Post] => List[Post], posts: Set[Post], num: Option[Int]): List[Post] = {
    assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
    if(posts.isEmpty) return List.empty[Post]

    val choice = f(posts.toList)
    num match {
      case Some(n) => if(n > 0) choice.take(n) else choice.reverse.take(n)
      case None => choice
    }
  }

  private[this] def wrapFutureHeuristic(f: List[Post] => Future[List[Post]], posts: Set[Post], num: Option[Int]): Future[List[Post]] = {
    assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
    assert(posts.nonEmpty, "Cannot call a heuristic on an empty set")

    val choice = f(posts.toList)
    num match {
      case Some(n) => if(n > 0) choice.map(_.take(n)) else choice.map(_.reverse.take(n))
      case None => choice
    }
  }

  private[this] def wrapMultiHeuristic(f: List[Post] => List[List[Post]], posts: Set[Post], num: Option[Int]): List[List[Post]] = {
    assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
    if(posts.isEmpty) return List.empty[List[Post]]

    val choice = f(posts.toList)
    num match {
      case Some(n) => if(n > 0) choice.map(_.take(n)) else choice.map(_.reverse.take(n))
      case None => choice
    }
  }

  private[this] def wrapGraphHeuristic(f: Graph => List[Post], graph: Graph, num: Option[Int]): List[Post] = {
    assert(math.abs(num.getOrElse(0)) <= graph.length, "Cannot pick more elements than there are")
    if(graph.isEmpty) return List.empty[Post]

    val choice = f(graph)
    num match {
      case Some(n) => if(n > 0) choice.take(n) else choice.reverse.take(n)
      case None => choice
    }
  }
  def random(posts: Set[Post], num: Option[Int] = Some(2)): List[Post] = {
    def _random(p: List[Post]) = Random.shuffle(p)
    wrapHeuristic(_random, posts, num)
  }

  def newest(posts: Set[Post], num: Option[Int] = Some(2)): List[Post] = {
    def _newest(p: List[Post]) = p.sortWith((p1, p2) => p1.created.isBefore(p2.created))
    wrapHeuristic(_newest, posts, num)
  }

  def oldest(posts: Set[Post], num: Option[Int] = Some(2)): List[Post] = {
    def _oldest(p: List[Post]) = p.sortWith((p1, p2) => p1.created.isAfter(p2.created))
    wrapHeuristic(_oldest, posts, num)
  }

  def maxPostSize(posts: Set[Post], num: Option[Int] = Some(1)): List[Post] = { // for e.g. SplitPost
    def _maxPostSize(p: List[Post]) = p.sortWith((p1, p2) => p1.content.length > p2.content.length)
    wrapHeuristic(_maxPostSize, posts, num)
  }

  def minPostSize(posts: Set[Post], num: Option[Int] = Some(1)): List[Post] = {
    def _minPostSize(p: List[Post]) = p.sortWith((p1, p2) => p1.content.length < p2.content.length)
    wrapHeuristic(_minPostSize, posts, num)
  }

  def gaussTime(posts: Set[Post], num: Option[Int] = Some(2)): List[Post] = {
    def _gaussTime(posts: List[Post]) = {
      def clamp(value: Int, minValue: Int, maxValue: Int) = math.min(math.max(value, minValue), maxValue)
      val base = Random.shuffle(posts).head

      val sortByTime: List[Post] = posts.sortWith((p1, p2) => p1.created.isBefore(p2.created))

      val baseIndex = sortByTime.indexOf(base)
      val pseudoStdDevPosts = 34.0 * sortByTime.length / 100

      def gauss = math.round(Random.nextGaussian * pseudoStdDevPosts).toInt
      val gaussPosts = for(_ <- 0 to num.getOrElse(posts.size)) yield sortByTime(clamp(baseIndex + gauss, 0, sortByTime.length - 1))

      gaussPosts.toList
    }
    wrapHeuristic(_gaussTime, posts, num)
  }

  def nodeDegree(graph: Graph, num: Option[Int] = Some(2)): List[Post] = {
    def _nodeDegree(graph: Graph) = graph.connectionDegree.toList.sortWith((p1, p2) => (p1._2 < p2._2)).map(p => graph.postsById(p._1))
    wrapGraphHeuristic(_nodeDegree, graph, num)
  }

  def complicatedHeuristic(posts: Set[Post], num: Option[Int] = Some(2)): Future[List[Post]] = {
    def _complicatedHeuristic(posts: List[Post]) = {
      val postMap: Map[PostId, Post] = posts.map(p => p.id -> p).toMap
      Client.api.chooseTaskPost(posts.map(_.id)).map(_.map(postMap).toList)
    }
    wrapFutureHeuristic(_complicatedHeuristic, posts, num)
  }

  def defaultHeuristic: Heuristic.PostHeuristic = random
}
