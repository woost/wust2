package wust.frontend.views.graphview

import org.scalajs.d3v4._
import rx._
import vectory._
import autowire._
import boopickle.Default._
import wust.frontend.Client
import wust.ids._
import wust.graph._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scalatags.JsDom.all._
import scala.concurrent.ExecutionContext

object DraggingPostSelection extends DataSelection[SimPost] {
  override val tag = "div"
  override def enter(post: Enter[SimPost]) {
    post.append { (simPost: SimPost) =>
      GraphView.postView(simPost.post)(
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

class PostDrag(graphState: GraphState, d3State: D3State, onPostDrag: () => Unit = () => (), onPostDragEnd: () => Unit = () => ())(implicit ec: ExecutionContext) {
  import graphState.state.persistence
  import d3State.{ simulation, transform }

  val dragActions = js.Array(
    DragAction("connect", { (dragging: SimPost, target: SimPost) => persistence.addChanges(addConnections = Set(Connection(dragging.id, target.id))) }),
    DragAction("insert into", { (dragging: SimPost, target: SimPost) =>
      val graph = graphState.state.displayGraphWithParents.now.graph
      val containment = Containment(target.id, dragging.id)
      val intersectingParents = graph.parents(dragging.id).toSet intersect (graph.transitiveParents(target.id).toSet ++ graph.transitiveChildren(target.id).toSet)
      val removeContainments = intersectingParents.map(Containment(_, dragging.id)) intersect graph.containments
      persistence.addChanges(addContainments = Set(containment), delContainments = removeContainments)
      //TODO: if dragged into cycle, both posts lose their common parent
    }),
    DragAction("move into", { (dragging: SimPost, target: SimPost) =>
      val contextGraph = graphState.state.displayGraphWithoutParents.now.graph
      val newContainments = Set(Containment(target.id, dragging.id))
      val removeContainments = ( contextGraph.parents(dragging.id) map (Containment(_, dragging.id))) - newContainments.head
      persistence.addChanges(addContainments = newContainments, delContainments = removeContainments)
    })
  // DragAction("merge", { (dragging: SimPost, target: SimPost) => /*Client.api.merge(target.id, dragging.id).call()*/ }),
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

  def closestTo(pos: Vec2) = simulation.find(pos.x, pos.y, dragHitDetectRadius / d3State.transform.k).toOption

  def postDragged(p: SimPost) {
    val draggingPost = p.draggingPost.get
    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    val transformedEventPos = p.dragStart + (eventPos - p.dragStart) / transform.k
    val closest = closestTo(transformedEventPos)

    p.dragClosest.foreach(_.isClosest= false)
    closest match {
      case Some(target) if target.id != p.id =>
        val dir = draggingPost.pos.get - target.pos.get
        target.isClosest = true
        target.dropAngle = dir.angle
      case _ =>
    }
    p.dragClosest = closest
    updateClosestPosts()

    draggingPost.pos = transformedEventPos
    onPostDrag()
  }

  def postDragEnded(dragging: SimPost) {
    val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
    val transformedEventPos = dragging.dragStart + (eventPos - dragging.dragStart) / transform.k

    val closest = closestTo(transformedEventPos)
    closest match {
      case Some(target) if target.id != dragging.id =>

        val dragAction = dragActions(target.dropIndex(dragActions.length))
        println(s"\nDropped ${dragAction.name}: [${dragging.id}]${dragging.title} -> [${target.id}]${target.title}")
        dragAction.action(dragging, target)

        target.isClosest = false
        dragging.fixedPos = js.undefined
      case _ =>
        dragging.pos = transformedEventPos
        dragging.fixedPos = transformedEventPos
    }
    updateClosestPosts()

    dragging.draggingPost = None
    updateDraggingPosts()

    onPostDragEnd()
  }

}
