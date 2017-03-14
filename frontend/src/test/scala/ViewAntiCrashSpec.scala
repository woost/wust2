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
  "adding Post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph()
      view(state).render
      state.graph.update(_ + Post(1, "wurst"))
    }
  }

  "deleting Post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustsein")).by(_.id))
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }

  "deleting focused Post" in new ViewsExamples {
    forAll(views) { view =>
      val state = new GlobalState
      state.graph := Graph(List(Post(1, "bewustlos")).by(_.id))
      state.focusedPostId := Some(1)
      view(state).render
      state.graph.update(_ - PostId(1))
    }
  }
}
