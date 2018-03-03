package wust.webApp

import wust.api._
import wust.ids._
import wust.webApp.PostHeuristic._
import wust.webApp.Restructure._
import wust.graph.{Graph, Post}
import wust.utilWeb._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import wust.ids.EpochMilli._

case object ChooseTaskHeuristic {
    type HeuristicType = List[RestructuringTaskObject] => RestructuringTaskObject

    def random(tasks: List[RestructuringTaskObject]): RestructuringTaskObject = {
      tasks(scala.util.Random.nextInt(tasks.size))
    }

  def defaultHeuristic: HeuristicType = random
}

sealed trait PostHeuristic
{
  def heuristic(graph: Graph, num:Option[Int]): PostHeuristicResult
}

object PostHeuristic {
  type PostHeuristicResult = Future[List[Heuristic.Result]]
  type PostHeuristicType = (Graph, Option[Int]) => PostHeuristicResult


  sealed trait FrontendHeuristic extends PostHeuristic {
    def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult = {
      Future.successful(List(frontendHeuristic(graph.posts.toList, num)).map(l => Heuristic.PostResult(None, l)))
    }
    def frontendHeuristic(posts: List[Post], num: Option[Int]): List[Post]

    protected def wrapHeuristic(f: List[Post] => List[Post], posts: List[Post], num: Option[Int]): List[Post] = {
      assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
      if (posts.isEmpty) return List.empty[Post]

      val choice = f(posts)
      num match {
        case Some(n) => if (n > 0) choice.take(n) else choice.reverse.take(math.abs(n))
        case None => choice
      }
    }

    protected def wrapMultiHeuristic(f: List[Post] => List[List[Post]], posts: Set[Post], num: Option[Int]): List[List[Post]] = {
      assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
      if (posts.isEmpty) return List.empty[List[Post]]

      val choice = f(posts.toList)
      num match {
        case Some(n) => if (n > 0) choice.map(_.take(n)) else choice.map(_.reverse.take(math.abs(n)))
        case None => choice
      }
    }
  }

