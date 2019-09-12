package wust.webApp.search

import wust.graph.Node


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

  protected def singleSearch(f: (String, String) => Double, needle: String, node: Node, boundary: Double) = {
    val nodeLowered = node.str.toLowerCase
    val needleLowered = needle.toLowerCase
    val nodeTrimmed = nodeLowered.trim
    val needleTrimmed = needleLowered.trim

    def weightedContains(n: String, h: String, maxMatch: Double = 0.999) = boundary + (maxMatch - boundary) * n.r.findAllMatchIn(h).length * n.length / h.length

    val nodeRes = if (needleLowered.length == 0 || nodeLowered.length == 0) None
                  else if (needle == node.str)            Some(node -> 1.0)       // case-sensitive matches
                  else if (needleLowered == nodeLowered)  Some(node -> 0.99999)   // case-insensitive matches
                  else if ((nodeLowered.length < 2 && needleLowered.length > 2) || (nodeLowered.length < 3 && needleLowered.length > 3)) None             // Ignore
                  else if (needleLowered.length < nodeLowered.length && nodeTrimmed.contains(needleTrimmed)) {
                    if(nodeLowered.contains(needleLowered)) Some(node -> weightedContains(needleLowered, nodeLowered))
                    else Some(node -> weightedContains(needleTrimmed, nodeTrimmed, 0.91))
                  } else if (nodeLowered.length < needleLowered.length && needleTrimmed.contains(nodeTrimmed)) {
                    if(needleLowered.contains(nodeLowered)) Some(node -> weightedContains(nodeLowered, needleLowered))
                    else Some(node -> weightedContains(nodeTrimmed, needleTrimmed, 0.91))
                  }
                  else {
                    val sim_1 = f(needleTrimmed, nodeTrimmed)
                    if(sim_1 > boundary)
                      Some(node -> sim_1)
                    else {
                      val sim_2 = (for(n <- needleTrimmed.split(" "); h <- nodeTrimmed.split(" ")) yield f(n, h)).max
                      if(sim_2 > boundary) Some(node -> (sim_2 - boundary)) else None
                    }
                  }

    nodeRes
  }

  protected def wrapSearch(
    f: (String, String) => Double,
    needle: String,
    nodes: List[Node],
    num: Option[Int],
    boundary: Double,
  ): List[(Node, Double)] = {

    if (nodes.isEmpty) return List.empty[(Node, Double)]

    val maxNum = math.min(nodes.length, math.abs(num.getOrElse(nodes.length)))

    val res = nodes.flatMap( node => singleSearch(f, needle, node, boundary) ).sortBy(-_._2)

    num match {
      case Some(n) => if(n > 0) res.takeRight(maxNum) else res.take(maxNum)
      case _       => res
    }
  }

  @inline def byString(needle: String, nodes: List[Node], num: Option[Int], boundary: Double = 0.0): List[(Node, Double)] = {
    wrapSearch(ratcliffObershelp, needle, nodes, num, boundary)
  }

  @inline def singleByString(needle: String, node: Node, boundary: Double = 0.0): Option[(Node, Double)] = {
    singleSearch(ratcliffObershelp, needle, node, boundary)
  }

}
