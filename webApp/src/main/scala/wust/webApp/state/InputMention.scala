package wust.webApp.state

import wust.graph.Node
import wust.util.StringOps

object InputMention {
  val mentionsRegex = raw"@(?:[^ \\]|\\\\)*(?:\\ [^ ]*)*".r
  def nodeToMentionsString(node: Node): String = {
    val lines = node.str.linesIterator
    val linesHead = if (lines.hasNext) StringOps.trimToMaxLength(lines.next.replace("\\", "\\\\").replace(" ", "\\ "), 100) else ""
    linesHead
  }
}
