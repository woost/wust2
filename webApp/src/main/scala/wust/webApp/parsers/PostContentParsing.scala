package wust.webApp.parsers

import wust.graph._
import wust.ids._

object NodeDataParser {
  import fastparse.all._
  import ParserElements._

  val linkContent: P[NodeData.Link] = P ( url.map(NodeData.Link(_)) )

  val nonHashContent: P[NodeData.Markdown] = P( CharPred(_ != '#').rep.!.map(NodeData.Markdown(_)) )

  val taggableContent: P[NodeData.Content] = P( linkContent | nonHashContent)

  val contentTags: P[Seq[String]] = P( ("#" ~ (word.! | "\"" ~ word.rep(sep = whitespaceChar).! ~ "\"")).rep(sep = whitespaceChar) )

  val anyContent: P[NodeData.Markdown] = P( AnyChar.rep.!.map(NodeData.Markdown(_)) )

  val contentWithTags: P[(NodeData.Content, Seq[String])] = P( (taggableContent ~ whitespaceChar.rep ~ contentTags) )

  //TODO: better?
  val taggedContent: P[(NodeData.Content, Seq[String])] = P( (contentWithTags | anyContent.map((_, Seq.empty))) ~ End )

  def editPost(contextPosts: Seq[Node], author: UserId)(post: Node.Content): P[GraphChanges] = taggedContent
      .map { case (data, tags) =>
        val tagPostsEither = tags.map(tag => contextPosts.find(_.data.str == tag).toRight(Node.Content(NodeData.PlainText(tag))))
        val newTagPosts = tagPostsEither.collect { case Left(p) => p }
        val tagPosts = tagPostsEither.map(_.fold(_.id, _.id))
        val updatedPost = post.copy(data = data)
        GraphChanges.from(addConnections = tagPosts.map(Edge.Parent(updatedPost.id, _)), updatePosts = Set(updatedPost), addPosts = newTagPosts)
      }

  def newNode(contextNodes: Seq[Node], author: UserId): P[GraphChanges] = taggedContent
    .map { case (data, tags) =>
      val tagPostsEither = tags.map(tag => contextNodes.find(_.data.str == tag).toRight(Node.Content(NodeData.PlainText(tag))))
      val newTagPosts = tagPostsEither.collect { case Left(p) => p }
      val tagPosts = tagPostsEither.map(_.fold(_.id, _.id))
      val newPost = Node.Content(data)
      GraphChanges.from(addConnections = tagPosts.map(Edge.Parent(newPost.id, _)), addPosts = newPost +: newTagPosts)
    }

  //TODO integrate help text
  def formattingHelp =
    """
      |The format support multiple types: markdown, media and tags.
    """.stripMargin
}

object PostDataWriter {
}
