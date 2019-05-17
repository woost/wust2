package wust.bench

import scala.concurrent.duration._
import wust.ids.Cuid
import bench._
import bench.util._
import wust.util.algorithm
import flatland.NestedArrayInt
import wust.util.collection.{BasicMap, BasicOpaqueMap}
import flatland._

import scala.collection.mutable
import scala.scalajs.js

object MapBenchmarks {

  val hashmap = Comparison("Map", Seq(
    Benchmark[(js.Dictionary[Int], Array[Cuid])](
      "js.Dictionary stringfast",
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
    Benchmark[(BasicMap[String, Int], Array[Cuid])](
      "basicmap stringfast",
      { n =>
        val map = BasicMap.ofString[Int]()
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreach { i => map += i.toStringFast -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i.toStringFast) }
      }
    ),
    Benchmark[(mutable.HashMap[String, Int], Array[Cuid])](
      "hashmap stringfast",
      { n =>
        val map = new mutable.HashMap[String, Int]
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
  ))

  val hashmapBuild = Comparison("MapBuild", Seq(
    Benchmark[Array[Cuid]](
      "js dictionary stringfast",
      n => Array.tabulate(n)(i => Cuid(i, i, i, i)),
      { arr =>
        val map = js.Dictionary[Int]()
        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "basicmap stringfast",
      n => Array.tabulate(n)(i => Cuid(i, i, i, i)),
      { arr =>
        val map = BasicMap.ofString[Int]()
        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "hashmap stringfast",
      n => Array.tabulate(n)(i => Cuid(i, i, i, i)),
      { arr =>
        val map = new mutable.HashMap[String, Int]()
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "hashmap",
      n => Array.tabulate(n)(i => Cuid(i, i, i, i)),
      { arr =>
        val map = new mutable.HashMap[Cuid, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
  ))

}
