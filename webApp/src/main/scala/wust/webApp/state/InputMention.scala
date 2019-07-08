package wust.webApp.state

import wust.graph.Node
import wust.util.StringOps

object InputMention {
  val mentionsRegex = raw"@(?:[^ \\]|\\\\)*(?:\\ [^ ]*)*".r
  def nodeToMentionsString(node: Node): String = {
    val lines = node.str.linesIterator
    if (lines.hasNext) stringToMentionsString(lines.next) else ""
  }
  def stringToMentionsString(str: String): String = {
    StringOps.trimToMaxLength(str.replace("\\", "\\\\").replace(" ", "\\ "), 100)
  }
}
