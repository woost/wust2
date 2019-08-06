package wust.webApp.state

import wust.graph.Node
import wust.util.StringOps

object InputMention {
  val mentionsRegex = raw"@(?:[^ \\]|\\\\)*(?:\\ [^ ]*)*".r
  def nodeToMentionsString(node: Node): String = {
    val firstLine = {
      val lines = node.str.linesIterator
      if (lines.hasNext) lines.next else ""
    }

    val str = if (firstLine.isEmpty) node.id.toBase58 else firstLine
    stringToMentionsString(str)
  }
  def stringToMentionsString(str: String): String = {
    StringOps.trimToMaxLength(str.replace("\\", "\\\\").replace(" ", "\\ "), 100)
  }
}
