package graph

import scalajs.js
import scala.scalajs.js.annotation._
import vectory._

import org.scalajs.d3v4.force.{SimulationNode => D3Node, SimulationLink => D3Link, SimulationNodeImpl => D3NodeImpl, SimulationLinkImpl => D3LinkImpl}

trait ExtendedD3Node extends D3Node {
  def pos = for (x <- x; y <- y) yield Vec2(x, y)
  def pos_=(newPos: js.UndefOr[Vec2]) {
    if (newPos.isDefined) {
      x = newPos.get.x
      y = newPos.get.y
    } else {
      x = js.undefined
      y = js.undefined
    }
  }
  def vel = for (vx <- vx; vy <- vy) yield Vec2(vx, vy)
  def vel_=(newVel: js.UndefOr[Vec2]) {
    if (newVel.isDefined) {
      vx = newVel.get.x
      vy = newVel.get.y
    } else {
      vx = js.undefined
      vy = js.undefined
    }
  }
  def fixedPos = for (fx <- fx; fy <- fy) yield Vec2(fx, fy)
  def fixedPos_=(newFixedPos: js.UndefOr[Vec2]) {
    if (newFixedPos.isDefined) {
      fx = newFixedPos.get.x
      fy = newFixedPos.get.y
    } else {
      fx = js.undefined
      fy = js.undefined
    }
  }

  var size: Vec2 = Vec2(0, 0)
  def rect = pos.map { pos => AARect(pos, size) }
  var centerOffset: Vec2 = Vec2(0, 0)
  var radius: Double = 0
  var collisionRadius: Double = 0

  var dragStart = Vec2(0, 0)
}

trait PostPlatformSpecificExtensions extends ExtendedD3Node with D3NodeImpl {
  var dragClosest: Option[Post] = None
  var isClosest = false
}

trait ConnectsPlatformSpecificExtensions extends D3Link[Post, ExtendedD3Node] with ExtendedD3Node with D3LinkImpl[Post, ExtendedD3Node] {

  var source: Post = _
  var target: ExtendedD3Node = _

  // propagate d3 gets/sets to incident posts
  def x = for (sx <- source.x; tx <- target.x) yield (sx + tx) / 2
  def x_=(newX: js.UndefOr[Double]) {
    val diff = for (x <- x; newX <- newX) yield (newX - x) / 2
    source.x = for (x <- source.x; diff <- diff) yield x + diff
    target.x = for (x <- target.x; diff <- diff) yield x + diff
  }
  def y = for (sy <- source.y; ty <- target.y) yield (sy + ty) / 2
  def y_=(newY: js.UndefOr[Double]) {
    val diff = for (y <- y; newY <- newY) yield (newY - y) / 2
    source.y = for (y <- source.y; diff <- diff) yield y + diff
    target.y = for (y <- target.y; diff <- diff) yield y + diff
  }
  def vx = for (svx <- source.vx; tvx <- target.vx) yield (svx + tvx) / 2
  def vx_=(newVX: js.UndefOr[Double]) {
    val diff = for (vx <- vx; newVX <- newVX) yield (newVX - vx) / 2
    source.vx = for (vx <- source.vx; diff <- diff) yield vx + diff
    target.vx = for (vx <- target.vx; diff <- diff) yield vx + diff
  }
  def vy = for (svy <- source.vy; tvy <- target.vy) yield (svy + tvy) / 2
  def vy_=(newVY: js.UndefOr[Double]) {
    val diff = for (vy <- vy; newVY <- newVY) yield (newVY - vy) / 2
    source.vy = for (vy <- source.vy; diff <- diff) yield vy + diff
    target.vy = for (vy <- target.vy; diff <- diff) yield vy + diff
  }
  def fx: js.UndefOr[Double] = ???
  def fx_=(newFX: js.UndefOr[Double]) = ???
  def fy: js.UndefOr[Double] = ???
  def fy_=(newFX: js.UndefOr[Double]) = ???
}

trait ContainsPlatformSpecificExtensions extends D3LinkImpl[Post, Post] {
  var source: Post = _
  var target: Post = _
}
