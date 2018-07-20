package wust.webApp.parsers

import wust.graph._
import wust.ids._

object NodeDataParser {
  import fastparse.all._
  import ParserElements._

  val linkContent: P[NodeData.Link] = P(url.map(NodeData.Link(_)))

  val nonHashContent: P[NodeData.Markdown] = P(CharPred(_ != '#').rep.!.map(NodeData.Markdown(_)))

  val taggableContent: P[NodeData.Content] = P(linkContent | nonHashContent)

  val contentTags: P[Seq[String]] = P(
    ("#" ~ (CharPred(c => !c.isWhitespace && c != '"').rep.!)).rep(sep = whitespaceChar)
  )

  val anyContent: P[NodeData.Markdown] = P(AnyChar.rep.!.map(NodeData.Markdown(_)))

  val contentWithTags: P[(NodeData.Content, Seq[String])] = P(
    taggableContent ~ whitespaceChar.rep ~ contentTags
  )

  //TODO: better?
  val taggedContent: P[(NodeData.Content, Seq[String])] = P(
    (contentWithTags | anyContent.map((_, Seq.empty))) ~ End
  )

  def addNode(str:String, contextNodes: Iterable[Node], baseNode:Node.Content = Node.Content.empty): GraphChanges = {
    val parser = taggedContent.map {
        case (data, tags) =>
          val tagNodesEither = tags.map(
            tag =>
              contextNodes.find(_.data.str == tag).toRight(Node.Content(NodeData.PlainText(tag)))
          )
          val newTagNodes = tagNodesEither.collect { case Left(p) => p }
          val tagNodes = tagNodesEither.map(_.fold(_.id, _.id))
          val newNode = baseNode.copy(data = data)
          GraphChanges.from(
            addEdges = tagNodes.map(Edge.Parent(newNode.id, _)),
            addNodes = newNode +: newTagNodes
          )
      }
    parser.parse(str) match {
      case Parsed.Success(changes, _) => changes
      case failure: Parsed.Failure =>
        scribe.warn(
          s"Error parsing chat message '$str': ${failure.msg}. Will assume Markdown."
        )
        GraphChanges.addNode(NodeData.Markdown(str))
    }
  }

  //TODO integrate help text
  def formattingHelp =
    """
      |The format support multiple types: markdown, media and tags.
    """.stripMargin
}

object PostDataWriter {}
