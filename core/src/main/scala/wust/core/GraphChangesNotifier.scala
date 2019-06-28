package wust.core

import wust.graph._

object GraphChangesNotifier {
  def notify(changes: GraphChanges): Unit = {
    val mentions = changes.addEdges.collect { case e: Edge.Mention => e }
    notifyMentions(mentions)
  }

  private def notifyMentions(mentions: Seq[Edge.Mention]): Unit = if (mentions.nonEmpty) {
    scribe.info(s"Sending out mentions: $mentions")
  }
}
