package wust.webApp

import outwatch.dom.VNode
import rx.Ctx
import outwatch.dom.dsl._
import outwatch.dom._
import outwatch._
import Math._
import collection.mutable
import wust.sdk.PostColor.genericBaseHue
import wust.ids._

import colorado.HCL

object Avatar {

  def post(postId:PostId) = apply(postId, 7, true)
  def user(postId:UserId) = apply(postId, 5, false)
  def apply(seed: Any, n: Int, xMirror:Boolean): VNode = {
    import outwatch.dom.dsl.svg.{svg, rect, fill, width, height, viewBox}
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable

    val hue = genericBaseHue(seed)
    // every avatar has exactly two possible accent-colors, which are base -+ 63deg
    def accentDelta = rnd.nextGaussian().toInt.min(1).max(-1) // (1:-1  1:1  4:0), 1 corresponds to 63 deg hue
    val area = n*n

    val fillx = ceil(n / 2.0).toInt
    val filly = if(xMirror) fillx else n
    def index(x:Int,y:Int) = y*n + x
    val color = new Array[String](area)
    var x = 0
    var y = 0
    while(y < filly) {
      x = 0
      while(x < fillx) {
        if(rnd.nextBoolean()) {
          //TODO: less-angry rainbow? https://bl.ocks.org/mbostock/310c99e53880faec2434
          color(index(x,y)) = HCL(hue + accentDelta, 60, 65).toHex
        }
        x+=1
      }
      y+=1
    }

    if(xMirror) {
      y = filly
      while(y < n) {
        x = 0
        while(x < fillx) {
          color(index(x,y)) = color(index(x, n-y-1)) // mirror on x-axis
          x+=1
        }
        y+=1
      }
    }

    y = 0
    while(y < n) {
      x = fillx
      while(x < n) {
        color(index(x,y)) = color(index(n-x-1, y)) // mirror on y-axis
        x+=1
      }
      y+=1
    }

    val pixels = new mutable.ArrayBuffer[VNode](initialSize = area)
    y = 0
    while(y < n) {
      x = 0
      while(x < n) {
        val c = color(index(x,y))
        if(c != null) {
         pixels += rect(
             dsl.svg.x := x.toString,
             dsl.svg.y := y.toString,
             width := "1",
             height := "1",
             fill := c
           )
        }
        x+=1
      }
      y+=1
    }

    // val left = List.tabulate(fillx, filly)((_, _)).flatten
    //   .filter(_ => rnd.nextBoolean()).map { case (x, y) =>
    //   val hue = (hue + rnd.nextGaussian().toInt)%360 // this was an accident! The hue should be 0..2PI
    //   val color = colorado.HCL(hue, 60, 65)

    //   (x,y, color.toHex)
    // }
    // val full = (left ++ left.map{case (x,y,c) => ((n-1)-x, y, c)}).distinct

    svg(
      viewBox := s"0 0 $n $n",
      pixels
      // full.map{case (px,py, c) =>
      //   rect(
      //     dom.dsl.svg.x := s"${px}",
      //     dom.dsl.svg.y := s"${py}",
      //     width := "1",
      //     height := "1",
      //     fill := c
      //   )
      // }
    )
  }
}
