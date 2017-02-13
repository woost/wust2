package frontend.graphview

import frontend._

import graph._
import math._
import mhtml._

import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import vectory._
import org.scalajs.d3v4._
import util.collectionHelpers._
import autowire._
import boopickle.Default._
import com.outr.scribe._

object DraggingPostSelection extends DataSelection[SimPost] {
  override val tag = "div"
  override def enter(post: Selection[SimPost]) {
    post
      .text((post: SimPost) => post.title)
      .style("opacity", "0.5")
      .style("background-color", (post: SimPost) => post.color)
      .style("padding", "3px 5px")
      .style("border-radius", "3px")
      .style("border", "1px solid #AAA")
      .style("max-width", "100px")
      .style("position", "absolute")
      .style("cursor", "move")
  }

  override def draw(post: Selection[SimPost]) {
    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
  }
}

class PostDrag(rxPosts: RxPosts, d3State: D3State, onPostDragged: () => Unit = () => ()) {
  import d3State.{simulation, transform}

  private val _draggingPosts: Var[js.Array[SimPost]] = Var(js.Array())
  private val _closestPosts: Var[js.Array[SimPost]] = Var(js.Array())
  def draggingPosts: Rx[js.Array[SimPost]] = _draggingPosts
  def closestPosts: Rx[js.Array[SimPost]] = _closestPosts

  private def graph = rxPosts.rxGraph.value
  private def postIdToSimPost = rxPosts.postIdToSimPost.value

  private val dragHitDetectRadius = 100

  def updateDraggingPosts() {
    val posts = graph.posts.values
    _draggingPosts := posts.flatMap(p => postIdToSimPost(p.id).draggingPost).toJSArray
  }

  def updateClosestPosts() {
    _closestPosts := postIdToSimPost.values.filter(_.isClosest).toJSArray
  }

  def postDragStarted(p: SimPost) {
    val draggingPost = p.newDraggingPost
    p.draggingPost = Some(draggingPost)
    updateDraggingPosts()

    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    p.dragStart = eventPos
    draggingPost.pos = eventPos

    simulation.stop()
  }

  def postDragged(p: SimPost) {
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
    updateClosestPosts()

    draggingPost.pos = transformedEventPos
    onPostDragged()
  }

  def postDragEnded(p: SimPost) {
    import DropMenu.dropActions
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
    updateClosestPosts()

    p.draggingPost = None
    updateDraggingPosts()

    simulation.alpha(1).restart()
  }

}
