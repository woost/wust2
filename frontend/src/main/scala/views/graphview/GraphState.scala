package wust.frontend.views.graphview

import scalajs.js
import js.JSConverters._
import collection.breakOut
import rx._, rxext._

import wust.frontend.{DevOnly, GlobalState}
import wust.graph._
import wust.frontend.Color._
import wust.util.collection._
import wust.util.Pipe

class GraphState(state: GlobalState)(implicit ctx: Ctx.Owner) {
  val rxGraph = state.graph
  val rxEditedPostId = state.editedPostId
  val rxCollapsedPostIds = state.collapsedPostIds

  val rxSimPosts: Rx[js.Array[SimPost]] = Rx {
    val graph = rxGraph()
    val collapsedPostIds = rxCollapsedPostIds()

    graph.posts.map { p =>
      val sp = new SimPost(p)

      def parents = graph.parents(p.id)
      def hasParents = parents.nonEmpty
      def mixedDirectParentColors = mixColors(parents.map(baseColor))
      def hasChildren = graph.children(p.id).nonEmpty
      //TODO: move border and color to views.post()
      sp.border = (
        if (hasChildren)
          "2px solid rgba(0,0,0,0.4)"
        else { // no children
          "2px solid rgba(0,0,0,0.2)"
        }
      ).toString()
      sp.color = (
        //TODO collapsedPostIds is not sufficient for being a parent (butt currently no knowledge about collapsed children in graph)
        if (hasChildren || collapsedPostIds(p.id))
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
  }

  val rxPostIdToSimPost: Rx[Map[PostId, SimPost]] = rxSimPosts.fold(Map.empty[PostId, SimPost]) {
    (previousMap, simPosts) =>
      (simPosts: js.ArrayOps[SimPost]).by(_.id) ||> (_.foreach {
        case (id, simPost) =>
          previousMap.get(id).foreach { previousSimPost =>
            // preserve position, velocity and fixed position
            simPost.x = previousSimPost.x
            simPost.y = previousSimPost.y
            simPost.vx = previousSimPost.vx
            simPost.vy = previousSimPost.vy
            simPost.fx = previousSimPost.fx
            simPost.fy = previousSimPost.fy
          }
      })
  }

  //TODO: multiple menus for multi-user multi-touch interface?
  val rxFocusedSimPost = RxVar(state.focusedPostId, Rx { state.focusedPostId().flatMap(rxPostIdToSimPost().get) })
  // val rxFocusedSimPost = state.focusedPostId.combine { fp => fp.flatMap(postIdToSimPost().get) } // TODO: Possible? See RxExt

  val rxSimConnects = Rx {
    val graph = rxGraph()
    val postIdToSimPost = rxPostIdToSimPost()

    val newData = graph.connections.map { c =>
      new SimConnects(c, postIdToSimPost(c.sourceId))
    }.toJSArray

    val connIdToSimConnects: Map[ConnectsId, SimConnects] = (newData: js.ArrayOps[SimConnects]).by(_.id)

    // set hyperedge targets, goes away with custom linkforce
    newData.foreach { e =>
      e.target = e.targetId match {
        case id: PostId => postIdToSimPost(id)
        case id: ConnectsId => connIdToSimConnects(id)
        case id: ConnectableId => throw new Exception("Unresolved ConnectableId found. Should not happen in consistent graph.")
      }
    }

    newData
  }

  val rxSimContains = Rx {
    val graph = rxGraph()
    val postIdToSimPost = rxPostIdToSimPost()

    graph.containments.map { c =>
      new SimContains(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
    }.toJSArray
  }

  val rxContainmentCluster = Rx {
    val graph = rxGraph()
    val postIdToSimPost = rxPostIdToSimPost()

    val parents: Seq[PostId] = graph.containments.map(c => c.parentId)(breakOut).distinct

    // due to transitive containment visualisation,
    // inner posts should be drawn above outer ones.
    val ordered = parents.topologicalSortBy(graph.children)

    ordered.map { p =>
      new ContainmentCluster(
        parent = postIdToSimPost(p),
        children = graph.transitiveChildren(p).map(p => postIdToSimPost(p))(breakOut),
        depth = graph.depth(p)
      )
    }.toJSArray
  }

  DevOnly {
    rxSimPosts.debug(v => s"  simPosts: ${v.map(_.id).toSeq.sorted.mkString(",")}")
    rxPostIdToSimPost.debug(v => s"  postIdToSimPost: ${v.keys.toSeq.sorted.mkString(",")}")
    rxSimConnects.debug(v => s"  simConnects: ${v.map(_.id).toSeq.sorted.mkString(",")}")
    rxSimContains.debug(v => s"  simContains: ${v.map(_.id).toSeq.sorted.mkString(",")}")
    rxContainmentCluster.debug(v => s"  containmentCluster: ${v.map(_.id).toSeq.sorted.mkString(",")}")
    rxFocusedSimPost.rx.debug(v => s"  focusedSimPost: ${v.map(sp => s"${sp.id}: ${sp.title}")}")
  }
}