  case object Deterministic {
    def apply(deterministicPosts: List[Post]) = new Deterministic(deterministicPosts)
  }
  case class Deterministic(deterministicPosts: List[Post]) extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(deterministicPosts.length)): List[Post] = {
      wrapHeuristic(deterministicPosts => deterministicPosts, posts, num)
    }
  }

  case object Random extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(2)): List[Post] = {
      def _random(p: List[Post]) = scala.util.Random.shuffle(p)

      wrapHeuristic(_random, posts, num)
    }
  }

  case object Newest extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(2)): List[Post] = {
      def _newest(p: List[Post]) = p.sortWith((p1, p2) => p1.created.isBefore(p2.created))

      wrapHeuristic(_newest, posts, num)
    }
  }

  case object Oldest extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(2)): List[Post] = {
      def _oldest(p: List[Post]) = p.sortWith((p1, p2) => p1.created.isAfter(p2.created))

      wrapHeuristic(_oldest, posts, num)
    }
  }

  case object MaxPostSize extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(1)): List[Post] = { // for e.g. SplitPost
      def _maxPostSize(p: List[Post]) = p.sortWith((p1, p2) => p1.content.length > p2.content.length)

      wrapHeuristic(_maxPostSize, posts, num)
    }
  }

  case object MinPostSize extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(1)): List[Post] = {
      def _minPostSize(p: List[Post]) = p.sortWith((p1, p2) => p1.content.length < p2.content.length)

      wrapHeuristic(_minPostSize, posts, num)
    }
  }

  case object GaussTime extends FrontendHeuristic {
    def frontendHeuristic(posts: List[Post], num: Option[Int] = Some(2)): List[Post] = {
      def _gaussTime(posts: List[Post]) = {
        def clamp(value: Int, minValue: Int, maxValue: Int) = math.min(math.max(value, minValue), maxValue)

        val base = scala.util.Random.shuffle(posts).head

        val sortByTime: List[Post] = posts.sortWith((p1, p2) => p1.created.isBefore(p2.created))

        val baseIndex = sortByTime.indexOf(base)
        val pseudoStdDevPosts = 34.0 * sortByTime.length / 100

        def gauss = math.round(scala.util.Random.nextGaussian * pseudoStdDevPosts).toInt

        val gaussPosts = for (_ <- 0 to num.getOrElse(posts.size)) yield sortByTime(clamp(baseIndex + gauss, 0, sortByTime.length - 1))

        gaussPosts.toList
      }

      wrapHeuristic(_gaussTime, posts, num)
    }
  }

  sealed trait GraphHeuristic extends PostHeuristic {
    def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult = {
      Future.successful(List(graphHeuristic(graph, num)).map(l => Heuristic.PostResult(None, l)))
    }
    protected def graphHeuristic(graph: Graph, num: Option[Int]): List[Post]

    protected def wrapGraphHeuristic(f: Graph => List[Post], graph: Graph, num: Option[Int]): List[Post] = {
      assert(math.abs(num.getOrElse(0)) <= graph.length, "Cannot pick more elements than there are")
      if (graph.isEmpty) return List.empty[Post]

      val choice = f(graph)
      num match {
        case Some(n) => if (n > 0) choice.take(n) else choice.reverse.take(math.abs(n))
        case None => choice
      }
    }
  }

  case object NodeDegree extends GraphHeuristic {
    def graphHeuristic(graph: Graph, num: Option[Int] = Some(2)): List[Post] = {
      def _nodeDegree(graph: Graph) = graph.connectionDegree.toList.sortBy(_._2).map(p => graph.postsById(p._1))

      wrapGraphHeuristic(_nodeDegree, graph, num)
    }
  }

  sealed trait BackendHeuristic extends PostHeuristic {
    def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult = backendHeuristic(graph.posts.toSet, num)
    protected def backendHeuristic(posts: Set[Post], num: Option[Int] = Some(2)): PostHeuristicResult

    protected def wrappedBackendHeuristic(heuristic: NlpHeuristic)(posts: Set[Post], num: Option[Int] = Some(2)): PostHeuristicResult = {
      def _backendHeuristic(posts: List[Post]): Future[List[Heuristic.PostResult]] = {
        val postMap: Map[PostId, Post] = posts.map(p => p.id -> p).toMap
        val postIds = Client.api.chooseTaskPosts(heuristic, posts.map(_.id), num)
        postIds.map(_.map(r => Heuristic.PostResult(r.measure, r.postIds.map(postMap))))
      }

      assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
      assert(posts.nonEmpty, "Cannot call a heuristic on an empty set")

      val choice = _backendHeuristic(posts.toList)

      num match {
        case Some(n) => choice.map(_.map(r => Heuristic.PostResult(r.measure, {
          if (n > 0) r.posts.take(n) else r.posts.reverse.take(math.abs(n))
        })))
        case None => choice
      }
    }
  }

  case object DiceSorensen {
    def apply(nGramValue: Int) = new DiceSorensen(nGramValue)
  }
  case class DiceSorensen(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.DiceSorensen(nGram))(posts, num)
  }

  case object Hamming extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Hamming)(posts, num)
  }

  case object Jaccard {
    def apply(nGramValue: Int) = new Jaccard(nGramValue)
  }
  case class Jaccard(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Jaccard(nGram))(posts, num)
  }

  case object Jaro extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Jaro)(posts, num)
  }

  case object JaroWinkler extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.JaroWinkler)(posts, num)
  }

  case object Levenshtein extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Levenshtein)(posts, num)
  }

  case object NGram {
    def apply(nGramValue: Int) = new NGram(nGramValue)
  }
  case class NGram(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.NGram(nGram))(posts, num)
  }

  case object Overlap {
    def apply(nGramValue: Int) = new Overlap(nGramValue)
  }
  case class Overlap(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Overlap(nGram))(posts, num)
  }

  case object RatcliffObershelp extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.RatcliffObershelp)(posts, num)
  }

  case object WeightedLevenshtein {
    def apply(delWeight: Int, insWeight: Int, subWeight: Int) = new WeightedLevenshtein(delWeight, insWeight, subWeight)
  }
  case class WeightedLevenshtein(delWeight: Int, insWeight: Int, subWeight: Int) extends BackendHeuristic {
    def backendHeuristic(posts: Set[Post], num: Option[Int]): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.WeightedLevenshtein(delWeight, insWeight, subWeight))(posts, num)
  }

}

object ChoosePostHeuristic {
  // Normalize weights between 0.0 and 1.0. Get random value between 0.0 and 1.0.
  // Take corresponding heuristic ordered between 0.0 and 1.0
  def choose(heuristics: List[HeuristicParameters]): HeuristicParameters =  {
    val totalWeight = heuristics.foldLeft(0.0)(_ + _.probability)
    val normalizedHeuristics = heuristics.map(h => h.copy(probability = h.probability / totalWeight))
        .scan(HeuristicParameters(0.0))((h1, h2) => h2.copy(probability = h1.probability + h2.probability)).drop(1)
    val r = scala.util.Random.nextDouble
    DevPrintln(s"heuristics: $heuristics")
    DevPrintln(s"normalized heuristics: $normalizedHeuristics")
    DevPrintln(s"random: $r")
    val choice = normalizedHeuristics.filter(h => h.probability >= r).head
    choice
  }

}
