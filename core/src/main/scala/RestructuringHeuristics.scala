package wust.backend

import wust.graph.{Graph, Node}
import wust.ids.NodeId
import wust.api._
import wust.api.Heuristic._
import com.rockymadden.stringmetric.similarity._
import com.rockymadden.stringmetric._

case object PostHeuristic {

  implicit def IntToDoubleMetric(m: StringMetric[Int]): StringMetric[Double] =
    new StringMetric[Double] {
      override def compare(a: Array[Char], b: Array[Char]): Option[Double] =
        m.compare(a, b).map(_.toDouble)
      override def compare(a: String, b: String): Option[Double] = m.compare(a, b).map(_.toDouble)
    }

  def apply(
      graph: Graph,
      heuristic: NlpHeuristic,
      posts: List[NodeId],
      num: Option[Int]
  ): List[ApiResult] = {
    heuristic match {
      case NlpHeuristic.DiceSorensen(nGramValue) => diceSorensen(graph, posts, num, nGramValue)
      case NlpHeuristic.Hamming                  => hamming(graph, posts, num)
      case NlpHeuristic.Jaccard(nGramValue)      => jaccard(graph, posts, num, nGramValue)
      case NlpHeuristic.Jaro                     => jaro(graph, posts, num)
      case NlpHeuristic.JaroWinkler              => jaroWinkler(graph, posts, num)
      case NlpHeuristic.Levenshtein              => levenshtein(graph, posts, num)
      case NlpHeuristic.NGram(nGramValue)        => nGram(graph, posts, num, nGramValue)
      case NlpHeuristic.Overlap(nGramValue)      => overlap(graph, posts, num, nGramValue)
      case NlpHeuristic.RatcliffObershelp        => ratcliffObershelp(graph, posts, num)
      case NlpHeuristic.WeightedLevenshtein(delWeight, insWeight, subWeight) =>
        weightedLevenshtein(graph, posts, num, delWeight, insWeight, subWeight)
      case _ => List(Heuristic.IdResult(None, posts))
    }
  }

  private[this] def wrapHeuristic(
      f: List[Node.Content] => List[Result],
      posts: List[Node.Content],
      num: Option[Int]
  ): List[Result] = {
    assert(math.abs(num.getOrElse(0)) <= posts.size, "Cannot pick more elements than there are")
    if (posts.isEmpty) return List.empty[Result]

    val choice = f(posts)
    num match {
      case Some(n) => if (n > 0) choice.take(n) else choice.reverse.take(math.abs(n))
      case None    => choice
    }
  }

  private[this] def wrapStringMetric(
      metric: StringMetric[Double],
      graph: Graph,
      nodeIds: List[NodeId],
      num: Option[Int]
  ): List[ApiResult] = {
    val posts = nodeIds.map(graph.nodesById).collect { case p: Node.Content => p }
    def f(p: List[Node.Content]): List[Result] =
      (for {
        l <- p.combinations(2).toList
      } yield {
        Heuristic.PostResult(metric.compare(l.head.data.str, l(1).data.str), l)
      }).sortBy(_.measure)

    val result = wrapHeuristic(f, posts, num)

    result.map(r => Heuristic.IdResult(r.measure, r.nodes.map(_.id)))
  }

  def diceSorensen(
      graph: Graph,
      posts: List[NodeId],
      num: Option[Int],
      nGramValue: Int = 3
  ): List[ApiResult] = {
    wrapStringMetric(DiceSorensenMetric(nGramValue), graph, posts, num)
  }

  def hamming(graph: Graph, posts: List[NodeId], num: Option[Int]): List[ApiResult] = {
    wrapStringMetric(HammingMetric, graph, posts, num)
  }

  def jaccard(
      graph: Graph,
      posts: List[NodeId],
      num: Option[Int],
      nGramValue: Int = 3
  ): List[ApiResult] = {
    wrapStringMetric(JaccardMetric(nGramValue), graph, posts, num)
  }

  def jaro(graph: Graph, posts: List[NodeId], num: Option[Int]): List[ApiResult] = {
    wrapStringMetric(JaroMetric, graph, posts, num)
  }

  def jaroWinkler(graph: Graph, posts: List[NodeId], num: Option[Int]): List[ApiResult] = {
    wrapStringMetric(JaroWinklerMetric, graph, posts, num)
  }

  def levenshtein(graph: Graph, posts: List[NodeId], num: Option[Int]): List[ApiResult] = {
    wrapStringMetric(LevenshteinMetric, graph, posts, num)
  }

  def nGram(
      graph: Graph,
      posts: List[NodeId],
      num: Option[Int],
      nGramValue: Int = 3
  ): List[ApiResult] = {
    wrapStringMetric(NGramMetric(nGramValue), graph, posts, num)
  }

  def overlap(
      graph: Graph,
      posts: List[NodeId],
      num: Option[Int],
      nGramValue: Int = 3
  ): List[ApiResult] = {
    wrapStringMetric(OverlapMetric(nGramValue), graph, posts, num)
  }

  def ratcliffObershelp(graph: Graph, posts: List[NodeId], num: Option[Int]): List[ApiResult] = {
    wrapStringMetric(RatcliffObershelpMetric, graph, posts, num)
  }

  def weightedLevenshtein(
      graph: Graph,
      posts: List[NodeId],
      num: Option[Int],
      delWeight: Int,
      insWeight: Int,
      subWeight: Int
  ): List[ApiResult] = {
    wrapStringMetric(WeightedLevenshteinMetric(delWeight, insWeight, subWeight), graph, posts, num)
  }

}
