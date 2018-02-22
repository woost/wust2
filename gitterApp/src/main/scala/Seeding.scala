package wust.gitter

import wust.graph.{Connection, Post, User}
import wust.ids._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object GitterImporter {
  import scala.collection.JavaConverters._
  import com.amatkivskiy.gitter.sdk.sync.client.SyncGitterApiClient

  private val gitterAccessToken = sys.env.getOrElse("WUST_GITTER_TOKEN", "")

  def getRoomMessages(url: String, user: User): Future[(Set[Post], Set[Connection])] = {
    val _uri = url.stripLineEnd.stripMargin.trim.
      stripPrefix("https://").
      stripPrefix("http://").
      stripPrefix("gitter.im/").
      takeWhile(_ != '?').
      stripSuffix("/")
    val tempUserId = user.id
    val client: SyncGitterApiClient = new SyncGitterApiClient.Builder().withAccountToken(gitterAccessToken).build()

    // Ensure gitter post
    val _gitter = Post(Constants.gitterId, PostContent.Text("wust-gitter"), tempUserId)

    val discussion = Post(PostId.fresh, PostContent.Text(_uri), tempUserId)
    val discussionTag = Connection(discussion.id, ConnectionContent.Parent, _gitter.id)
    val postsAndConnection = for {
      roomId <- Future { client.getRoomIdByUri(_uri).id }
      roomMessages <- Future { client.getRoomMessages(roomId).asScala.toList }
    } yield {
      roomMessages.map { message =>
        //TODO what about this userid?
        val post = Post(PostId.fresh, PostContent.Markdown(message.text), tempUserId)
        val conn = Connection(post.id, ConnectionContent.Parent, discussion.id)
        (Set(post), Set(conn))
      }.toSet
    }

    postsAndConnection.map(zipped => {
      val (posts, conns) = zipped.unzip
      (posts.flatten + _gitter + discussion, conns.flatten + discussionTag)
    })
  }
}
