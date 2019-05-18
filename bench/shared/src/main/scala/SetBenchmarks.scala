package wust.bench

import bench.{Benchmark, Comparison}
import wust.ids.Cuid
import wust.util.collection.BasicSet
import flatland._

import scala.collection.mutable

object SetBenchmarks {

  val stringSetLookup = Comparison("String Set Lookup", Seq(
//    Benchmark[(BasicSet[String], Array[String])](
//      "basicset",
//      { n =>
//        val set = BasicSet.ofString()
//        val arr = Array.tabulate(n)(_.toString)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
//    Benchmark[(mutable.HashSet[String], Array[String])](
//      "hashset",
//      { n =>
//        val set = new mutable.HashSet[String]
//        val arr = Array.tabulate(n)(_.toString)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
//    Benchmark[(mutable.TreeSet[String], Array[String])](
//      "treeset",
//      { n =>
//        val set = new mutable.TreeSet[String]
//        val arr = Array.tabulate(n)(_.toString)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
//    Benchmark[(Set[String], Array[String])](
//      "set",
//      { n =>
//        var set = Set.empty[String]
//        val arr = Array.tabulate(n)(_.toString)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
    Benchmark[(Array[String], Array[String])](
      "array",
      { n =>
        val set = new Array[String](n)
        val arr = Array.tabulate(n)(_.toString)
        arr.foreachIndexAndElement { (idx, i) => set(idx) = i }
        (set, arr)
      },
      { case (set, arr) =>
        arr.foreach { i => set.exists(_ == i) } // exists instead of contains because faster
      }
    ),
  ))

  val stringSetConstruct = Comparison("String Set Construct", Seq(
//    Benchmark[Array[String]](
//      "basicset",
//      n => Array.tabulate(n)(_.toString),
//      { arr =>
//        val map = BasicSet.ofString(arr.length)
//        arr.foreach { str => map += str }
//        map
//      },
//    ),
//    Benchmark[Array[String]](
//      "hashset",
//      n => Array.tabulate(n)(_.toString),
//      { arr =>
//        val set = new mutable.HashSet[String]
//        set.sizeHint(arr.length)
//        arr.foreach { str => set += str }
//        set
//      },
//    ),
//    Benchmark[Array[String]](
//      "treeset",
//      n => Array.tabulate(n)(_.toString),
//      { arr =>
//        val set = new mutable.TreeSet[String]
//        set.sizeHint(arr.length)
//        arr.foreach { str => set += str }
//        set
//      },
//    ),
//    Benchmark[Array[String]](
//      "set",
//      n => Array.tabulate(n)(_.toString),
//      { arr =>
//        var set = Set.empty[String]
//        arr.foreach { str => set += str }
//        set
//      },
//    ),
    Benchmark[Array[String]](
      "array",
      n => Array.tabulate(n)(_.toString),
      { arr =>
        var set = new Array[String](arr.length)
        arr.foreachIndexAndElement { (idx, i) => set(idx) = i }
        set
      },
    ),
  ))

  val cuidSetLookup = Comparison("Cuid Set Lookup", Seq(
//    Benchmark[(BasicSet[String], Array[Cuid])](
//      "basicset stringfast",
//      { n =>
//        val set = BasicSet.ofString()
//        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
//        arr.foreach { i => set += i.toStringFast }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i.toStringFast) }
//      }
//    ),
//    Benchmark[(mutable.HashSet[Cuid], Array[Cuid])](
//      "hashset",
//      { n =>
//        val set = new mutable.HashSet[Cuid]
//        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
//    Benchmark[(mutable.TreeSet[Cuid], Array[Cuid])](
//      "treeset",
//      { n =>
//        val set = new mutable.TreeSet[Cuid]
//        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
//    Benchmark[(Set[Cuid], Array[Cuid])](
//      "set",
//      { n =>
//        var set = Set.empty[Cuid]
//        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
//        arr.foreach { i => set += i }
//        (set, arr)
//      },
//      { case (set, arr) =>
//        arr.foreach { i => set(i) }
//      }
//    ),
    Benchmark[(Array[Cuid], Array[Cuid])](
      "array",
      { n =>
        val set = new Array[Cuid](n)
        val arr = Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        arr.foreachIndexAndElement { (idx, i) => set(idx) = i }
        (set, arr)
      },
      { case (set, arr) =>
        arr.foreach { i => set.exists(_ == i) } // exists instead of contains because faster
      }
    ),
  ))

  val cuidSetConstruct = Comparison("Cuid Set Construct", Seq(
//    Benchmark[Array[Cuid]](
//      "basicset stringfast",
//      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
//      { arr =>
//        val set = BasicSet.ofString(arr.length)
//        arr.foreach { cuid => set += cuid.toStringFast }
//        set
//      },
//    ),
//    Benchmark[Array[Cuid]](
//      "hashset",
//      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
//      { arr =>
//        val set = new mutable.HashSet[Cuid]
//        set.sizeHint(arr.length)
//        arr.foreach { cuid => set += cuid }
//        set
//      },
//    ),
//    Benchmark[Array[Cuid]](
//      "treeset",
//      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
//      { arr =>
//        val set = new mutable.TreeSet[Cuid]
//        set.sizeHint(arr.length)
//        arr.foreach { cuid => set += cuid }
//        set
//      },
//    ),
//    Benchmark[Array[Cuid]](
//      "set",
//      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
//      { arr =>
//        var set = Set.empty[Cuid]
//        arr.foreach { cuid => set += cuid }
//        set
//      },
//    ),
    Benchmark[Array[Cuid]](
      "array",
      n => Array.fill(n)(Cuid.fromCuidString(cuid.Cuid()).right.get),
      { arr =>
        val set = new Array[Cuid](arr.length)
        arr.foreachIndexAndElement { (idx, cuid) => set(idx) = cuid }
        set
      },
    ),
  ))

}
