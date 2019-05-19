package wust.bench

import bench._
import flatland._
import wust.util.algorithm


object CuidBenchmarks {
  val serialization = Comparison("Cuid serialization", {
    import wust.ids._
    Seq(
      Benchmark[Array[Cuid]]("toCuidString",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toCuidString
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toUuid",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toUuid.toString
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toBase58",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toBase58
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("toHex",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.toHex
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("string-plus",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = cuid.lefthi.toString + "|" + cuid.leftlo.toString + "|" + cuid.righthi.toString + "|" + cuid.rightlo.toString
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("stringbuilder",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            val sb = new StringBuilder
            sb.append(cuid.lefthi)
            sb.append("|")
            sb.append(cuid.leftlo)
            sb.append("|")
            sb.append(cuid.righthi)
            sb.append("|")
            sb.append(cuid.rightlo)
            s = sb.result
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("format",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = s"${cuid.lefthi}|${cuid.leftlo}|${cuid.righthi}|${cuid.rightlo}"
          }
          s
        }
      ),
      Benchmark[Array[Cuid]]("perfolation",
        { size =>
          Array.fill(size)(Cuid.fromCuidString(cuid.Cuid()).right.get)
        },
        { (cuids) =>
          import perfolation._
          var s: String = ""
          cuids.foreachElement { cuid =>
            s = p"${cuid.lefthi}|${cuid.leftlo}|${cuid.righthi}|${cuid.rightlo}"
          }
          s
        }
      ),
    )
  })
}
