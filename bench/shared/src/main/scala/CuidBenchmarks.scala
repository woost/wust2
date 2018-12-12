package bench

import flatland._
import wust.util.algorithm

object CuidBenchmarks {
  val serialization = Comparison("Cuid serialization", {
    import wust.ids._
    Seq(
      Benchmark[Array[Cuid]]("toCuidString",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()))
        },
        { (cuids, _) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toCuidString
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toUuid",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()))
        },
        { (cuids, _) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toUuid.toString
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toBase58",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()))
        },
        { (cuids, _) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toBase58
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toHex",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()))
        },
        { (cuids, _) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toHex
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toStringFast",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()))
        },
        { (cuids, _) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toStringFast
          }
          s
        }
      ),
    )
  })
}
