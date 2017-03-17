package wust.frontend.views

import org.scalatest._
import org.scalatest.prop._
import wust.graph._
import rx.Ctx.Owner.Unsafe._
import wust.util.collection._
import wust.frontend._
import scala.collection.immutable._
import graphview.GraphView

class ViewsExamples extends Tables {
  def views = Table("views", AddPostForm(_), TreeView(_), GraphView(_), MainView(_))
}

class ViewAntiCrashSpec extends FreeSpec with TableDrivenPropertyChecks with MustMatchers {
  "focusing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "wust")).by(_.id))
      view(state).render
      state.focusedPostId := Some(1)
    }
  }

  "unfocusing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "wust")).by(_.id))
      state.focusedPostId := Some(1)
      view(state).render
      state.focusedPostId := None
    }
  }

  "editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "wust")).by(_.id))
      view(state).render
      state.editedPostId := Some(1)
    }
  }

  "stop editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "wust")).by(_.id))
      state.editedPostId := Some(1)
      view(state).render
      state.editedPostId := None
    }
  }

  "adding post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph()
      view(state).render
      state.graph.update(_ + Post(1, "wurst"))
    }
  }

  "updating post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "wust")).by(_.id))
      view(state).render
      state.graph.update(_ + Post(1, "wurst"))
    }
  }

  "deleting post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustsein")).by(_.id))
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }

  "deleting connected source post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id), List(Connects(3, PostId(1), PostId(2))).by(_.id))
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }

  "deleting connected target post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id), List(Connects(3, PostId(1), PostId(2))).by(_.id))
      view(state).render
      state.graph.update(_ - PostId(2))
    }
  }


  "deleting containment parent post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id), containments = List(Contains(3, PostId(1), PostId(2))).by(_.id))
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }

  "deleting containment child post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id), containments = List(Contains(3, PostId(1), PostId(2))).by(_.id))
      view(state).render
      state.graph.update(_ - PostId(2))
    }
  }

  "deleting focused post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos")).by(_.id))
      state.focusedPostId := Some(1)
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }

  "deleting editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos")).by(_.id))
      state.editedPostId := Some(1)
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }

  "updating focused post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos")).by(_.id))
      state.focusedPostId := Some(1)
      view(state).render
      state.graph.update(_ + Post(1, "wurst"))
    }
  }

  "updating editing post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos")).by(_.id))
      state.editedPostId := Some(1)
      view(state).render
      state.graph.update(_ + Post(1, "wurst"))
    }
  }

  "adding connection" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id))
      view(state).render
      state.graph.update(_ + Connects(3, PostId(1), PostId(2)))
    }
  }

  "deleting connection" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id), List(Connects(3, PostId(1), PostId(2))).by(_.id))
      view(state).render
      state.graph.update(_ - ConnectsId(3))
    }
  }

  "deleting containment" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id), containments = List(Contains(3, PostId(1), PostId(2))).by(_.id))
      view(state).render
      state.graph.update(_ - ContainsId(3))
    }
  }


  "adding containment" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos"), Post(2, "unbewust")).by(_.id))
      view(state).render
      state.graph.update(_ + Contains(3, PostId(1), PostId(2)))
    }
  }
}
