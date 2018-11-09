package wust.webApp.restructuring

import wust.api._
import wust.graph.{Graph, Node}
import wust.ids._
import wust.webApp.{Client, DevPrintln}

import PostHeuristic._
import Restructure._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case object ChooseTaskHeuristic {
  type HeuristicType = List[RestructuringTaskObject] => RestructuringTaskObject

  def random(tasks: List[RestructuringTaskObject]): RestructuringTaskObject = {
    tasks(scala.util.Random.nextInt(tasks.size))
  }

  def defaultHeuristic: HeuristicType = random
}

sealed trait PostHeuristic {
  def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult
}

object PostHeuristic {
  type PostHeuristicResult = Future[List[Heuristic.Result]]
  type PostHeuristicType = (Graph, Option[Int]) => PostHeuristicResult

  sealed trait FrontendHeuristic extends PostHeuristic {
    def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult = {
      Future.successful(
        List(frontendHeuristic(???, num))
          .map(l => Heuristic.PostResult(None, l))
      )
    }
    def frontendHeuristic(posts: List[Node.Content], num: Option[Int]): List[Node.Content]

    protected def wrapHeuristic(
        f: List[Node.Content] => List[Node.Content],
        posts: List[Node.Content],
        num: Option[Int]
    ): List[Node.Content] = {
      assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
      if (posts.isEmpty) return List.empty[Node.Content]

      val choice = f(posts)
      num match {
        case Some(n) => if (n > 0) choice.take(n) else choice.reverse.take(math.abs(n))
        case None    => choice
      }
    }

    protected def wrapMultiHeuristic(
        f: List[Node.Content] => List[List[Node.Content]],
        posts: Set[Node.Content],
        num: Option[Int]
    ): List[List[Node.Content]] = {
      assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
      if (posts.isEmpty) return List.empty[List[Node.Content]]

      val choice = f(posts.toList)
      num match {
        case Some(n) =>
          if (n > 0) choice.map(_.take(n)) else choice.map(_.reverse.take(math.abs(n)))
        case None => choice
      }
    }
  }

  case object Deterministic {
    def apply(deterministicPosts: List[Node.Content]) = new Deterministic(deterministicPosts)
  }
  case class Deterministic(deterministicPosts: List[Node.Content]) extends FrontendHeuristic {
    def frontendHeuristic(
        posts: List[Node.Content],
        num: Option[Int] = Some(deterministicPosts.length)
    ): List[Node.Content] = {
      deterministicPosts
    }
  }

  case object Random extends FrontendHeuristic {
    def frontendHeuristic(
        posts: List[Node.Content],
        num: Option[Int] = Some(2)
    ): List[Node.Content] = {
      def _random(p: List[Node.Content]) = scala.util.Random.shuffle(p)

      wrapHeuristic(_random, posts, num)
    }
  }

//  case object Newest extends FrontendHeuristic {
//    def frontendHeuristic(posts: List[Post.Content], num: Option[Int] = Some(2)): List[Post.Content] = {
//      def _newest(p: List[Post.Content]) = p.sortWith((p1, p2) => p1.created.isBefore(p2.created))
//
//      wrapHeuristic(_newest, posts, num)
//    }
//  }
//
//  case object Oldest extends FrontendHeuristic {
//    def frontendHeuristic(posts: List[Post.Content], num: Option[Int] = Some(2)): List[Post.Content] = {
//      def _oldest(p: List[Post.Content]) = p.sortWith((p1, p2) => p1.created.isAfter(p2.created))
//
//      wrapHeuristic(_oldest, posts, num)
//    }
//  }
//
//  case object MaxPostSize extends FrontendHeuristic {
//    def frontendHeuristic(posts: List[Post.Content], num: Option[Int] = Some(1)): List[Post.Content] = { // for e.g. SplitPost
//      def _maxPostSize(p: List[Post.Content]) = p.sortWith((p1, p2) => p1.content.str.length > p2.content.str.length)
//
//      wrapHeuristic(_maxPostSize, posts, num)
//    }
//  }
//
//  case object MinPostSize extends FrontendHeuristic {
//    def frontendHeuristic(posts: List[Post.Content], num: Option[Int] = Some(1)): List[Post.Content] = {
//      def _minPostSize(p: List[Post.Content]) = p.sortWith((p1, p2) => p1.content.str.length < p2.content.str.length)
//
//      wrapHeuristic(_minPostSize, posts, num)
//    }
//  }

//  case object GaussTime extends FrontendHeuristic {
//    def frontendHeuristic(posts: List[Post.Content], num: Option[Int] = Some(2)): List[Post.Content] = {
//      def _gaussTime(posts: List[Post.Content]) = {
//        def clamp(value: Int, minValue: Int, maxValue: Int) = math.min(math.max(value, minValue), maxValue)
//
//        val base = scala.util.Random.shuffle(posts).head
//
//        val sortByTime: List[Post.Content] = posts.sortWith((p1, p2) => p1.created.isBefore(p2.created))
//
//        val baseIndex = sortByTime.indexOf(base)
//        val pseudoStdDevPosts = 34.0 * sortByTime.length / 100
//
//        def gauss = math.round(scala.util.Random.nextGaussian * pseudoStdDevPosts).toInt
//
//        val gaussPosts = for (_ <- 0 to num.getOrElse(posts.size)) yield sortByTime(clamp(baseIndex + gauss, 0, sortByTime.length - 1))
//
//        gaussPosts.toList
//      }
//
//      wrapHeuristic(_gaussTime, posts, num)
//    }
//  }

