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

class PostSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.State)
  extends DataSelection[SimPost](container, "div", keyFunction = Some((p: SimPost) => p.id)) {
  import env._
  import PostDrag._

  var postIdToSimPost: Map[AtomId, SimPost] = Map.empty
  def update(posts: Iterable[Post]) {

    val newData = posts.map { p =>
      val sp = new SimPost(p)
      postIdToSimPost.get(sp.id).foreach { old =>
        // preserve position, velocity and fixed position
        sp.x = old.x
        sp.y = old.y
        sp.vx = old.vx
        sp.vy = old.vy
        sp.fx = old.fx
        sp.fy = old.fy
      }

      def parents = graph.parents(p.id)
      def hasParents = parents.nonEmpty
      def mixedDirectParentColors = mixColors(parents.map((p: Post) => baseColor(p.id)))
      def hasChildren = graph.children(p.id).nonEmpty
      sp.border = (
        if (hasChildren)
          "2px solid rgba(0,0,0,0.4)"
        else { // no children
          "2px solid rgba(0,0,0,0.05)"
        }
      ).toString()
      sp.color = (
        if (hasChildren)
          baseColor(p.id)
        else { // no children
          if (hasParents)
            mixColors(mixedDirectParentColors, postDefaultColor)
          else
            postDefaultColor
        }
      ).toString()
      sp
    }.toJSArray

    update(newData)
    postIdToSimPost = (newData: js.ArrayOps[SimPost]).by(_.id)
  }

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
      .style("border", (p: SimPost) => p.border)
  }
}
