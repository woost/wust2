package wust.frontend.views.graphview

import math._
import rx._
import scalajs.js
import js.JSConverters._
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import org.scalajs.dom
import org.scalajs.dom.raw.HTMLElement
import org.scalajs.dom.console
import vectory._
import org.scalajs.d3v4._
import autowire._
import boopickle.Default._
import scalatags.JsDom.all._

import wust.frontend._
import wust.frontend.views.Views
import wust.graph._
import wust.util.collection._
import wust.util.Pipe

object DraggingPostSelection extends DataSelection[SimPost] {
  override val tag = "div"
  override def enter(post: Enter[SimPost]) {
    post.append { (simPost: SimPost) =>
      Views.post(simPost.post)(
        position.absolute,
        cursor.move,
        opacity := 0.5,
        border := simPost.border,
        backgroundColor := simPost.color
      ).render
    }
  }

  override def draw(post: Selection[SimPost]) {
    post
      .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
  }
}

class PostDrag(graphState: GraphState, d3State: D3State, onPostDragged: () => Unit = () => ()) {
  import d3State.{simulation, transform}

  private val _draggingPosts: Var[js.Array[SimPost]] = Var(js.Array())
  private val _closestPosts: Var[js.Array[SimPost]] = Var(js.Array())
  def draggingPosts: Rx[js.Array[SimPost]] = _draggingPosts
  def closestPosts: Rx[js.Array[SimPost]] = _closestPosts

  private def graph = graphState.rxGraph.now //TODO: avoid now, this will probably crash when dragging during an update
  private def postIdToSimPost = graphState.rxPostIdToSimPost.now

  private val dragHitDetectRadius = 100

  def updateDraggingPosts() {
    val posts = graph.posts.values
    _draggingPosts() = posts.flatMap(p => postIdToSimPost(p.id).draggingPost).toJSArray
  }

  def updateClosestPosts() {
    _closestPosts() = postIdToSimPost.values.filter(_.isClosest).toJSArray
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

  def postDragEnded(dragging: SimPost) {
    import DropMenu.dropActions
    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    val transformedEventPos = dragging.dragStart + (eventPos - dragging.dragStart) / transform.k

    val closest = simulation.find(transformedEventPos.x, transformedEventPos.y, dragHitDetectRadius).toOption
    closest match {
      case Some(target) if target != dragging =>
        import autowire._
        import boopickle.Default._

        val dropAction = dropActions(target.dropIndex(dropActions.size))
        println(s"\nDropped ${dropAction.name}: [${dragging.id}]${dragging.title} -> [${target.id}]${target.title}")
        dropAction.action(dragging, target)

        target.isClosest = false
        dragging.fixedPos = js.undefined
      case _ =>
        dragging.pos = transformedEventPos
        dragging.fixedPos = transformedEventPos
    }
    updateClosestPosts()

    dragging.draggingPost = None
    updateDraggingPosts()

    simulation.alpha(1).restart()
  }

}
