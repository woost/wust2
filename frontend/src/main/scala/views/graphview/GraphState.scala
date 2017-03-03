package frontend.views.graphview

import scalajs.js
import js.JSConverters._
import util.collection._
import collection.breakOut
import rx._
import frontend.RxVar._

import frontend.GlobalState
import graph._
import frontend.Color._
import util.Pipe

class GraphState(state: GlobalState)(implicit ctx: Ctx.Owner) {
  val rxGraph = state.graph
  val editedPostId = state.editedPostId
  val collapsedPostIds = state.collapsedPostIds

  val rxSimPosts: Rx[js.Array[SimPost]] = for {
    graph <- rxGraph
    collapsedPostIds <- collapsedPostIds
  } yield {
    graph.posts.values.map { p =>
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

  val postIdToSimPost: Rx[Map[AtomId, SimPost]] = rxSimPosts.fold(Map.empty[AtomId, SimPost]) {
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
  val focusedPost = for {
    idOpt <- state.focusedPostId
    map <- postIdToSimPost
  } yield idOpt.flatMap(map.get)

  val rxSimConnects = for {
    graph <- rxGraph
    postIdToSimPost <- postIdToSimPost
  } yield {
    val newData = graph.connections.values.map { c =>
      new SimConnects(c, postIdToSimPost(c.sourceId))
    }.toJSArray

    val connIdToSimConnects: Map[AtomId, SimConnects] = (newData: js.ArrayOps[SimConnects]).by(_.id)

    // set hyperedge targets, goes away with custom linkforce
    newData.foreach { e =>
      e.target = postIdToSimPost.getOrElse(e.targetId, connIdToSimConnects(e.targetId))
    }

    newData
  }

  val rxSimContains = for {
    newGraph <- rxGraph
    postIdToSimPost <- postIdToSimPost
  } yield {
    newGraph.containments.values.map { c =>
      new SimContains(c, postIdToSimPost(c.parentId), postIdToSimPost(c.childId))
    }.toJSArray
  }

  val rxContainmentCluster = for {
    graph <- rxGraph
    postIdToSimPost <- postIdToSimPost
  } yield {
    val containments = graph.containments.values
    val parents: Seq[AtomId] = containments.map(c => c.parentId)(breakOut).distinct

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

  rxSimPosts.foreach(v => println(s"simPosts update"))
  postIdToSimPost.foreach(v => println(s"postIdToSimPost update"))
  for (v <- focusedPost) { println(s"focusedSimPost: ${v.map(sp => s"${sp.id}: ${sp.title}")}") }
}
