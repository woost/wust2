package wust.frontend.views.graphview

import org.scalajs.d3v4._
import rx._
import vectory._
import wust.frontend.views.Views
import autowire._
import boopickle.Default._
import wust.frontend.Client
import wust.ids._
import wust.graph.Containment

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scalatags.JsDom.all._
import scala.concurrent.ExecutionContext

object DraggingPostSelection extends DataSelection[SimPost] {
  override val tag = "div"
  override def enter(post: Enter[SimPost]) {
    post.append { (simPost: SimPost) =>
      Views.post(simPost.post)(
        position.absolute,
        cursor.move,
        opacity := 0.5,
        fontSize := simPost.fontSize,
        border := simPost.border,
        backgroundColor := simPost.color
      ).render
    }
  }

  override def draw(post: Selection[SimPost]) {
    post
      // .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
      // .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
      .style("transform", (p: SimPost) => s"translate(${p.x.get + p.centerOffset.x}px,${p.y.get + p.centerOffset.y}px)")
  }
}

class PostDrag(graphState: GraphState, d3State: D3State, onPostDragged: () => Unit = () => ())(implicit ec: ExecutionContext) {
  import d3State.{ simulation, transform }

  val dropActions = js.Array(
    DropAction("connect", { (dropped: SimPost, target: SimPost) => Client.api.connect(dropped.id, target.id).call() }),
    DropAction("insert into", { (dropped: SimPost, target: SimPost) =>
      val graph = graphState.state.displayGraph.now.graph
      Client.api.createContainment(target.id, dropped.id).call()
      val intersectingParents = graph.parents(dropped.id).toSet intersect (graph.transitiveParents(target.id).toSet ++ graph.transitiveChildren(target.id).toSet)
      val removeContainments = intersectingParents.map(Containment(_, dropped.id)) intersect graph.containments
      removeContainments.foreach {
        Client.api.deleteContainment(_).call()
      }
    })
  // DropAction("Merge", { (dropped: SimPost, target: SimPost) => /*Client.api.merge(target.id, dropped.id).call()*/ }),
  )

  private val _draggingPosts: Var[js.Array[SimPost]] = Var(js.Array())
  private val _closestPosts: Var[js.Array[SimPost]] = Var(js.Array())
  def draggingPosts: Rx[js.Array[SimPost]] = _draggingPosts
  def closestPosts: Rx[js.Array[SimPost]] = _closestPosts

  private def graph = graphState.rxDisplayGraph.now.graph //TODO: avoid now, this will probably crash when dragging during an update
  private def postIdToSimPost = graphState.rxPostIdToSimPost.now

  private val dragHitDetectRadius = 100

  def updateDraggingPosts() {
    val posts = graph.posts
    _draggingPosts() = posts.flatMap(p => postIdToSimPost(p.id).draggingPost).toJSArray
  }

  def updateClosestPosts() {
    _closestPosts() = postIdToSimPost.values.filter(_.isClosest).toJSArray
  }

  def postDragStarted(p: SimPost) {
    val draggingPost = p.newDraggingPost
    p.draggingPost = Option(draggingPost)
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

    p.dragClosest.foreach(_.isClosest= false)
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
    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    val transformedEventPos = dragging.dragStart + (eventPos - dragging.dragStart) / transform.k

    val closest = simulation.find(transformedEventPos.x, transformedEventPos.y, dragHitDetectRadius).toOption
    closest match {
      case Some(target) if target != dragging =>

        val dropAction = dropActions(target.dropIndex(dropActions.length))
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
