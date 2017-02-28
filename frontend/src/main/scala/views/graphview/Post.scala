package frontend.views.graphview

import frontend._

import graph._
import math._
import mhtml._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._
import Color._

class PostSelection(graphState: GraphState, postDrag: PostDrag) extends DataSelection[SimPost] {
  import postDrag._, graphState.focusedPost

  override val tag = "div"
  override def enter(post: Selection[SimPost]) {
    post
      .text((post: SimPost) => post.title)
      .attr("title", (post: SimPost) => post.id)
      .style("padding", "3px 5px")
      // .style("border", "1px solid #444")
      .style("border-radius", "3px")
      .style("max-width", "10em")
      .style("position", "absolute") // TODO: max-width does not work with position:absolute
      .style("cursor", "default")
      .style("pointer-events", "auto") // reenable
      .on("click", { (p: SimPost) =>
        //TODO: click should not trigger drag
        focusedPost.update(_.setOrToggle(p.id))
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
      if (p.size.width == 0)
        post.each({ (node: HTMLElement, p: SimPost) =>
          p.recalculateSize(node)
        })
    }

    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
  }
}
