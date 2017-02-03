package frontend.graphview

import frontend._

import graph._
import math._

import scalajs.js
import js.JSConverters._
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._

class DraggingPostSelection(container: Selection[dom.EventTarget])(implicit env: GraphView.D3Environment)
  extends DataSelection[SimPost](container, "div", keyFunction = Some((p: SimPost) => p.id)) {

  import env._
  override def enter(post: Selection[SimPost]) {
    post
      .text((post: SimPost) => post.title)
      .style("opacity", "0.5")
      .style("background-color", (post: SimPost) => post.color)
      .style("padding", "3px 5px")
      .style("border-radius", "5px")
      .style("border", "1px solid #AAA")
      .style("max-width", "100px")
      .style("position", "absolute")
      .style("cursor", "move")
  }

  override def drawCall(post: Selection[SimPost]) {
    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
  }
}

object PostDrag {
  def updateDraggingPosts()(implicit env: GraphView.D3Environment) {
    import env._
    import postSelection.postIdToSimPost

    val posts = graph.posts.values
    val draggingPosts = posts.flatMap(p => postIdToSimPost(p.id).draggingPost).toJSArray
    draggingPostSelection.update(draggingPosts)
  }

  def postDragStarted(p: SimPost)(implicit env: GraphView.D3Environment) {
    import env._
    val draggingPost = p.newDraggingPost
    p.draggingPost = Some(draggingPost)
    updateDraggingPosts()

    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    p.dragStart = eventPos
    draggingPost.pos = eventPos
    draggingPostSelection.draw()

    simulation.stop()
  }

  def postDragged(p: SimPost)(implicit env: GraphView.D3Environment) {
    import env._
    val draggingPost = p.draggingPost.get
    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    val transformedEventPos = p.dragStart + (eventPos - p.dragStart) / transform.k
    val closest = simulation.find(transformedEventPos.x, transformedEventPos.y, dragHitDetectRadius).toOption

    p.dragClosest.foreach(_.isClosest = false)
    closest match {
      case Some(target) if target != p =>
        val dir = draggingPost.pos.get - target.pos.get
        target.isClosest = true
        target.dropAngle = dir.angle
      case _ =>
    }
    p.dragClosest = closest

    draggingPost.pos = transformedEventPos
    draggingPostSelection.draw()
    postSelection.draw() // for highlighting closest
  }

  def postDragEnded(p: SimPost)(implicit env: GraphView.D3Environment) {
    import env._
    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    val transformedEventPos = p.dragStart + (eventPos - p.dragStart) / transform.k

    val closest = simulation.find(transformedEventPos.x, transformedEventPos.y, dragHitDetectRadius).toOption
    closest match {
      case Some(target) if target != p =>
        import autowire._
        import boopickle.Default._

        dropActions(target.dropIndex(dropActions.size)).action(p, target)

        target.isClosest = false
        p.fixedPos = js.undefined
      case _ =>
        p.pos = transformedEventPos
        p.fixedPos = transformedEventPos
    }

    p.draggingPost = None
    updateDraggingPosts()
    draggingPostSelection.draw()

    simulation.alpha(1).restart()
  }

}
