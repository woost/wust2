package wust.bench

import scala.concurrent.duration._
import wust.ids.Cuid
import bench._
import bench.util._
import wust.util.algorithm
import flatland.NestedArrayInt
import scala.collection.mutable
import scala.scalajs.js

object MapBenchmarks {

  val hashmap = Comparison("Map", Seq(
    Benchmark[(js.Dictionary[Int], Array[Cuid])](
      "js dictionary",
      { n =>
        val map = js.Dictionary[Int]()
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreach { i => map += i.toStringFast -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i.toStringFast) }
      }
    ),
    Benchmark[(mutable.HashMap[Cuid, Int], Array[Cuid])](
      "hashmap",
      { n =>
        val map = new mutable.HashMap[Cuid, Int]
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
    Benchmark[(Map[Cuid, Int], Array[Cuid])](
      "map",
      { n =>
        var map = Map[Cuid, Int]()
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    )
  ))

  val hashmapBuild = Comparison("MapBuild", Seq(
    Benchmark[Int](
      "js dictionary",
      n => n,
      { n =>
        val map = js.Dictionary[Int]()
        (0 to n).foreach { i => map += Cuid(i,i).toStringFast -> i }
        map
      },
    ),
    Benchmark[Int](
      "hashmap",
      n => n,
      { n =>
        val map = new mutable.HashMap[Cuid, Int]
        map.sizeHint(n)
        (0 to n).foreach { i => map += Cuid(i,i) -> i }
        map
      },
    ),
    Benchmark[Int](
      "map",
      n => n,
      { n =>
        var map = Map[Cuid, Int]()
        (0 to n).foreach { i => map += Cuid(i, i) -> i }
        map
      },
    )
  ))

}