  sealed trait GraphHeuristic extends PostHeuristic {
    def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult = {
      Future.successful(List(graphHeuristic(graph, num)).map(l => Heuristic.PostResult(None, l)))
    }
    protected def graphHeuristic(graph: Graph, num: Option[Int]): List[Node.Content]

    protected def wrapGraphHeuristic(
        f: Graph => List[Node.Content],
        graph: Graph,
        num: Option[Int]
    ): List[Node.Content] = {
      assert(math.abs(num.getOrElse(0)) <= graph.length, "Cannot pick more elements than there are")
      if (graph.isEmpty) return List.empty[Node.Content]

      val choice = f(graph)
      num match {
        case Some(n) => if (n > 0) choice.take(n) else choice.reverse.take(math.abs(n))
        case None    => choice
      }
    }
  }

  case object NodeDegree extends GraphHeuristic {
    def graphHeuristic(graph: Graph, num: Option[Int] = Some(2)): List[Node.Content] = {
      def _nodeDegree(graph: Graph) =
        ???
//        graph.connectionDegree.toList.sortBy(_._2).map(p => graph.nodesById(p._1)).collect {
//          case p: Node.Content => p
//        }

      wrapGraphHeuristic(_nodeDegree, graph, num)
    }
  }

  sealed trait BackendHeuristic extends PostHeuristic {
    def heuristic(graph: Graph, num: Option[Int]): PostHeuristicResult =
      backendHeuristic(???, num)
    protected def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int] = Some(2)
    ): PostHeuristicResult

    protected def wrappedBackendHeuristic(
        heuristic: NlpHeuristic
    )(posts: Set[Node.Content], num: Option[Int] = Some(2)): PostHeuristicResult = {
      def _backendHeuristic(posts: List[Node.Content]): Future[List[Heuristic.PostResult]] = {
        val postMap: Map[NodeId, Node.Content] = posts.map(p => p.id -> p).toMap
        val nodeIds = Client.api.chooseTaskNodes(heuristic, posts.map(_.id), num)
        nodeIds.map(_.map(r => Heuristic.PostResult(r.measure, r.nodeIds.map(postMap))))
      }

      assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
      assert(posts.nonEmpty, "Cannot call a heuristic on an empty set")

      val choice = _backendHeuristic(posts.toList)

      num match {
        case Some(n) =>
          choice.map(
            _.map(
              r =>
                Heuristic.PostResult(r.measure, {
                  if (n > 0) r.nodes.take(n) else r.nodes.reverse.take(math.abs(n))
                })
            )
          )
        case None => choice
      }
    }
  }

  case object DiceSorensen {
    def apply(nGramValue: Int) = new DiceSorensen(nGramValue)
  }
  case class DiceSorensen(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.DiceSorensen(nGram))(posts, num)
  }

  case object Hamming extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Hamming)(posts, num)
  }

  case object Jaccard {
    def apply(nGramValue: Int) = new Jaccard(nGramValue)
  }
  case class Jaccard(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.Jaccard(nGram))(posts, num)
  }

  case object Jaro extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] = wrappedBackendHeuristic(NlpHeuristic.Jaro)(posts, num)
  }

  case object JaroWinkler extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.JaroWinkler)(posts, num)
  }

  case object Levenshtein extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.Levenshtein)(posts, num)
  }

  case object NGram {
    def apply(nGramValue: Int) = new NGram(nGramValue)
  }
  case class NGram(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.NGram(nGram))(posts, num)
  }

  case object Overlap {
    def apply(nGramValue: Int) = new Overlap(nGramValue)
  }
  case class Overlap(nGram: Int) extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.Overlap(nGram))(posts, num)
  }

  case object RatcliffObershelp extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.RatcliffObershelp)(posts, num)
  }

  case object WeightedLevenshtein {
    def apply(delWeight: Int, insWeight: Int, subWeight: Int) =
      new WeightedLevenshtein(delWeight, insWeight, subWeight)
  }
  case class WeightedLevenshtein(delWeight: Int, insWeight: Int, subWeight: Int)
      extends BackendHeuristic {
    def backendHeuristic(
        posts: Set[Node.Content],
        num: Option[Int]
    ): Future[List[Heuristic.Result]] =
      wrappedBackendHeuristic(NlpHeuristic.WeightedLevenshtein(delWeight, insWeight, subWeight))(
        posts,
        num
      )
  }

}

object ChoosePostHeuristic {
  // Normalize weights between 0.0 and 1.0. Get random value between 0.0 and 1.0.
  // Take corresponding heuristic ordered between 0.0 and 1.0
  def choose(heuristics: List[HeuristicParameters]): HeuristicParameters = {
    val totalWeight = heuristics.foldLeft(0.0)(_ + _.probability)
    val normalizedHeuristics = heuristics
      .map(h => h.copy(probability = h.probability / totalWeight))
      .scan(HeuristicParameters(0.0))(
        (h1, h2) => h2.copy(probability = h1.probability + h2.probability)
      )
      .drop(1)
    val r = scala.util.Random.nextDouble
    DevPrintln(s"heuristics: $heuristics")
    DevPrintln(s"normalized heuristics: $normalizedHeuristics")
    DevPrintln(s"random: $r")
    val choice = normalizedHeuristics.filter(h => h.probability >= r).head
    choice
  }

}
