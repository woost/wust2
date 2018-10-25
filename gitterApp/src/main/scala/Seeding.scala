package wust.gitter

import wust.graph.{Edge, Node}
import wust.ids._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object GitterImporter {
  import scala.collection.JavaConverters._
  import com.amatkivskiy.gitter.sdk.sync.client.SyncGitterApiClient

  private val gitterAccessToken = sys.env.getOrElse("WUST_GITTER_TOKEN", "")

  def getRoomMessages(url: String, user: Node.User): Future[(Set[Node], Set[Edge])] = {
    val _uri = url.stripLineEnd.stripMargin.trim
      .stripPrefix("https://")
      .stripPrefix("http://")
      .stripPrefix("gitter.im/")
      .takeWhile(_ != '?')
      .stripSuffix("/")
    val tempUserId = user.id
    val client: SyncGitterApiClient =
      new SyncGitterApiClient.Builder().withAccountToken(gitterAccessToken).build()

    // Ensure gitter post
    // TODO: author: tempUserId
    val _gitter = Node.Content(Constants.gitterId, NodeData.PlainText("wust-gitter"), NodeRole.Message)

    // TODO: author: tempUserId
    val discussion = Node.Content(NodeData.PlainText(_uri), NodeRole.Message)
    val discussionTag = Edge.Parent(discussion.id, _gitter.id)
    val postsAndConnection = for {
      roomId <- Future { client.getRoomIdByUri(_uri).id }
      roomMessages <- Future { client.getRoomMessages(roomId).asScala.toList }
    } yield {
      roomMessages.map { message =>
        //TODO what about this userid?
        //TODO: author: tempUserId
        val post: Node = Node.Content(NodeData.Markdown(message.text), NodeRole.Message)
        val conn: Edge = Edge.Parent(post.id, discussion.id)
        (Set(post), Set(conn))
      }.toSet
    }

    postsAndConnection.map(zipped => {
      val (posts, conns) = zipped.unzip
      (posts.flatten + _gitter + discussion, conns.flatten + discussionTag)
    })
  }
}
