package frontend

import org.scalatest._
import graph._

class GlobalStateSpec extends FlatSpec {

  "raw graph" should "be consistent" in {
    val state = new GlobalState
    state.rawGraph := Graph(
      posts = Map(1L -> Post(1, "title")),
      connections = Map(2L -> Connects(2, 1, 11)),
      containments = Map(3L -> Contains(3, 1, 12)))
    assert(state.rawGraph.value == Graph(posts = Map(1L -> Post(1, "title"))))
  }

  "graph" should "be complete with empty view" in {
    val state = new GlobalState
    state.rawGraph := Graph(
      posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
      connections = Map(2L -> Connects(2, 1, 11)),
      containments = Map(3L -> Contains(3, 11, 1)))
    assert(state.rawGraph.value == state.graph.value)
  }

  it should "be consistent with focused" in {
    val state = new GlobalState
    state.focusedPostId := Some(1L)
    assert(state.focusedPostId.value == None)
    state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
    assert(state.focusedPostId.value == Some(1L))
    state.rawGraph := Graph.empty
    assert(state.focusedPostId.value == None)
  }

  it should "be consistent with edited" in {
    val state = new GlobalState
    state.editedPostId.foreach(_ => ()) //TODO WHY?
    state.editedPostId := Some(1L)
    assert(state.editedPostId.value == None)
    state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
    assert(state.editedPostId.value == Some(1L))
    state.rawGraph := Graph.empty
    assert(state.editedPostId.value == None)
  }

  "mode" should "be consistent with mode" in {
    val state = new GlobalState
    state.mode.foreach(_ => ()) //TODO WHY?
    state.editedPostId := Some(1L)
    state.focusedPostId := Some(1L)
    assert(state.mode.value == InteractionMode(None, None))
    state.rawGraph := Graph(posts = Map(1L -> Post(1, "title")))
    assert(state.mode.value == InteractionMode(Some(1L), Some(1L)))
  }

  it should "have view" in {
    val state = new GlobalState
    state.rawGraph := Graph(
      posts = Map(1L -> Post(1, "title"), 11L -> Post(11, "title2")),
      connections = Map.empty,
      containments = Map(3L -> Contains(3, 1, 11)))
    state.currentView := View(collapsed = Selector.IdSet(Set(1L)))
    assert(state.graph.value == Graph(posts = Map(1L -> Post(1, "title"))))
  }

  "view" should "be consistent with collapsed" in {
    val state = new GlobalState
    state.collapsedPostIds := Set(1L)
    assert(state.currentView.value == View().union(View(collapsed = Selector.IdSet(Set(1L)))))
    state.collapsedPostIds := Set.empty
    assert(state.currentView.value == View().union(View(collapsed = Selector.IdSet(Set.empty))))
  }
}
