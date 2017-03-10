package wust.frontend

import wust.graph._
import org.scalatest._
import wust.util.collection._

class PageTest extends FreeSpec with MustMatchers {
  implicit def tuplePosts(t:(Long, Post)): (PostId, Post) = (PostId(t._1), t._2)
  implicit def tupleConnects(t:(Long, Connects)): (ConnectsId, Connects) = (ConnectsId(t._1), t._2)
  implicit def tupleContains(t:(Long, Contains)): (ContainsId, Contains) = (ContainsId(t._1), t._2)

  "view" - {
    "collapse graph" in {
      val selector = Selector.IdSet(Set(1L))
      val graph = Graph(
        posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
        connections = Map.empty,
        containments = Map(3L -> Contains(3, 1, PostId(11))))
      val collapsed = View.collapse(selector, graph)

      collapsed mustEqual Graph(posts = Map(1L -> Post(1, "title")))
    }

    "not collapse cycle" in { //TODO cycle and non-cycle children
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

      graph mustEqual collapsed // nothing to collapse because of cycle
    }
  }
}
