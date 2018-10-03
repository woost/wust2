import org.scalatest._
import wust.util.{NestedArrayInt, SliceInt}
import wust.ids._

class SliceSpec extends FreeSpec with MustMatchers {

  "sliceInt" - {
    "empty" in {
      val slice = new SliceInt(Array(), 0, 0)
      slice.isEmpty mustEqual true
      assertThrows[IndexOutOfBoundsException](slice(0))
    }
    "single element" in {
      val slice = new SliceInt(Array(7), 0, 1)
      slice.size mustEqual 1
      slice(0) mustEqual 7
      slice.toList mustEqual List(7)
      assertThrows[IndexOutOfBoundsException](slice(1))
    }
    "single element on larger array" in {
      val slice = new SliceInt(Array(2,7,5,3,4,6), 3, 2)
      slice.size mustEqual 2
      slice(0) mustEqual 3
      slice(1) mustEqual 4
      slice.toList mustEqual List(3,4)
    }
    "sub slice" in {
      val initial = new SliceInt(Array(2,7,5,3,4,6), 1, 4)
      val slice = initial.slice(2, 4)
      slice.size mustEqual 2
      slice(0) mustEqual 3
      slice(1) mustEqual 4
      slice.toList mustEqual List(3,4)
    }
    "filter" in {
      val slice = new SliceInt(Array(2,7,5,3,4,6), 3, 2)
      val filtered = slice.filter(_ > 3)
      filtered.size mustEqual 1
      filtered(0) mustEqual 4

    }
  }
  "NestedArrayInt" - {
    "empty" in {
      val nested = NestedArrayInt(Array[Array[Int]]())
      nested.isEmpty mustEqual true
      nested.length mustEqual 0
      assertThrows[IndexOutOfBoundsException](nested(0))
    }
    "one empty array" in {
      val nested = NestedArrayInt(Array(Array[Int]()))
      nested.isEmpty mustEqual false
      nested.length mustEqual 1
      nested(0).isEmpty mustEqual true
    }
    "one single-element array" in {
      val nested = NestedArrayInt(Array(Array(13)))
      nested.length mustEqual 1
      nested(0)(0) mustEqual 13
      nested(0,0) mustEqual 13
    }
    "two non-empty arrays" in {
      val nested = NestedArrayInt(Array(Array(7,8,9), Array(1,2,3)))
      nested.length mustEqual 2
      nested(0,1) mustEqual 8
      nested(0)(1) mustEqual 8
      nested(1,1) mustEqual 2
      nested(1)(1) mustEqual 2
      nested(0).toList mustEqual List(7,8,9)
      nested(1).toList mustEqual List(1,2,3)
    }
    "many arrays, some empty" in {
      val nested = NestedArrayInt(Array(Array(3), Array[Int](), Array(0), Array[Int](), Array(0, 1)))
      nested.length mustEqual 5
      nested(0).toList mustEqual List(3)
      nested(1).toList mustEqual List()
      nested(2).toList mustEqual List(0)
      nested(3).toList mustEqual List()
      nested(4).toList mustEqual List(0,1)
    }
    "many arrays, some empty - from builders" in {
      def builder(elems: Int*) = {
        val b = new scala.collection.mutable.ArrayBuilder.ofInt
        b ++= elems
        b
      }
      val nested = NestedArrayInt(Array(builder(3), builder(), builder(0), builder(), builder(0, 1)))
      nested.length mustEqual 5
      nested(0).toList mustEqual List(3)
      nested(1).toList mustEqual List()
      nested(2).toList mustEqual List(0)
      nested(3).toList mustEqual List()
      nested(4).toList mustEqual List(0,1)
    }
  }
}
