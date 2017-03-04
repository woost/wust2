package frontend.views.graphview

import frontend._

import graph._
import math._
import rx._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.raw.HTMLElement
import scalatags.JsDom.all._
import vectory._
import org.scalajs.d3v4._
import util.collection._
import Color._

class PostSelection(graphState: GraphState, postDrag: PostDrag) extends DataSelection[SimPost] {
  import postDrag._, graphState.rxFocusedSimPost

  override val tag = "div"
  override def enter(post: Enter[SimPost]) {
    post.append((simPost: SimPost) => frontend.views.Views.post(simPost.post)(
      title := simPost.title,
      position.absolute,
      pointerEvents.auto, // reenable
      cursor.default
    ).render)
      .on("click", { (p: SimPost) =>
        //TODO: click should not trigger drag
        rxFocusedSimPost.update(_.setOrToggle(p.id))
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
      .text((p: SimPost) => p.title)

    post.each({ (node: HTMLElement, p: SimPost) =>
      p.recalculateSize(node)
    })
  }

  override def draw(post: Selection[SimPost]) {

    // lazily recalculate rendered size to center posts
    // TODO: sometimes some elements still have size 0
    post.data().headOption.foreach { p =>
      post.each({ (node: HTMLElement, p: SimPost) =>
        if (p.size.width == 0) {
          p.recalculateSize(node)
        }
      })
    }

    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
  }
}
