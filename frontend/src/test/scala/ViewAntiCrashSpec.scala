package wust.frontend.views

import org.scalatest._
import org.scalatest.prop._
import rx.Ctx.Owner.Unsafe._
import rxext._
import wust.frontend._
import wust.frontend.views.graphview.GraphView
import wust.ids._
import wust.graph._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.collection.immutable._

class ViewsExamples extends Tables {
  DevOnly.enabled = false
  def views = Table("views", AddPostForm(_), TreeView(_), GraphView(_: GlobalState, disableSimulation = true), MainView(_: GlobalState, disableSimulation = true))
}

class ViewAntiCrashSpec extends FreeSpec with TableDrivenPropertyChecks with MustMatchers with LocalStorageMock {
  "focusing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "wust")))
      view(state).render
      state.focusedPostId() = Option("heinz")
    }
  }

  "unfocusing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "wust")))
      state.focusedPostId() = Option("heinz")
      view(state).render
      state.focusedPostId() = None
    }
  }

  "editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "wust")))
      view(state).render
      state.editedPostId() = Option("heinz")
    }
  }

  "stop editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "wust")))
      state.editedPostId() = Option("heinz")
      view(state).render
      state.editedPostId() = None
    }
  }

  "adding post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph()
      view(state).render
      state.rawGraph.updatef(_ + Post("heinz", "wurst"))
    }
  }

  "updating post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "wust")))
      view(state).render
      state.rawGraph.updatef(_ + Post("heinz", "wurst"))
    }
  }

  "deleting post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustsein")))
      view(state).render
      state.rawGraph.updatef(_ - PostId("heinz"))
    }
  }

  "deleting connected source post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), List(Connection("heinz", "kalla")))
      view(state).render
      state.rawGraph.updatef(_ - PostId("heinz"))
    }
  }

  "deleting connected target post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), List(Connection("heinz", "kalla")))
      view(state).render
      state.rawGraph.updatef(_ - PostId("kalla"))
    }
  }

  "deleting containment parent post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), containments = List(Containment("heinz", "kalla")))
      view(state).render
      state.rawGraph.updatef(_ - PostId("heinz"))
    }
  }

  "deleting containment child post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), containments = List(Containment(PostId("heinz"), PostId("kalla"))))
      view(state).render
      state.rawGraph.updatef(_ - PostId("kalla"))
    }
  }

  "deleting focused post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos")))
      state.focusedPostId() = Option("heinz")
      view(state).render
      state.rawGraph.updatef(_ - PostId("heinz"))
    }
  }

  "deleting editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos")))
      state.editedPostId() = Option("heinz")
      view(state).render
      state.rawGraph.updatef(_ - PostId("heinz"))
    }
  }

  "updating focused post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos")))
      state.focusedPostId() = Option("heinz")
      view(state).render
      state.rawGraph.updatef(_ + Post("heinz", "wurst"))
    }
  }

  "updating editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos")))
      state.editedPostId() = Option("heinz")
      view(state).render
      state.rawGraph.updatef(_ + Post("heinz", "wurst"))
    }
  }

  "adding connection" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")))
      view(state).render
      state.rawGraph.updatef(_ + Connection(PostId("heinz"), PostId("kalla")))
    }
  }

  "deleting connection" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      val conn = Connection(PostId("heinz"), PostId("kalla"))
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), List(conn))
      view(state).render
      state.rawGraph.updatef(_ - conn)
    }
  }

  "deleting containment" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")), containments = List(Containment(PostId("heinz"), PostId("kalla"))))
      view(state).render
      state.rawGraph.updatef(_ - Containment("heinz", "kalla"))
    }
  }

  "adding containment" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.rawGraph() = Graph(List(Post("heinz", "bewustlos"), Post("kalla", "unbewust")))
      view(state).render
      state.rawGraph.updatef(_ + Containment(PostId("heinz"), PostId("kalla")))
    }
  }
}
