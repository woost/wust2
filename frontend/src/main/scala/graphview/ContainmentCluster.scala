package frontend.graphview

import graph._
import math._
import collection.breakOut

import scalajs.js
import org.scalajs.d3v4.force._
import vectory._

import org.scalajs.d3v4._
import org.scalajs.d3v4.polygon._

class ContainmentCluster(val parent: SimPost, val children: IndexedSeq[SimPost]) {
  def positions: js.Array[js.Array[Double]] = (children :+ parent).map(post => js.Array(post.x.asInstanceOf[Double], post.y.asInstanceOf[Double]))(breakOut)
  def convexHull: js.Array[js.Array[Double]] = {
    val hull = d3.polygonHull(positions)
    //TODO: how to correctly handle scalajs union type?
    if (hull == null) positions
    else hull.asInstanceOf[js.Array[js.Array[Double]]]
  }
}
