package wust.util

import flatland._
import org.scalatest._
import wust.util.algorithm._

import scala.collection.mutable

class DepthFirstSearchSpec extends FreeSpec with MustMatchers {

  "depth-first-search" - {
    "one vertex" in {
      val edges = NestedArrayInt(Array(Array[Int]()))
      val traversal = dfs.toArray(_(0), dfs.withStart, edges).toList
      assert(traversal == List(0))
    }

    "two vertices" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int]()))
      val traversal = dfs.toArray(_(0), dfs.withStart, edges).toList
      assert(traversal == List(0, 1))
    }

    "directed cycle" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int](0)))
      val traversal = dfs.toArray(_(0), dfs.withStart, edges).toList
      assert(traversal == List(0, 1))
    }
  }

  "depth-first-search afterStart" - {
    "one vertex" in {
      val edges = NestedArrayInt(Array(Array[Int]()))
      val traversal = dfs.toArray(_(0), dfs.afterStart, edges).toList
      assert(traversal == List())
    }

    "two vertices" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int]()))
      val traversal = dfs.toArray(_(0), dfs.afterStart, edges).toList
      assert(traversal == List(1))
    }

    "directed cycle" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int](0)))
      val traversal = dfs.toArray(_(0), dfs.afterStart, edges).toList
      assert(traversal == List(1, 0))
    }

    "undirected cycle (diamond) - sink first" in {
      val edges = NestedArrayInt(Array(
        /* 0 -> */ Array(1, 2),
        /* 1 -> */ Array(2),
        /* 2 -> */ Array[Int]()
      ))

      val traversal = dfs.toArray(_(0), dfs.afterStart, edges).toList
      assert(traversal == List(2, 1))
    }

    "undirected cycle (diamond) - sink last" in {
      val edges = NestedArrayInt(Array(
        /* 0 -> */ Array(2, 1),
        /* 1 -> */ Array(2),
        /* 2 -> */ Array[Int]()
      ))

      val traversal = dfs.toArray(_(0), dfs.afterStart, edges).toList
      assert(traversal == List(1, 2))
    }

    "skip multiple starts" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val traversal = dfs.toArray(Array(0, 2).foreachElement, dfs.afterStart, edges).toList
      assert(traversal == List(3, 4, 1))
    }
  }

  "depth-first-search withoutStart" - {
    "directed cycle" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int](0)))
      val traversal = dfs.toArray(_(0), dfs.withoutStart, edges).toList
      assert(traversal == List(1))
    }

    "undirected cycle (diamond) - sink first" in {
      val edges = NestedArrayInt(Array(
        /* 0 -> */ Array(1, 2),
        /* 1 -> */ Array(2),
        /* 2 -> */ Array[Int]()
      ))

      val traversal = dfs.toArray(_(0), dfs.withoutStart, edges).toList
      assert(traversal == List(2, 1))
    }

    "undirected cycle (diamond) - sink last" in {
      val edges = NestedArrayInt(Array(
        /* 0 -> */ Array(2, 1),
        /* 1 -> */ Array(2),
        /* 2 -> */ Array[Int]()
      ))

      val traversal = dfs.toArray(_(0), dfs.withoutStart, edges).toList
      assert(traversal == List(1, 2))
    }

  }

  "depth-first-search exists" - {
    "one vertex, found" in {
      val edges = NestedArrayInt(Array(Array[Int]()))
      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = _ == 0)
      assert(traversal == true)
    }
    "one vertex, not found" in {
      val edges = NestedArrayInt(Array(Array[Int]()))
      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = _ == 1)
      assert(traversal == false)
    }
    "traversed => found" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = _ == 4)
      assert(traversal == true)
    }
    "not traversed => not found" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = _ == 3)
      assert(traversal == false)
    }
    "not traversed => not found because excluded" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val excluded = ArraySet.create(5)
      excluded += 1

      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = _ == 4, isIncluded = excluded.containsNot)
      assert(traversal == false)
    }
  }

  "depth-first-search exists arraySet" - {
    "not traversed => found" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val set = ArraySet.create(5)
      set += 2
      set += 3
      set += 4
      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = set.contains _)
      assert(traversal == true)
    }
    "not traversed => not found" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val set = ArraySet.create(5)
      set += 2
      set += 3
      val traversal = dfs.exists(_(0), dfs.withStart, edges, isFound = set.contains _)
      assert(traversal == false)
    }
  }

  "depth-first-search exists after start" - {
    "one vertex, found" in {
      val edges = NestedArrayInt(Array(Array[Int]()))
      val traversal = dfs.exists(_(0), dfs.afterStart, edges, isFound = _ == 0)
      assert(traversal == false)
    }
    "one vertex, not found" in {
      val edges = NestedArrayInt(Array(Array[Int]()))
      val traversal = dfs.exists(_(0), dfs.afterStart, edges, isFound = _ == 1)
      assert(traversal == false)
    }
    "traversed => found" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val traversal = dfs.exists(_(0), dfs.afterStart, edges, isFound = _ == 4)
      assert(traversal == true)
    }
    "not traversed => not found" in {
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1), // 0 -> 1-\
        /* 1 */ Array[Int](4), //         4
        /* 2 */ Array[Int](3), // 2 -> 3-/
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val traversal = dfs.exists(_(0), dfs.afterStart, edges, isFound = _ == 3)
      assert(traversal == false)
    }
  }

  "depth-first-search manual append" - {
    "two vertices" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int]()))
      val result = new mutable.ArrayBuilder.ofInt
      dfs.withManualAppend(_(0), dfs.withStart, edges, elem => result += elem)
      val traversal = result.result().toList
      assert(traversal == List(0, 1))
    }
  }

  "depth-first-search manual append stop if false" - {
    "four vertices" in {
      val edges = NestedArrayInt(Array(Array[Int](1), Array[Int](2), Array[Int](3), Array[Int]()))
      val result = new mutable.ArrayBuilder.ofInt
      dfs.withManualAppendStopIfAppendFalse(0, edges, { elem =>
        result += elem
        elem < 2
      })
      val traversal = result.result().toList
      assert(traversal == List(0, 1, 2))
    }
  }

  "depth-first-search manual append skip if false" - {
    "only follow even" in {
      //  /-1-3\    <-- odd
      // 0      4
      //  \-2--/    <-- even
      val edges = NestedArrayInt(Array(
        /* 0 */ Array[Int](1, 2),
        /* 1 */ Array[Int](3),
        /* 2 */ Array[Int](4),
        /* 3 */ Array[Int](4),
        /* 4 */ Array[Int](),
      ))
      val result = new mutable.ArrayBuilder.ofInt
      dfs.withManualAppendSkipIfAppendFalse(0, edges, { elem =>
        result += elem
        elem % 2 == 0
      })
      val traversal = result.result().toList
      assert(traversal == List(0, 2, 4, 1))
    }
  }

  "depth first search cycles" - {
    "directed cycle" in {
      val edges = NestedArrayInt(Array(
        /* 0 -> */ Array(1),
        /* 1 -> */ Array(2),
        /* 2 -> */ Array(0, 3),
        /* 3 -> */ Array[Int]()
      ))

      val traversal = containmentsOfCycle(Array(0, 1, 2, 3), edges).toList
      assert(traversal == List(0, 1, 2))
    }

  }
}
