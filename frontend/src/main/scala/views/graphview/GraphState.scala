package frontend.views.graphview

import scalajs.js
import js.JSConverters._
import util.collectionHelpers._
import collection.breakOut
import mhtml._

import frontend.GlobalState
import graph._
import frontend.Color._

class GraphState(state: GlobalState) {
  val rxGraph = state.graph
  val editedPostId = state.editedPostId
  val collapsedPostIds = state.collapsedPostIds

  val rxSimPosts: Rx[js.Array[SimPost]] = rxGraph.map { graph =>
    graph.posts.values.map { p =>
      val sp = new SimPost(p)
      postIdToSimPost.value.get(sp.id).foreach { old =>
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
          "2px solid rgba(0,0,0,0.2)"
        }
      ).toString()
      sp.color = (
        //TODO collapsedPostIds is not sufficient for being a parent (butt currently no knowledge about collapsed children in graph)
        if (hasChildren || collapsedPostIds.value(p.id))
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

  val postIdToSimPost: Rx[Map[AtomId, SimPost]] = rxSimPosts.map(nd => (nd: js.ArrayOps[SimPost]).by(_.id))

  //TODO: multiple menus for multi-user multi-touch interface?
  val focusedPost = for {
    idOpt <- state.focusedPostId
    map <- postIdToSimPost
  } yield idOpt.flatMap(map.get)

  val rxSimConnects = for {
    postIdToSimPost <- postIdToSimPost
    graph <- rxGraph
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

  val rxContainmentCluster = rxGraph.map { graph =>
    val containments = graph.containments.values
    val parents: Seq[Post] = containments.map(c => graph.posts(c.parentId)).toSeq.distinct

    // due to transitive containment visualisation,
    // inner posts should be drawn above outer ones.
    val ordered = parents.topologicalSortBy((p: Post) => graph.children(p.id))

    ordered.map { p =>
      new ContainmentCluster(
        parent = postIdToSimPost.value(p.id),
        children = graph.transitiveChildren(p.id).map(p => postIdToSimPost.value(p.id))(breakOut),
        depth = graph.depth(p.id)
      )
    }.toJSArray
  }

  // rxSimPosts.foreach(v => println(s"post rxSimPosts update: $v"))
  // postIdToSimPost.foreach(v => println(s"postIdToSimPost update: $v"))
  // for (v <- focusedPost) { println(s"focusedSimPost update: $v") }
}
