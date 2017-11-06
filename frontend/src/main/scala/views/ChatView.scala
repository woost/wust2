 package wust.frontend.views

 import io.circe.Decoder.state
 import org.scalajs.d3v4._
 import org.scalajs.dom.raw.Element
 import outwatch.Sink
 import outwatch.dom._
 import rxscalajs.Observable
 import wust.frontend.Color._
 import wust.frontend._
 import wust.frontend.views.Elements.textAreaWithEnter
 import wust.graph._
 import wust.ids.PostId
 import wust.util.outwatchHelpers._

 import scala.scalajs.js.timers.setTimeout
 import scalaz.Tag

// Outwatch TODOs:
// when writing: sink <-- obs; obs(action)
// action is always triggered first, even though it is registered after subscribe(<--)
//
// observable.filter does not accept partial functions.filter{case (_,text) => text.nonEmpty}
//
// like Handler, Subject needs to be wrapped in IO
//
// handler[A].map(A => B) should return Sink[A] with Observable[B]

object ChatView {
  def apply(state: GlobalState) = {
    import state._
    val newPostHandler = createStringHandler().unsafeRunSync()

    (state.persistence.enrichChanges <-- newPostHandler.map{ text =>
      val newPost = Post.newId(text)
      GraphChanges( addPosts = Set(newPost) )
    }).unsafeRunSync()

    component(rawGraph, displayGraphWithoutParents, newPostHandler, page, ownPosts)
  }

  def component(
                 rawGraph:Observable[Graph],
                 displayGraphWithoutParents:Observable[DisplayGraph],
                 newPostSink:Sink[String],
                 page:Handler[Page],
                 ownPosts:PostId => Boolean
               ) = {

    val focusedParentIds = page.map(_.parentIds)

    val headLineText = focusedParentIds.combineLatestWith(rawGraph) { (focusedParentIds, rawGraph) =>
      val parents = focusedParentIds.map(rawGraph.postsById)
      val parentTitles = parents.map(_.title).mkString(", ")
      parentTitles
    }

    val bgColor = focusedParentIds.map { focusedParentIds =>
      val mixedDirectParentColors = mixColors(focusedParentIds.map(baseColor))
      mixColors(List(mixedDirectParentColors, d3.lab("#FFFFFF"), d3.lab("#FFFFFF"))).toString
    }

    val chatHistory = displayGraphWithoutParents.map{ dg =>
      val graph = dg.graph
      graph.posts.toSeq.sortBy(p => Tag.unwrap(p.id))
    }

    val latestPost = chatHistory.map(_.lastOption)

    def scrollToBottom(elem: Element) {
      //TODO: scrollHeight is not yet available in jsdom tests: https://github.com/tmpvar/jsdom/issues/1013
      try {
        elem.scrollTop = elem.scrollHeight
      } catch { case _: Throwable => } // with NonFatal(_) it fails in the tests
    }

    val chatHistoryDiv = div(
      //TODO: the update hook triggers too early. Try the postpatch-hook from snabbdom instead
      update --> { (e: Element) => println("update hook"); setTimeout(100) { scrollToBottom(e) } },
      stl("height") := "100%",
      stl("overflow") := "auto",
      stl("padding") := "20px",
      stl("backgroundColor") := "white",

      children <-- chatHistory.map {
        _.map{ post =>
          val isMine = ownPosts(post.id)
          div(
            p(
              stl("maxWidth") := "60%",
              post.title,
              stl("backgroundColor") := (if (isMine) "rgb(192, 232, 255)" else "#EEE"),
              stl("float") := (if (isMine) "right" else "left"),
              stl("clear") := "both",
              stl("padding") := "5px 10px",
              stl("borderRadius") := "7px",
              stl("margin") := "5px 0px",
              // TODO: What about cursor when selecting text?
              stl("cursor") := "pointer",
              click(Page.Union(Set(post.id))) --> page
            )
          )
        }
      }
    )

    div(
      stl("height") := "100%",
      stl("background-color") <-- bgColor,

      div(
        stl("margin") := "0 auto",
        stl("maxWidth") := "48rem",
        stl("width") := "48rem",
        stl("height") := "100%",

        stl("display") := "flex",
        stl("flexDirection") := "column",
        stl("justifyContent") := "flexStart",
        stl("alignItems") := "stretch",
        stl("alignContent") := "stretch",

        h1(child <-- headLineText),

        chatHistoryDiv,
        textAreaWithEnter(newPostSink)
      )
    )
  }

}
