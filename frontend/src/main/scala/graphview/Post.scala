package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import org.scalajs.dom.console
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._
import Color._

class PostSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
  extends DataSelection[SimPost](container, "div", keyFunction = Some((p: SimPost) => p.id)) {
  import env._
  import PostDrag._

  var postIdToSimPost: Map[AtomId, SimPost] = Map.empty
  def update(posts: Iterable[Post]) {

    val newData = posts.map { p =>
      val sp = new SimPost(p)
      postIdToSimPost.get(sp.id).foreach { old =>
        // preserve position
        sp.x = old.x
        sp.y = old.y
      }

      val parents = graph.parents(p.id)
      val parentColors = parents.map((p: Post) => baseColor(p.id))
      val selfColor = (if (graph.children(p.id).nonEmpty) baseColor(p.id) else postDefaultColor)
      sp.color = (if (parentColors.nonEmpty) mixColors(selfColor, mixColors(parentColors)) else selfColor).toString()
      sp
    }.toJSArray

    update(newData)
    postIdToSimPost = (newData: js.ArrayOps[SimPost]).by(_.id)
  }

  override def enter(post: Selection[SimPost]) {
    post
      .text((post: SimPost) => post.title)
      .style("padding", "3px 5px")
      .style("border-radius", "3px")
      .style("max-width", "100px")
      .style("position", "absolute")
      .style("cursor", "default")
      .style("pointer-events", "auto") // reenable
      .on("click", { (p: SimPost) =>
        //TODO: click should not trigger drag
        if (focusedPost.isEmpty || focusedPost.get != p)
          focusedPost = Some(p)
        else
          focusedPost = None
        env.postMenuSelection.draw()
      })
      .call(d3.drag[SimPost]()
        .on("start", postDragStarted _)
        .on("drag", postDragged _)
        .on("end", postDragEnded _))

    nodes.each({ (node: HTMLElement, p: SimPost) =>
      //TODO: if this fails, because post is not rendered yet, recalculate it lazyly
      val rect = node.getBoundingClientRect
      p.size = Vec2(rect.width, rect.height)
      p.centerOffset = p.size / -2
      p.radius = p.size.length / 2
      p.collisionRadius = p.radius
    })

  }

  override def drawCall(post: Selection[SimPost]) {
    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
      .style("background-color", (post: SimPost) => post.color)
      .style("border", (p: SimPost) => if (p.isClosest) s"5px solid ${dropColors(p.dropIndex(dropActions.size))}" else "1px solid #444")
  }
}
