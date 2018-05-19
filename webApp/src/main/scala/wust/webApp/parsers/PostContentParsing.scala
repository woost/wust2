package wust.webApp.parsers

import wust.graph._
import wust.ids.{Label, PostId, UserId}

object PostContentParser {
  import fastparse.all._
  import ParserElements._

  val linkContent: P[PostContent.Link] = P ( url.map(PostContent.Link(_)) )

  val nonHashContent: P[PostContent.Markdown] = P( CharPred(_ != '#').rep.!.map(PostContent.Markdown(_)) )

  val taggableContent: P[PostContent] = P( linkContent | nonHashContent)

  val contentTags: P[Seq[String]] = P( ("#" ~ (word.! | "\"" ~ word.rep(sep = whitespaceChar).! ~ "\"")).rep(sep = whitespaceChar) )

  val anyContent: P[PostContent.Markdown] = P( AnyChar.rep.!.map(PostContent.Markdown(_)) )

  val contentWithTags: P[(PostContent, Seq[String])] = P( (taggableContent ~ whitespaceChar.rep ~ contentTags) )

  //TODO: better?
  val taggedContent: P[(PostContent, Seq[String])] = P( (contentWithTags | anyContent.map((_, Seq.empty))) ~ End )

  def editPost(contextPosts: Seq[Post], author: UserId)(post: Post): P[GraphChanges] = taggedContent
      .map { case (content, tags) =>
        //TODO: wrong author - should just be added in enrichment
        val tagPostsEither = tags.map(tag => contextPosts.find(_.content.str == tag).toRight(Post(PostContent.Text(tag), author)))
        val newTagPosts = tagPostsEither.collect { case Left(p) => p }
        val tagPosts = tagPostsEither.map(_.fold(_.id, _.id))
        val updatedPost = post.copy(content = content)
        GraphChanges.from(addConnections = tagPosts.map(Connection(updatedPost.id, Label.parent, _)), updatePosts = Set(updatedPost), addPosts = newTagPosts)
      }

  def newPost(contextPosts: Seq[Post], author: UserId): P[GraphChanges] = taggedContent
    .map { case (content, tags) =>
      val tagPostsEither = tags.map(tag => contextPosts.find(_.content.str == tag).toRight(Post(PostContent.Text(tag), author)))
      val newTagPosts = tagPostsEither.collect { case Left(p) => p }
      val tagPosts = tagPostsEither.map(_.fold(_.id, _.id))
      val newPost = Post(content, author)
      GraphChanges.from(addConnections = tagPosts.map(Connection(newPost.id, Label.parent, _)), addPosts = newPost +: newTagPosts)
    }

  //TODO integrate help text
  def formattingHelp =
    """
      |The format support multiple types: markdown, media and tags.
    """.stripMargin
}

object PostContentWriter {
}
