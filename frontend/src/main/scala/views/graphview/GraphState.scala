// package wust.frontend.views.graphview

// import wust.frontend.Color._
// import wust.frontend.{DevOnly, GlobalState}
// import wust.graph._
// import wust.ids._
// import wust.util.Pipe
// import wust.util.collection._

// import scala.collection.breakOut
// import scala.scalajs.js
// import scala.scalajs.js.JSConverters._

//class GraphState(val state: GlobalState)(implicit ctx: Ctx.Owner) {
//  val rxDisplayGraph = state.displayGraphWithoutParents
//  val rxCollapsedPostIds = state.collapsedPostIds

//  def fontSizeByDepth(d: Int) = (Math.pow(0.65, d + 1) + 1) // 2..1
//  def fontSizeByTransitiveChildren(n: Int) = Math.log(n + 1) + 0.5 // 1..~5

//  val rxSimPosts: Rx[js.Array[SimPost]] = Rx {
//    val rawGraph = state.rawGraph().consistent
//    val graph = rxDisplayGraph().graph
//    val collapsedPostIds = rxCollapsedPostIds()

//    graph.posts.zipWithIndex.map { case (p,i) =>
//      val sp = new SimPost(p)

//      //TODO: this is only to avoid the initial positions of d3.simulation, and do that in GraphView.recalculateBoundsAndZoom
//      // if we replace d3.simulation we can hopefully remove this.
//      sp.x = Constants.invalidPosition
//      sp.y = Constants.invalidPosition

//      def parents = rawGraph.parents(p.id)
//      def hasParents = parents.nonEmpty
//      def mixedDirectParentColors = mixColors(parents.map(baseColor))
//      def hasChildren = rawGraph.children(p.id).nonEmpty

//      sp.border =
//        if (hasChildren) {
//          if (collapsedPostIds(p.id))
//            s"5px dotted rgba(0,0,0,0.5)"
//          else
//            s"10px solid ${baseColor(p.id)}"
//        } else
//          "2px solid rgba(0,0,0,0.2)" // no children

//      sp.fontSize = if (hasChildren) {
//        val factor = fontSizeByDepth(rawGraph.parentDepth(p.id)) * fontSizeByTransitiveChildren(rawGraph.descendants(p.id).size)
//        s"${factor * 100.0}%"
//      } else "100%"

//      sp.color = (
//        //TODO collapsedPostIds is not sufficient for being a parent (but currently no knowledge about collapsed children in graph)
//        if (hasChildren) {
//            baseColor(p.id)
//        } else { // no children
//          if (hasParents)
//            mixColors(mixedDirectParentColors, postDefaultColor)
//          else
//            postDefaultColor
//        }
//      ).toString

//      val postGroups = graph.groupsByPostId(p.id)
//      sp.opacity = if (state.selectedGroupId().map(postGroups.contains).getOrElse(postGroups.isEmpty)) 1.0 else 0.3

//      sp

//    }.toJSArray // TODO: topological sort?
//  }

//  val rxPostIdToSimPost: Rx[Map[PostId, SimPost]] = rxSimPosts.fold(Map.empty[PostId, SimPost]) {
//    (previousMap, simPosts) =>
//      (simPosts: js.ArrayOps[SimPost]).by(_.id) sideEffect (_.foreach {
//        case (id, simPost) =>
//          previousMap.get(id).foreach { previousSimPost =>
//            // preserve position, velocity and fixed position
//            simPost.x = previousSimPost.x
//            simPost.y = previousSimPost.y
//            simPost.vx = previousSimPost.vx
//            simPost.vy = previousSimPost.vy
//            simPost.fx = previousSimPost.fx
//            simPost.fy = previousSimPost.fy
//          }
//      })
//  }

//  //TODO: multiple menus for multi-user multi-touch interface?
//  val rxFocusedSimPost = RxVar(state.focusedPostId, Rx { state.focusedPostId().flatMap(rxPostIdToSimPost().get) })
//  // val rxFocusedSimPost = state.focusedPostId.combine { fp => fp.flatMap(postIdToSimPost().get) } // TODO: Possible? See RxExt

//  val rxSimConnection = Rx {
//    val graph = rxDisplayGraph().graph
//    val postIdToSimPost = rxPostIdToSimPost()

//    graph.connections.map { c => new SimConnection(c, postIdToSimPost(c.sourceId), postIdToSimPost(c.targetId)) }.toJSArray
//  }

//  val rxSimRedirectedConnection = Rx {
//    val displayGraph = rxDisplayGraph()
//    val postIdToSimPost = rxPostIdToSimPost()

//    displayGraph.redirectedConnections.map { c =>
//      new SimRedirectedConnection(c, postIdToSimPost(c.sourceId), postIdToSimPost(c.targetId))
//    }.toJSArray
//  }

//  val rxSimContainment = Rx {
//    val graph = rxDisplayGraph().graph
//    val postIdToSimPost = rxPostIdToSimPost()

//    val containments = graph.postIds.flatMap(parentId => graph.descendants(parentId).map(childId => Containment(parentId, childId)))

//    containments.map { c =>
//      new SimContainment(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
//    }.toJSArray
//  }

//  val rxSimCollapsedContainment = Rx {
//    val postIdToSimPost = rxPostIdToSimPost()

//    rxDisplayGraph().collapsedContainments.map { c =>
//      new SimCollapsedContainment(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
//    }.toJSArray
//  }

//  val rxContainmentCluster = Rx {
//    val graph = rxDisplayGraph().graph
//    val postIdToSimPost = rxPostIdToSimPost()

//    // due to transitive containment visualisation,
//    // inner posts should be drawn above outer ones.
//    val ordered = graph.allParentIdsTopologicallySortedByChildren

//    ordered.map { p =>
//      new ContainmentCluster(
//        parent = postIdToSimPost(p),
//        children = graph.descendants(p).map(p => postIdToSimPost(p))(breakOut),
//        depth = graph.childDepth(p)
//      )
//    }.toJSArray
//  }

//  val rxCollapsedContainmentCluster = Rx {
//    val graph = rxDisplayGraph().graph
//    val postIdToSimPost = rxPostIdToSimPost()

//    val children: Map[PostId, Seq[PostId]] = rxDisplayGraph().collapsedContainments.groupBy(_.parentId).mapValues(_.map(_.childId)(breakOut))
//    val parents: Iterable[PostId] = children.keys

//    parents.map { p =>
//      new ContainmentCluster(
//        parent = postIdToSimPost(p),
//        children = children(p).map(p => postIdToSimPost(p))(breakOut),
//        depth = graph.childDepth(p)
//      )
//    }.toJSArray
//  }

//  DevOnly {
//    rxSimPosts.debug(v => s"  simPosts: ${v.size}")
//    rxPostIdToSimPost.debug(v => s"  postIdToSimPost: ${v.size}")
//    rxSimConnection.debug(v => s"  simConnection: ${v.size}")
//    rxSimContainment.debug(v => s"  simContainment: ${v.size}")
//    rxContainmentCluster.debug(v => s"  containmentCluster: ${v.size}")
//    rxFocusedSimPost.rx.debug(v => s"  focusedSimPost: ${v.size}")
//  }
//}
