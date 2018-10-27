package wust.webApp.views

import java.lang.Math._

import colorado.HCL
import outwatch.dom.{VNode, _}
import wust.graph.Node
import wust.ids._
import wust.sdk.NodeColor.genericHue
import wust.util.Memo
import wust.webApp.outwatchHelpers._

import scala.collection.mutable
import scala.scalajs.js

object Avatar {
  //TODO: less-angry rainbow? https://bl.ocks.org/mbostock/310c99e53880faec2434

  def apply(n:Node): VNode = {
    n match {
      case n:Node.Content => node(n.id)
      case n:Node.User => user(n.id)
    }
  }
  val node = Memo.mutableHashMapMemo { nodeId: NodeId =>
    twoMirror(nodeId, 8)
  }
  val user = Memo.mutableHashMapMemo { userId: UserId =>
    verticalMirror(userId, 5)
  }

  val PI2 = PI * 2

  private def accentColorSelection(hue1: Double, rnd: scala.util.Random): Array[String] = {
    // select two more hues randomly with minimum padding
    val padding = 1

    //      [padding]
    //    0 |       |      PI2
    // ---|-]xxx|xxx[-------|-]xxx|xxx[-------|---
    //        base  |         | base+PI2
    //              lower     upper + PI2
    //              [  range  ]
    //             ]xxx|xxx[
    //                hue2

    val lowerBound = hue1 + padding
    val upperBound = hue1 - padding + PI2
//    assert(lowerBound > 0)
//    assert(upperBound > 0)
//    assert(upperBound > lowerBound)
    val range = upperBound - lowerBound
    val hue2 = lowerBound + rnd.nextDouble() * range

    // 2 possible intervals left for third hue:
    val lowerBound2Left = lowerBound
    val upperBound2Left = hue2 - padding
    val rangeLeft = { // check if there is space on the left of hue2
      val range = upperBound2Left - lowerBound2Left
      if (range < 0) 0 else range
    }

    val lowerBound2Right = hue2 + padding
    val upperBound2Right = upperBound
    val rangeRight = { // check if there is space on the right of hue2
      val range = upperBound2Right - lowerBound2Right
      if (range < 0) 0 else range
    }

    val rand = rnd.nextDouble() * (rangeLeft + rangeRight)
    val hue3 =
      if (rand < rangeLeft) lowerBound2Left + rand
      else lowerBound2Right + (rand - rangeLeft)

//    assert((hue1 - hue2).abs >= padding)
//    assert((hue1 - hue3).abs >= padding)
//    assert((hue2 - hue3).abs >= padding)
//    assert((hue1 - (hue2 - PI2)).abs >= padding)
//    assert((hue1 - (hue3 - PI2)).abs >= padding)
//    assert((hue2 - (hue3 - PI2)).abs >= padding)

    @inline def c = 60
    @inline def l = 65
    val col1 = HCL(hue1, c, l).toHex
    val col2 = HCL(hue2, c, l).toHex
    val col3 = HCL(hue3, c, l).toHex
    Array(col1, col1, col1, col1, col2, col3)
  }

  @inline private def randomElement(array: Array[String], rnd: scala.util.Random) =
    array(rnd.nextInt(array.length))

  private val svgWidthOne = dsl.svg.width := "1"
  private val svgHeightOne = dsl.svg.height := "1"
  private def addPixel(pixels: js.Array[VNode], x: Int, y: Int, color: String): Unit = {
    import outwatch.dom.dsl.svg
    pixels push svg.rect(
      dsl.svg.x := x.toString,
      dsl.svg.y := y.toString,
      svgWidthOne,
      svgHeightOne,
      svg.fill := color
    )
  }

  @inline def renderSvg(n:Int, pixels: js.Array[VNode]): VDomModifier = {
    import outwatch.dom.dsl.svg.viewBox
    VDomModifier(
      viewBox := s"0 0 $n $n",
      dsl.style("shape-rendering") := "optimizeSpeed",
      pixels
    )
  }

  def verticalMirror(seed: Any, n: Int): VNode = {
    import outwatch.dom.dsl.svg.svg

    svg.static(keyValue(seed)) {
      val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable

      val half = (n / 2) + (n % 2)

      val pixels = new js.Array[VNode]
      val colors = accentColorSelection(genericHue(seed), rnd)
      @inline def rndColor() = randomElement(colors, rnd)

      // mirror on y axis
      var x = 0
      var y = 0
      while (y < n) {
        x = 0
        while (x < half) {
          if (rnd.nextBoolean()) {
            val color = rndColor()
            addPixel(pixels, x, y, color)
            addPixel(pixels, n - x - 1, y, color)
          }
          x += 1
        }
        y += 1
      }

      renderSvg(n, pixels)
    }
  }

  def twoMirror(seed: Any, n: Int): VNode = {
    import outwatch.dom.dsl.svg.svg

    svg.static(keyValue(seed)) {
      val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable

      val half = (n / 2) + (n % 2)

      val pixels = new js.Array[VNode]
      val colors = accentColorSelection(genericHue(seed), rnd)
      @inline def rndColor() = randomElement(colors, rnd)

      if (rnd.nextBoolean()) {
        // mirror on x and y axis
        var x = 0
        var y = 0
        while (y < half) {
          x = 0
          while (x < half) {
            if (rnd.nextBoolean()) {
              val color = rndColor()
              addPixel(pixels, x, y, color)
              addPixel(pixels, x, n - y - 1, color)
              addPixel(pixels, n - x - 1, y, color)
              addPixel(pixels, n - x - 1, n - y - 1, color)
            }
            x += 1
          }
          y += 1
        }
      } else {
        // mirror on diagonals
        var x = 0
        var y = 0
        var startx = 0
        while (y < half) {
          x = startx
          while (x < n - startx) {
            if (rnd.nextBoolean()) {
              val c = rndColor()
              addPixel(pixels, x, y, c)
              addPixel(pixels, y, x, c)
              addPixel(pixels, n - y - 1, n - x - 1, c)
              addPixel(pixels, n - x - 1, n - y - 1, c)
            }
            x += 1
          }
          startx += 1
          y += 1
        }
      }

      renderSvg(n, pixels)
    }
  }
}
