package wust.webApp.state

import wust.facades.emojijs.EmojiConvertor
import wust.graph.{GraphChanges, Node}
import wust.ids.NodeData

object EmojiTitleConverter {
  val emojiTitleConvertor = new EmojiConvertor()
  emojiTitleConvertor.replace_mode = "unified"
  emojiTitleConvertor.allow_native = true
}

object EmojiReplacer {
  // https://stackoverflow.com/questions/49745304/regex-to-find-and-replace-emoji-names-within-colons
  val emojiAtBeginningRegex = raw"^(:[^:\s]*(?:::[^:\s]*)*:\s*)".r.unanchored // unanchored finds pattern in entire input

  val emojiTextConvertor = new EmojiConvertor()
  emojiTextConvertor.colons_mode = true
  emojiTextConvertor.text_mode = true
  private def replaceToColons(nodes: Array[Node]): Array[Node] = nodes.map {
    case n@Node.Content(_, editable: NodeData.EditableText, _, _, _, _) =>
      scribe.debug(s"replacing node emoji: ${ n.str }.")
      editable.updateStr(emojiTextConvertor.replace_unified_safe(emojiTextConvertor.replace_emoticons_safe(n.str))) match {
        case Some(emojiData) =>
          scribe.debug(s"New representation: ${ emojiData.str }.")
          n.copy(data = emojiData)
        case None => n
      }
    case n => n
  }
  def replaceChangesToColons(graphChanges: GraphChanges) = graphChanges.copy(addNodes = replaceToColons(graphChanges.addNodes))
}
