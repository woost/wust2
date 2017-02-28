package frontend

import org.scalatest._
import graph._

class GlobalStateSpec extends FreeSpec with MustMatchers {

  "raw graph" - {
    "be consistent" in {
      val state = new GlobalState
      state.rawGraph := Graph(
        posts = Map(1L -> Post(1, "title")),
        connections = Map(2L -> Connects(2, 1, 11)),
        containments = Map(3L -> Contains(3, 1, 12)))

      state.rawGraph.value mustEqual Graph(posts = Map(1L -> Post(1, "title")))
    }
  }

  "graph" - {
    "be complete with empty view" in {
      val state = new GlobalState
      state.rawGraph := Graph(
        posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
        connections = Map(2L -> Connects(2, 1, 11)),
        containments = Map(3L -> Contains(3, 11, 1)))

      state.rawGraph.value mustEqual state.graph.value
    }

    "be consistent with focused" in {
      val state = new GlobalState
      state.focusedPostId := Some(1L)
      state.focusedPostId.value mustEqual None

      state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
      state.focusedPostId.value mustEqual Some(1L)

      state.rawGraph := Graph.empty
      state.focusedPostId.value mustEqual None
    }

    "be consistent with edited" in {
      val state = new GlobalState
      state.editedPostId.foreach(_ => ()) //TODO WHY?
      state.editedPostId := Some(1L)
      state.editedPostId.value mustEqual None

      state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
      state.editedPostId.value mustEqual Some(1L)

      state.rawGraph := Graph.empty
      state.editedPostId.value mustEqual None
    }

    "be consistent with mode" in {
      val state = new GlobalState
      state.mode.foreach(_ => ()) //TODO WHY?
      state.editedPostId := Some(1L)
      state.focusedPostId := Some(1L)
      state.mode.value mustEqual InteractionMode(None, None)

      state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
      state.mode.value mustEqual InteractionMode(Some(1L), Some(1L))
    }

    "have view" in {
      val state = new GlobalState
      state.rawGraph := Graph(
        posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
        connections = Map.empty,
        containments = Map(3L -> Contains(3, 1, 11)))
      state.currentView := View(collapsed = Selector.IdSet(Set(1L)))

      state.graph.value mustEqual Graph(posts = Map(1L -> Post(1, "title")))
    }

  }

  "view" - {
    "be consistent with collapsed" in {
      val state = new GlobalState
      state.collapsedPostIds := Set(1L)
      state.currentView.value mustEqual View().union(View(collapsed = Selector.IdSet(Set(1L))))

      state.collapsedPostIds := Set.empty
      state.currentView.value mustEqual View().union(View(collapsed = Selector.IdSet(Set.empty)))
    }
  }
}
