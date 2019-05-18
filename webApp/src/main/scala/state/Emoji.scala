package wust.webApp.state

import emojijs.EmojiConvertor
import wust.graph.{GraphChanges, Node}
import wust.ids.NodeData

import scala.collection.breakOut

object EmojiTitleConverter {
  val emojiTitleConvertor = new EmojiConvertor()
  emojiTitleConvertor.replace_mode = "unified"
  emojiTitleConvertor.allow_native = true
}

object EmojiReplacer {
  val emojiTextConvertor = new EmojiConvertor()
  emojiTextConvertor.colons_mode = true
  emojiTextConvertor.text_mode = true
  private def replaceToColons(nodes: Array[Node]): Array[Node] = nodes.map {
    case n@Node.Content(_, editable: NodeData.EditableText, _, _, _) =>
      scribe.debug(s"replacing node emoji: ${ n.str }.")
      editable.updateStr(emojiTextConvertor.replace_unified(emojiTextConvertor.replace_emoticons(n.str))) match {
        case Some(emojiData) =>
          scribe.debug(s"New representation: ${ emojiData.str }.")
          n.copy(data = emojiData)
        case None => n
      }
    case n => n
  }
  def replaceChangesToColons(graphChanges: GraphChanges) = graphChanges.copy(addNodes = replaceToColons(graphChanges.addNodes))
}
