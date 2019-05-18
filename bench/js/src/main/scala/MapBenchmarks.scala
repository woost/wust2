package wust.bench

import scala.concurrent.duration._
import wust.ids.Cuid
import bench._
import bench.util._
import wust.util.algorithm
import flatland.NestedArrayInt
import wust.util.collection.BasicMap
import flatland._

import scala.collection.mutable
import scala.scalajs.js

object MapBenchmarks {

  val map = Comparison("Map", Seq(
    Benchmark[(js.Dictionary[Int], Array[String])](
      "js.Dictionary",
      { n =>
        val map = js.Dictionary[Int]()
        val arr = Array.tabulate(n)(_.toString)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
    Benchmark[(BasicMap[String, Int], Array[String])](
      "basicmap",
      { n =>
        val map = BasicMap.ofString[Int]()
        val arr = Array.tabulate(n)(_.toString)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
    Benchmark[(mutable.HashMap[String, Int], Array[String])](
      "hashmap",
      { n =>
        val map = new mutable.HashMap[String, Int]
        val arr = Array.tabulate(n)(_.toString)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
    Benchmark[(mutable.TreeMap[String, Int], Array[String])](
      "treemap",
      { n =>
        val map = new mutable.TreeMap[String, Int]
        val arr = Array.tabulate(n)(_.toString)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
  ))

  val mapBuild = Comparison("MapBuild", Seq(
    Benchmark[Array[String]](
      "js dictionary",
      n => Array.tabulate(n)(_.toString),
      { arr =>
        val map = js.Dictionary[Int]()
        arr.foreachIndexAndElement { (i, str) => map += str -> i }
        map
      },
    ),
    Benchmark[Array[String]](
      "basicmap",
      n => Array.tabulate(n)(_.toString),
      { arr =>
        val map = BasicMap.ofString[Int]()
        arr.foreachIndexAndElement { (i, str) => map += str -> i }
        map
      },
    ),
    Benchmark[Array[String]](
      "hashmap",
      n => Array.tabulate(n)(_.toString),
      { arr =>
        val map = new mutable.HashMap[String, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, str) => map += str -> i }
        map
      },
    ),
    Benchmark[Array[String]](
      "treemap",
      n => Array.tabulate(n)(_.toString),
      { arr =>
        val map = new mutable.TreeMap[String, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, str) => map += str -> i }
        map
      },
    ),
  ))

  val hashmap = Comparison("HashMap", Seq(
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
    Benchmark[(mutable.TreeMap[Cuid, Int], Array[Cuid])](
      "treemap",
      { n =>
        val map = new mutable.TreeMap[Cuid, Int]
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
  ))

  val hashmapBuild = Comparison("HashMapBuild", Seq(
    Benchmark[Array[Cuid]](
      "js dictionary stringfast",
      n => Array.tabulate(n)(i => Cuid(i, i + 1)),
      { arr =>
        val map = js.Dictionary[Int]()
        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "basicmap stringfast",
      n => Array.tabulate(n)(i => Cuid(i, i + 1)),
      { arr =>
        val map = BasicMap.ofString[Int]()
        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "hashmap",
      n => Array.tabulate(n)(i => Cuid(i, i + 1)),
      { arr =>
        val map = new mutable.HashMap[Cuid, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "treemap",
      n => Array.tabulate(n)(i => Cuid(i, i + 1)),
      { arr =>
        val map = new mutable.TreeMap[Cuid, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
  ))

}
