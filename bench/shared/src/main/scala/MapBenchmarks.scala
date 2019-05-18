package wust.bench

import bench.{Benchmark, Comparison}
import wust.ids.Cuid
import wust.util.collection.BasicMap
import flatland._

import scala.collection.mutable

object MapBenchmarks {

  val stringMapLookup = Comparison("String Map Lookup", Seq(
//    Benchmark[(js.Dictionary[Int], Array[String])](
//      "js.Dictionary",
//      { n =>
//        val map = js.Dictionary[Int]()
//        val arr = Array.tabulate(n)(_.toString)
//        arr.foreach { i => map += i -> 1 }
//        (map, arr)
//      },
//      { case (map, arr) =>
//        arr.foreach { i => map(i) }
//      }
//    ),
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
    Benchmark[(Map[String, Int], Array[String])](
      "map",
      { n =>
        var map = Map.empty[String, Int]
        val arr = Array.tabulate(n)(_.toString)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
  ))

  val stringMapConstruct = Comparison("String Map Construct", Seq(
//    Benchmark[Array[String]](
//      "js dictionary",
//      n => Array.tabulate(n)(_.toString),
//      { arr =>
//        val map = js.Dictionary[Int]()
//        arr.foreachIndexAndElement { (i, str) => map += str -> i }
//        map
//      },
//    ),
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
    Benchmark[Array[String]](
      "map",
      n => Array.tabulate(n)(_.toString),
      { arr =>
        var map = Map.empty[String, Int]
        arr.foreachIndexAndElement { (i, str) => map += str -> i }
        map
      },
    ),
  ))

  val cuidMapLookup = Comparison("Cuid Map Construct", Seq(
//    Benchmark[(js.Dictionary[Int], Array[Cuid])](
//      "js.Dictionary stringfast",
//      { n =>
//        val map = js.Dictionary[Int]()
//        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
//        arr.foreach { i => map += i.toStringFast -> 1 }
//        (map, arr)
//      },
//      { case (map, arr) =>
//        arr.foreach { i => map(i.toStringFast) }
//      }
//    ),
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
    Benchmark[(Map[Cuid, Int], Array[Cuid])](
      "map",
      { n =>
        var map = Map.empty[Cuid, Int]
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreach { i => map += i -> 1 }
        (map, arr)
      },
      { case (map, arr) =>
        arr.foreach { i => map(i) }
      }
    ),
  ))

  val cuidMapConstruct = Comparison("Cuid Map Construct", Seq(
//    Benchmark[Array[Cuid]](
//      "js dictionary stringfast",
//      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
//      { arr =>
//        val map = js.Dictionary[Int]()
//        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
//        map
//      },
//    ),
    Benchmark[Array[Cuid]](
      "basicmap stringfast",
      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
      { arr =>
        val map = BasicMap.ofString[Int]()
        arr.foreachIndexAndElement { (i, cuid) => map += cuid.toStringFast -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "hashmap",
      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
      { arr =>
        val map = new mutable.HashMap[Cuid, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "treemap",
      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
      { arr =>
        val map = new mutable.TreeMap[Cuid, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "map",
      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
      { arr =>
        val map = new mutable.TreeMap[Cuid, Int]
        map.sizeHint(arr.length)
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
    Benchmark[Array[Cuid]](
      "map",
      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
      { arr =>
        var map = Map.empty[Cuid, Int]
        arr.foreachIndexAndElement { (i, cuid) => map += cuid -> i }
        map
      },
    ),
  ))

}
