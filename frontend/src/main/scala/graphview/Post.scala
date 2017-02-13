package frontend.graphview

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

class PostSelection(rxPosts: RxPosts, postDrag: PostDrag) extends DataSelection[SimPost] {
  import postDrag._, rxPosts.focusedPost

  override val tag = "div"
  override def enter(post: Selection[SimPost]) {
    post
      .text((post: SimPost) => post.title)
      .style("padding", "3px 5px")
      // .style("border", "1px solid #444")
      .style("border-radius", "3px")
      .style("max-width", "10em")
      .style("position", "absolute") // TODO: max-width does not work with position:absolute
      .style("cursor", "default")
      .style("pointer-events", "auto") // reenable
      .on("click", { (p: SimPost) =>
        //TODO: click should not trigger drag
        if (focusedPost.value.isEmpty || focusedPost.value.get != p)
          focusedPost := Some(p.id)
        else
          focusedPost := None
      })
      .call(d3.drag[SimPost]()
        .on("start", postDragStarted _)
        .on("drag", postDragged _)
        .on("end", postDragEnded _))
  }

  override def update(post: Selection[SimPost]) {
    post.each({ (node: HTMLElement, p: SimPost) =>
      //TODO: if this fails, because post is not rendered yet, recalculate it lazyly
      val rect = node.getBoundingClientRect
      p.size = Vec2(rect.width, rect.height)
      p.centerOffset = p.size / -2
      p.radius = p.size.length / 2
      p.collisionRadius = p.radius
    })
  }

  override def draw(post: Selection[SimPost]) {
    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
      .style("background-color", (post: SimPost) => post.color)
      .style("border", (p: SimPost) => p.border)
  }
}
