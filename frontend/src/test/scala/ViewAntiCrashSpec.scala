package wust.frontend.views

import org.scalatest._
import org.scalatest.prop._
import rx.Ctx.Owner.Unsafe._
import rxext._
import wust.frontend._
import wust.frontend.views.graphview.GraphView
import wust.graph._

import scala.collection.immutable._

class ViewsExamples extends Tables {
  def views = Table("views", AddPostForm(_), TreeView(_), GraphView(_: GlobalState, disableSimulation = true), MainView(_: GlobalState, disableSimulation = true))
}

class ViewAntiCrashSpec extends FreeSpec with TableDrivenPropertyChecks with MustMatchers {
  "focusing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "wust")))
      view(state).render
      state.focusedPostId() = Option(1)
    }
  }

  "unfocusing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "wust")))
      state.focusedPostId() = Option(1)
      view(state).render
      state.focusedPostId() = None
    }
  }

  "editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "wust")))
      view(state).render
      state.editedPostId() = Option(1)
    }
  }

  "stop editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "wust")))
      state.editedPostId() = Option(1)
      view(state).render
      state.editedPostId() = None
    }
  }

  "adding post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph()
      view(state).render
      state.rawGraph.updatef(_ + Post(1, "wurst"))
    }
  }

  "updating post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "wust")))
      view(state).render
      state.rawGraph.updatef(_ + Post(1, "wurst"))
    }
  }

  "deleting post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustsein")))
      view(state).render
      state.rawGraph.updatef(_ - PostId(1))
    }
  }

  "deleting connected source post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")), List(Connects(3, PostId(1), PostId(2))))
      view(state).render
      state.rawGraph.updatef(_ - PostId(1))
    }
  }

  "deleting connected target post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")), List(Connects(3, PostId(1), PostId(2))))
      view(state).render
      state.rawGraph.updatef(_ - PostId(2))
    }
  }

  "deleting containment parent post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")), containments = List(Contains(3, PostId(1), PostId(2))))
      view(state).render
      state.rawGraph.updatef(_ - PostId(1))
    }
  }

  "deleting containment child post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")), containments = List(Contains(3, PostId(1), PostId(2))))
      view(state).render
      state.rawGraph.updatef(_ - PostId(2))
    }
  }

  "deleting focused post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos")))
      state.focusedPostId() = Option(1)
      view(state).render
      state.rawGraph.updatef(_ - PostId(1))
    }
  }

  "deleting editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos")))
      state.editedPostId() = Option(1)
      view(state).render
      state.rawGraph.updatef(_ - PostId(1))
    }
  }

  "updating focused post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos")))
      state.focusedPostId() = Option(1)
      view(state).render
      state.rawGraph.updatef(_ + Post(1, "wurst"))
    }
  }

  "updating editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos")))
      state.editedPostId() = Option(1)
      view(state).render
      state.rawGraph.updatef(_ + Post(1, "wurst"))
    }
  }

  "adding connection" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")))
      view(state).render
      state.rawGraph.updatef(_ + Connects(3, PostId(1), PostId(2)))
    }
  }

  "deleting connection" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")), List(Connects(3, PostId(1), PostId(2))))
      view(state).render
      state.rawGraph.updatef(_ - ConnectsId(3))
    }
  }

  "deleting containment" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")), containments = List(Contains(3, PostId(1), PostId(2))))
      view(state).render
      state.rawGraph.updatef(_ - ContainsId(3))
    }
  }

  "adding containment" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")))
      view(state).render
      state.rawGraph.updatef(_ + Contains(3, PostId(1), PostId(2)))
    }
  }
}
