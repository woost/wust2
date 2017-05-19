package wust.frontend

import org.scalatest._
import rx.Ctx.Owner.Unsafe._
import wust.graph._
import wust.ids._

class GlobalStateSpec extends FreeSpec with MustMatchers {

  implicit def tuplePosts(t: (Long, Post)): (PostId, Post) = (PostId(t._1), t._2)
  implicit def tupleConnection(t: (Long, Connection)): (ConnectionId, Connection) = (ConnectionId(t._1), t._2)
  implicit def tupleContainment(t: (Long, Containment)): (ContainmentId, Containment) = (ContainmentId(t._1), t._2)
  //TODO: test the number of rx updates

  "raw graph" - {
    "be not consistent" in {
      val state = new GlobalState
      val graph = Graph(
        posts = List(Post(1, "title")),
        connections = List(Connection(2, 1, ConnectionId(11))),
        containments = List(Containment(3, 1, 12))
      )

      state.rawGraph() = graph

      state.rawGraph.now mustEqual graph
    }
  }

  "graph" - {
    "be complete with empty view" in {
      val state = new GlobalState
      state.rawGraph() = Graph(
        posts = List(Post(1, "title"), Post(11, "title2")),
        connections = List(Connection(2, 1, PostId(11))),
        containments = List(Containment(3, 11, 1))
      )

      state.rawGraph.now mustEqual state.displayGraph.now.graph
    }

    "be consistent with focused" in {
      val state = new GlobalState
      state.focusedPostId() = Option(1L)
      state.focusedPostId.now mustEqual None

      state.rawGraph() = Graph(posts = List(Post(1, "title")))
      state.focusedPostId.now mustEqual Option(PostId(1L))

      state.rawGraph() = Graph.empty
      state.focusedPostId.now mustEqual None
    }

    "be consistent with edited" in {
      val state = new GlobalState
      state.editedPostId() = Option(1L)
      state.editedPostId.now mustEqual None

      state.rawGraph() = Graph(posts = List(Post(1, "title")))
      state.editedPostId.now mustEqual Option(PostId(1L))

      state.rawGraph() = Graph.empty
      state.editedPostId.now mustEqual None
    }

    "be consistent with mode" in {
      val state = new GlobalState
      state.editedPostId() = Option(1L)
      state.focusedPostId() = Option(1L)
      state.mode.now mustEqual DefaultMode

      state.rawGraph() = Graph(posts = List(Post(1, "title")))
      state.mode.now mustEqual EditMode(1L)
    }

    "have view" in {
      val state = new GlobalState
      state.rawGraph() = Graph(
        posts = List(Post(1, "title"), Post(11, "title2")),
        connections = Nil,
        containments = List(Containment(3, 1, 11))
      )
      state.currentView() = Perspective(collapsed = Selector.IdSet(Set(1L)))

      state.displayGraph.now.graph mustEqual Graph(posts = List(Post(1, "title")))
    }

  }

  "view" - {
    "be consistent with collapsed" in {
      val state = new GlobalState
      state.collapsedPostIds() = Set(1L)
      state.currentView.now mustEqual Perspective().union(Perspective(collapsed = Selector.IdSet(Set(1L))))

      state.collapsedPostIds() = Set.empty
      state.currentView.now mustEqual Perspective().union(Perspective(collapsed = Selector.IdSet(Set.empty)))
    }
  }
}
