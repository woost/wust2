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
  //TODO: less-angry rainbow? https://bl.ocks.org/mbostock/310c99e53880faec2434

  def post(postId:PostId) = twoMirror(postId, 10)
  def user(postId:UserId) = verticalMirror(postId, 5)

  private val accentColorDeltas = Array[Double](-1, 0, 0, 0, 0, 1)
  @inline private def accentColorLookup(r:scala.util.Random) = accentColorDeltas(r.nextInt(accentColorDeltas.size))
  @inline private def addPixel(pixels:mutable.ArrayBuffer[VNode], x:Int, y:Int, color:String):Unit = {
    import outwatch.dom.dsl.svg
    pixels += svg.rect(
      dsl.svg.x := x.toString,
      dsl.svg.y := y.toString,
      svg.width := "1",
      svg.height := "1",
      svg.fill := color
    )
  }

  def verticalMirror(seed: Any, n: Int): VNode = {
    import outwatch.dom.dsl.svg.{svg, rect, fill, width, height, viewBox}
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable

    val hue = genericBaseHue(seed)
    // every avatar has exactly two possible accent-colors, which are base -+ 63deg
    def accentDelta = accentColorLookup(rnd)
    val area = n*n
    val half = (n / 2) + (n % 2)

    val pixels = new mutable.ArrayBuffer[VNode](initialSize = area)

    // mirror on y axis
    var x = 0
    var y = 0
    while(y < n) {
      x = 0
      while(x < half) {
        if(rnd.nextBoolean()) {
          val color = HCL(hue + accentDelta, 60, 65).toHex
          addPixel(pixels, x,y, color)
          addPixel(pixels, n-x-1,y, color)
        }
        x+=1
      }
      y+=1
    }

    svg(
      viewBox := s"0 0 $n $n",
      pixels
    )
  }

  def twoMirror(seed: Any, n: Int): VNode = {
    import outwatch.dom.dsl.svg.{svg, rect, fill, width, height, viewBox}
    val rnd = new scala.util.Random(new scala.util.Random(seed.hashCode).nextLong()) // else nextDouble is too predictable

    val hue = genericBaseHue(seed)
    // every avatar has exactly two possible accent-colors, which are base -+ 63deg
    def accentDelta = accentColorLookup(rnd)
    val area = n*n
    val half = (n / 2) + (n % 2)

    val pixels = new mutable.ArrayBuffer[VNode](initialSize = area)

    if(rnd.nextBoolean()) {
      // mirror on x and y axis
      var x = 0
      var y = 0
      while(y < half) {
        x = 0
        while(x < half) {
          if(rnd.nextBoolean()) {
            val color = HCL(hue + accentDelta, 60, 65).toHex
            addPixel(pixels, x,y, color)
            addPixel(pixels, x,n-y-1, color)
            addPixel(pixels, n-x-1,y, color)
            addPixel(pixels, n-x-1,n-y-1, color)
          }
          x+=1
        }
        y+=1
      }
    } else {
      // mirror on diagonals
      var x = 0
      var y = 0
      var startx = 0
      while(y < half) {
        x = startx
        while(x < n - startx) {
          if(rnd.nextBoolean()) {
            val c = HCL(hue + accentDelta, 60, 65).toHex
            addPixel(pixels, x,y, c)
            addPixel(pixels, y,x, c)
            addPixel(pixels, n-y-1,n-x-1, c)
            addPixel(pixels, n-x-1,n-y-1, c)
          }
          x+=1
        }
        startx += 1
        y+=1
      }
    }

    svg(
      viewBox := s"0 0 $n $n",
      pixels
    )
  }
}
