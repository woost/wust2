package util

import org.scalatest._

class AlgorithmsSpec extends FlatSpec {
  import algorithm._

  "depth first search" should "one vertex" in {
    val dfs = depthFirstSearch[Int](0, _ => Seq.empty).toList
    assert(dfs == List(0))
  }

  it should "directed cycle" in {
    val edges = Map(
      0 -> Seq(1, 2),
      1 -> Seq(3),
      2 -> Seq(1),
      3 -> Seq(0, 2))

    val dfs = depthFirstSearch[Int](0, edges).toList
    assert(dfs == List(0, 2, 1, 3))
  }

  it should "undirected cycle" in {
    val edges = Map(
      0 -> Seq(1, 2),
      1 -> Seq(3),
      2 -> Seq.empty,
      3 -> Seq(2))

    val dfs = depthFirstSearch[Int](0, edges).toList
    assert(dfs == List(0, 2, 1, 3))
  }

  "topological sort" should "empty" in {
    val list = topologicalSort[Int,Seq](Seq.empty, _ => Seq.empty)
    assert(list == List())
  }

  it should "one vertex" in {
    val list = topologicalSort[Int,Seq](Seq(0), _ => Seq.empty)
    assert(list == List(0))
  }

  it should "with successor" in {
    val list = topologicalSort[Int,Seq](Seq(0, 1), _ => Seq(1)).toList
    assert(list == List(0, 1))
  }

  it should "directed cycle" in {
    val edges = Map(
      0 -> Seq(1),
      1 -> Seq(2),
      2 -> Seq(1, 3),
      3 -> Seq.empty)

    val list = topologicalSort[Int,Seq](Seq(0, 1, 2, 3), edges).toList
    assert(list == List(0, 1, 2, 3))
  }
}
