package wust.webApp.search

import wust.graph.{Graph, Node}


object Search {

  private def weightedLevenshtein(d: Double, i: Double, s: Double)(needle: String, haystack: String): Double = {
    val n: Array[Char] = needle.toCharArray
    val h: Array[Char] = haystack.toCharArray

    val m = Array.ofDim[Double](n.length + 1, h.length + 1)

    for (r <- 0 to n.length) m(r)(0) = d * r
    for (c <- 0 to h.length) m(0)(c) = s * c

    for (r <- 1 to n.length; c <- 1 to h.length) {
      m(r)(c) =
        if (n(r - 1) == h(c - 1)) m(r - 1)(c - 1)
        else (m(r - 1)(c) + d).min( // Delete (left).
          (m(r)(c - 1) + i).min( // Insert (up).
            m(r - 1)(c - 1) + s // Substitute (left-up).
          )
        )
    }

    val dist = m(n.length)(h.length)

    1 - dist/h.length
  }

  private def ratcliffObershelp(needle: String, haystack: String): Double = {

    def longestCommonSubsequence(first: Array[Char], second: Array[Char]) = {
      val m = Array.ofDim[Int](first.length + 1, second.length + 1)
      var lrc = (0, 0, 0) // Length, row, column.

      for (r <- 0 to first.length - 1; c <- 0 to second.length - 1) {
        if (first(r) == second(c)) {
          val l = m(r)(c) + 1
          m(r + 1)(c + 1) = l
          if (l > lrc._1) lrc = (l, r + 1, c + 1)
        }
      }
      lrc
    }

    def commonSequences(first: Array[Char], second: Array[Char]): Array[Array[Char]] = {
      val lcs: (Int, Int, Int) = longestCommonSubsequence(first, second)

      if (lcs._1 == 0) Array.empty
      else {
        val sct1: (Array[Char], Array[Char]) = (first.take(lcs._2 - lcs._1), first.takeRight(first.length - lcs._2))
        val sct2: (Array[Char], Array[Char]) = (second.take(lcs._3 - lcs._1), second.takeRight(second.length - lcs._3))


        Array(first.slice(lcs._2 - lcs._1, lcs._2)) ++ commonSequences(sct1._1, sct2._1) ++ commonSequences(sct1._2, sct2._2)
      }
    }

    val n: Array[Char] = needle.toCharArray
    val h: Array[Char] = haystack.toCharArray

    2.0 * commonSequences(n, h).foldLeft(0)(_ + _.length) / (n.length + h.length)
  }

  protected def wrapSearch(
    f: (String, String) => Double,
    needle: String,
    nodes: List[Node],
    num: Option[Int],
    boundary: Double,
  ): List[(Node, Double)] = {

    val maxNum = math.min(nodes.length, num.getOrElse(nodes.length))


    if (nodes.isEmpty) return List.empty[(Node, Double)]

    val res = nodes.flatMap { node =>

      val nodeStr = node.str.trim
      val trimmedNeedle = needle.trim
      val nodeStrLowered = nodeStr.toLowerCase
      val trimmedLoweredNeedle = trimmedNeedle.toLowerCase

      val nodeRes = if (trimmedNeedle.length == 0 || nodeStr.length == 0) None
                else if (trimmedNeedle == nodeStr) Some(node -> 1.0)
                else if (trimmedLoweredNeedle == nodeStrLowered) Some(node -> 0.99999)
                else if (nodeStrLowered.contains(trimmedLoweredNeedle) || trimmedLoweredNeedle.contains(nodeStrLowered)) Some(node -> 0.99)
                else {
                  val sim_1 = f(trimmedLoweredNeedle, nodeStrLowered)
                  if(sim_1 > boundary)
                    Some(node -> sim_1)
                  else {
                    val sim_2 = (for(n <- trimmedLoweredNeedle.split(" "); h <- nodeStr.split(" ")) yield f(n, h)).max
                    if(sim_2 > boundary) Some(node -> (sim_2 - boundary)) else None
                  }
                }

      nodeRes

    }.sortBy(_._2)

    num match {
      case Some(n) => if (n > 0) res.reverse.take(n) else res.take(math.abs(n))
      case None    => res
    }
  }

  def byString(needle: String, nodes: List[Node], num: Option[Int], boundary: Double = 0.0): List[(Node, Double)] = {
    wrapSearch(ratcliffObershelp, needle, nodes, num, boundary)
  }

}
