package wust.frontend.views.graphview

import org.scalajs.d3v4._
import org.scalajs.dom.raw.HTMLElement
import rxext._
import wust.frontend._
import wust.frontend.views.Views
import wust.util.collection._
import wust.util.EventTracker.sendEvent

import scalatags.JsDom.all._

class PostSelection(graphState: GraphState, d3State: D3State, postDrag: PostDrag) extends DataSelection[SimPost] {
  import graphState.rxFocusedSimPost
  import postDrag._

  override val tag = "div"
  override def enter(post: Enter[SimPost]) {
    post.append((simPost: SimPost) => Views.post(simPost.post)(
      title := simPost.title,
      position.absolute,
      pointerEvents.auto, // reenable
      cursor.default
    ).render)
      //TODO: http://bl.ocks.org/couchand/6394506 distinguish between click and doubleclick, https://stackoverflow.com/questions/42330521/distinguishing-click-and-double-click-in-d3-version-4
      //TODO: click tolerance vs dragstart
      //TODO: Doubleclick -> Focus
      .on("click", { (p: SimPost) =>
        //TODO: click should not trigger drag
        DevPrintln(s"\nClicked Post: ${p.id} ${p.title}")
        sendEvent("post", "clicked", "postmenu")
        rxFocusedSimPost.updatef(_.map(_.id).setOrToggle(p.id))
      })
      .call(d3.drag[SimPost]()
        .on("start", postDragStarted _)
        .on("drag", postDragged _)
        .on("end", postDragEnded _))
  }

  override def update(post: Selection[SimPost]) {
    post
      .style("background-color", (post: SimPost) => post.color)
      .style("border", (p: SimPost) => p.border)
      .style("opacity", (p: SimPost) => p.opacity)
      .text((p: SimPost) => p.title)

    post.each({ (node: HTMLElement, p: SimPost) =>
      p.recalculateSize(node, d3State.transform.k)
    })
  }

  var draw = 0
  override def draw(post: Selection[SimPost]) {

    // DevOnly {
    //   assert(post.data().forall(_.size.width == 0) || post.data().forall(_.size.width != 0))
    // }
    val onePostHasSizeZero = {
      // every drawcall exactly one different post is checked
      val simPosts = post.data()
      if (simPosts.isEmpty) false
      else simPosts(draw % simPosts.size).size.width == 0

    }
    if (onePostHasSizeZero) {
      // if one post has size zero => all posts have size zero
      // --> recalculate all visible sizes
      post.each({ (node: HTMLElement, p: SimPost) =>
        p.recalculateSize(node, d3State.transform.k)
      })
    }

    post
      // .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      // .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
      .style("transform", (p: SimPost) => s"translate(${p.x.get + p.centerOffset.x}px,${p.y.get + p.centerOffset.y}px)")

    draw += 1
  }
}
