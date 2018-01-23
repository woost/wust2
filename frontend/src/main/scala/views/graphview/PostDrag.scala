 package wust.frontend.views.graphview

 import org.scalajs.d3v4._
 import outwatch.dom._
 import outwatch.dom.dsl._
 import rx._
 import vectory._
 import wust.util.outwatchHelpers._
 import wust.graph._
 import wust.ids._
 import wust.frontend.DevPrintln

 import scala.concurrent.ExecutionContext
 import scala.scalajs.js
 import scala.scalajs.js.JSConverters._

 object DraggingPostSelection extends DataSelection[SimPost] {
   override val tag = "div"
   override def enter(post: Enter[SimPost]): Unit = {
     post.append { (simPost: SimPost) =>
       GraphView.postView(simPost.post)(
         cursor.move, //TODO: this is not working...
         opacity := 0.5,
         fontSize := simPost.fontSize,
         border := simPost.border,
         backgroundColor := simPost.color
       ).render
     }
   }

   override def draw(post: Selection[SimPost]): Unit = {
     post
       // .style("left", (p: SimPost) => s"${p.x.get + p.centerOffset.x}px")
       // .style("top", (p: SimPost) => s"${p.y.get + p.centerOffset.y}px")
       .style("transform", (p: SimPost) => s"translate(${p.x.get + p.centerOffset.x}px,${p.y.get + p.centerOffset.y}px)")
   }
 }

 class PostDrag(graphState: GraphState, d3State: D3State, onPostDrag: () => Unit = () => (), onPostDragEnd: () => Unit = () => ())(implicit ec: ExecutionContext) {
   import d3State.{simulation, transform}
   import graphState.state

  val dragActions = js.Array(
    DragAction("connect", { (dragging: SimPost, target: SimPost) => state.eventProcessor.changes.unsafeOnNext(GraphChanges(addConnections = Set(Connection(dragging.id, Label("woo"), target.id)))) }),
    DragAction("insert into", { (dragging: SimPost, target: SimPost) =>
      val graph = graphState.state.inner.displayGraphWithParents.now.graph
      val containment = Connection(dragging.id, Label.parent, target.id)
      val removeContainments:Set[Connection] = (if (graph.ancestors(target.id).toSet contains dragging.id) { // cycle
        Set.empty
      } else { // no cycle
      val intersectingParents = graph.parents(dragging.id).toSet intersect (graph.ancestors(target.id).toSet ++ graph.descendants(target.id).toSet)
        intersectingParents.map(Connection(dragging.id, Label.parent, _)) intersect graph.containments
      })
      state.eventProcessor.changes.unsafeOnNext(GraphChanges(addConnections = Set(containment), delConnections = removeContainments))
    }),
    DragAction("move into", { (dragging: SimPost, target: SimPost) =>
      val contextGraph = graphState.state.inner.displayGraphWithoutParents.now.graph
      val newContainments = Set(Connection(dragging.id, Label.parent, target.id))
      val removeContainments:Set[Connection] = (if (graph.ancestors(target.id).toSet contains dragging.id) { // cycle
        Set.empty
      } else { // no cycle
        ( contextGraph.parents(dragging.id) map (Connection(dragging.id, Label.parent, _))) - newContainments.head
      })
      state.eventProcessor.changes.unsafeOnNext(GraphChanges(addConnections = newContainments, delConnections = removeContainments))
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

   def updateDraggingPosts(): Unit = {
     val posts = graph.posts
     _draggingPosts() = posts.flatMap(p => postIdToSimPost(p.id).draggingPost).toJSArray
   }

   def updateClosestPosts(): Unit = {
     _closestPosts() = postIdToSimPost.values.filter(_.isClosest).toJSArray
   }

   def postDragStarted(p: SimPost): Unit = {
     val draggingPost = p.newDraggingPost
     p.draggingPost = Option(draggingPost)
     updateDraggingPosts()

     val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
     p.dragStart = eventPos
     draggingPost.pos = eventPos

     simulation.stop()
   }

   def closestTo(pos: Vec2) = simulation.find(pos.x, pos.y, dragHitDetectRadius / d3State.transform.now.k).toOption

   def postDragged(p: SimPost): Unit = {
     val draggingPost = p.draggingPost.get
     val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
     val transformedEventPos = p.dragStart + (eventPos - p.dragStart) / transform.now.k
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

   def postDragEnded(dragging: SimPost): Unit = {
     val eventPos = Vec2(d3.event.asInstanceOf[DragEvent].x, d3.event.asInstanceOf[DragEvent].y)
     val transformedEventPos = dragging.dragStart + (eventPos - dragging.dragStart) / transform.now.k

     val closest = closestTo(transformedEventPos)
     closest match {
       case Some(target) if target.id != dragging.id =>

        val dragAction = dragActions(target.dropIndex(dragActions.length))
        DevPrintln(s"\nDropped ${dragAction.name}: [${dragging.id}]${dragging.content} -> [${target.id}]${target.content}")
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
