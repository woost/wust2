package frontend

import utest._
import graph._

object GlobalStateTest extends TestSuite{

  val tests = this{
    'graph_is_consistent{
      val state = new GlobalState
      state.rawGraph := Graph(
        posts = Map(1L -> Post(1, "title")),
        connections = Map(2L -> Connects(2, 1, 11)),
        containments = Map(3L -> Contains(3, 1, 12)))
      assert(state.rawGraph.value == Graph(posts = Map(1L -> Post(1, "title"))))
    }
    'empty_view_has_graph{
      val state = new GlobalState
      state.rawGraph := Graph(
        posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
        connections = Map(2L -> Connects(2, 1, 11)),
        containments = Map(3L -> Contains(3, 11, 1)))
      assert(state.rawGraph.value == state.graph.value)
    }
    'focused_consistent_with_graph{
      val state = new GlobalState
      state.focusedPostId := Some(1L)
      assert(state.focusedPostId.value == None)
      state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
      assert(state.focusedPostId.value == Some(1L))
      state.rawGraph := Graph.empty
      assert(state.focusedPostId.value == None)
    }
    'edited_consistent_with_graph{
      val state = new GlobalState
      state.editedPostId.foreach(_ => ()) //TODO WHY?
      state.editedPostId := Some(1L)
      assert(state.editedPostId.value == None)
      state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
      assert(state.editedPostId.value == Some(1L))
      state.rawGraph := Graph.empty
      assert(state.editedPostId.value == None)
    }
    'mode_consistent_with_edited_focused{
      val state = new GlobalState
      state.mode.foreach(_ => ()) //TODO WHY?
      state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
      state.editedPostId := Some(1L)
      state.focusedPostId := None
      assert(state.mode.value == InteractionMode(Some(1L), None))
      state.editedPostId := None
      state.focusedPostId := Some(1L)
      assert(state.mode.value == InteractionMode(None, Some(1L)))
      state.editedPostId := None
      state.focusedPostId := None
      assert(state.mode.value == InteractionMode(None, None))
      state.editedPostId := Some(1L)
      state.focusedPostId := Some(1L)
      assert(state.mode.value == InteractionMode(Some(1L), Some(1L)))
    }
    'view_consistent_with_collapsed{
      val state = new GlobalState
      state.collapsedPostIds := Set(1L)
      assert(state.currentView.value == View().union(View(collapsed = Selector.IdSet(Set(1L)))))
      state.collapsedPostIds := Set.empty
      assert(state.currentView.value == View().union(View(collapsed = Selector.IdSet(Set.empty))))
    }
    'graph_has_view{
      val state = new GlobalState
      state.rawGraph := Graph(
        posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
        connections = Map.empty,
        containments = Map(3L -> Contains(3, 1, 11)))
      state.currentView := View(collapsed = Selector.IdSet(Set(1L)))
      assert(state.graph.value == Graph(posts = Map(1L -> Post(1, "title"))))
    }
  }
}
