package wust.frontend

import org.scalatest._

import wust.graph._
import wust.util.collection._

class PerspectiveSpec extends FreeSpec with MustMatchers {
  val edgeId: () => Long = {
    var id = 1000
    () => { id += 1; id } // die kollidieren mit posts. wir brauchen für jeden typ ne eigene range
  }

  implicit def intToPost(id: Int): Post = Post(id, "title")
  implicit def intTupleToConnects(ts: (Int, Int)): Connects = Connects(edgeId(), ts._1, PostId(ts._2))
  implicit def intTupleToContains(ts: (Int, Int)): Contains = Contains(edgeId(), ts._1, ts._2)
  implicit def intSetToSelectorIdSet(set: Set[Int]) = Selector.IdSet(set.map(PostId(_)))
  def PostIds(ids: Int*) = ids.map(PostId(_))

  "perspective" - {
    "collapse parent" in {
      val graph = Graph(
        posts = List(1, 11),
        containments = List(1 -> 11)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph - PostId(11))
    }

    "collapse child" in {
      val graph = Graph(
        posts = List(1, 11),
        containments = List(1 -> 11)
      )
      Perspective.collapse(Set(11), graph) mustEqual graph
    }

    "collapse transitive children" in {
      val graph = Graph(
        posts = List(1, 11, 12),
        containments = List(1 -> 11, 11 -> 12)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12))
    }

    "collapse children while having parent" in pendingUntilFixed {
      val graph = Graph(
        posts = List(1, 11, 12),
        containments = List(1 -> 11, 11 -> 12)
      )
      Perspective.collapse(Set(11), graph) mustEqual (graph - PostId(12))
    }

    "not collapse cycle" in {
      val graph = Graph(
        posts = List(11, 12, 13),
        containments = List(11 -> 12, 12 -> 13, 13 -> 11) // containment cycle
      )
      Perspective.collapse(Set(11), graph) mustEqual graph // nothing to collapse because of cycle
    }

    "not collapse cycle with child" in pendingUntilFixed {
      val graph = Graph(
        posts = List(11, 12, 13, 20),
        containments = List(11 -> 12, 12 -> 13, 13 -> 11, 12 -> 20) // containment cycle -> 20
      )
      Perspective.collapse(Set(11), graph) mustEqual (graph - PostId(20)) // cycle stays
    }

    "collapse parent with child-cycle" in {
      val graph = Graph(
        posts = List(1, 11, 12, 13),
        containments = List(1 -> 11, 11 -> 12, 12 -> 13, 13 -> 11) // 1 -> containment cycle
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12, 13))
    }

    "redirect collapsed connection to source" in {
      val connection = Connects(5, 11, PostId(2))
      val graph = Graph(
        posts = List(1, 11, 2),
        containments = List(1 -> 11),
        connections = List(connection)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(sourceId = 1))
    }

    "redirect collapsed connection to target" in {
      val connection = Connects(5, 2, PostId(11))
      val graph = Graph(
        posts = List(1, 11, 2),
        containments = List(1 -> 11),
        connections = List(connection)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(targetId = PostId(1)))
    }

    "collapse two parents" in {
      val graph = Graph(
        posts = List(1, 2, 11),
        containments = List(1 -> 11, 2 -> 11)
      )
      Perspective.collapse(Set(1), graph) mustEqual graph
      Perspective.collapse(Set(2), graph) mustEqual graph
      Perspective.collapse(Set(1, 2), graph) mustEqual (graph - PostId(11))
    }

    "redirect and split outgoing edge while collapsing two parents" in pendingUntilFixed {
      val connection = Connects(5, 11, PostId(20))
      val graph = Graph(
        posts = List(1, 2, 11, 20),
        containments = List(1 -> 11, 2 -> 11),
        connections = List(connection)
      )
      Perspective.collapse(Set(1), graph) mustEqual graph
      Perspective.collapse(Set(2), graph) mustEqual graph
      Perspective.collapse(Set(1, 2), graph) mustEqual (graph - PostId(11) + connection.copy(sourceId = 1) + connection.copy(sourceId = 2)) //TODO: new connection ids?
    }

    "redirect and split incoming edge while collapsing two parents" in pendingUntilFixed {
      val connection = Connects(5, 20, PostId(11))
      val graph = Graph(
        posts = List(1, 2, 11, 20),
        containments = List(1 -> 11, 2 -> 11),
        connections = List(connection)
      )
      Perspective.collapse(Set(1), graph) mustEqual graph
      Perspective.collapse(Set(2), graph) mustEqual graph
      Perspective.collapse(Set(1, 2), graph) mustEqual (graph - PostId(11) + connection.copy(targetId = PostId(1)) + connection.copy(targetId = PostId(2))) //TODO: new connection ids?
    }

    "redirect connection between children while collapsing two parents" in {
      val connection = Connects(5, 11, PostId(12))
      val graph = Graph(
        posts = List(1, 2, 11, 12),
        containments = List(1 -> 11, 2 -> 12),
        connections = List(connection)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph - PostId(11) + connection.copy(sourceId = 1))
      Perspective.collapse(Set(2), graph) mustEqual (graph - PostId(12) + connection.copy(targetId = PostId(2)))
      Perspective.collapse(Set(1, 2), graph) mustEqual (graph -- PostIds(11, 12) + connection.copy(sourceId = 1, targetId = PostId(2)))
    }

    "redirect and bundle edges to target" in pendingUntilFixed {
      val connection1 = Connects(5, 11, PostId(2))
      val connection2 = Connects(6, 12, PostId(2))
      val graph = Graph(
        posts = List(1, 11, 12, 2),
        containments = List(1 -> 11, 1 -> 12),
        connections = List(connection1, connection2)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12) + connection1.copy(sourceId = PostId(1))) //TODO: which id to choose? generate temporary id only for view?
    }

    "redirect and bundle edges to source" in pendingUntilFixed {
      val connection1 = Connects(5, 2, PostId(11))
      val connection2 = Connects(6, 2, PostId(12))
      val graph = Graph(
        posts = List(1, 11, 12, 2),
        containments = List(1 -> 11, 1 -> 12),
        connections = List(connection1, connection2)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12) + connection1.copy(targetId = PostId(1))) //TODO: which id to choose? generate temporary id only for view?
    }

    "redirect and bundle mixed edges" in pendingUntilFixed {
      val connection1 = Connects(5, 2, PostId(11))
      val connection2 = Connects(6, 12, PostId(2))
      val graph = Graph(
        posts = List(1, 11, 12, 2),
        containments = List(1 -> 11, 1 -> 12),
        connections = List(connection1, connection2)
      )
      Perspective.collapse(Set(1), graph) mustEqual (graph -- PostIds(11, 12) + connection1.copy(targetId = PostId(1))) //TODO: which id to choose? generate temporary id only for view? Which direction?
    }
  }
}
