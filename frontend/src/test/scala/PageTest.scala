package frontend

import graph._
import org.scalatest._
import util.collectionHelpers._

class PageTest extends FlatSpec {
  "view" should "collapse graph" in {
    val selector = Selector.IdSet(Set(1L))
    val graph = Graph(
      posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
      connections = Map.empty,
      containments = Map(3L -> Contains(3, 1, 11)))
    val collapsed = View.collapse(selector, graph)
    assert(collapsed == Graph(posts = Map(1L -> Post(1, "title"))))
  }

  it should "not collapse cycle" in { //TODO cycle and non-cycle children
    val selector = Selector.IdSet(Set(11L))
    // 1 contains 11
    //11 contains 12
    //12 contains  1
    // --> containment cycle
    val graph = Graph(
      posts = List( Post(1, "title"),  Post(11, "title2"),  Post(12, "test3")).by(_.id),
      connections = Map.empty,
      containments = List( Contains(3, 1, 11),  Contains(4, 11, 12),  Contains(5, 12, 1)).by(_.id))
    val collapsed = View.collapse(selector, graph)
    assert(graph == collapsed) // nothing to collapse because of cycle
  }
}
