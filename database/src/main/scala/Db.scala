package wust.db

import java.time.{LocalDateTime, ZoneOffset}

import com.typesafe.config.Config
import io.getquill._
import io.circe.parser._, io.circe.syntax._
import io.treev.tag.TaggedType
import wust.ids._, wust.ids.serialize.Circe._
import wust.util._

import scala.concurrent.{ExecutionContext, Future}

object Db {
  def apply(config: Config) = {
    new Db(new PostgresAsyncContext(LowerCase, config))
  }
}

class Db(val ctx: PostgresAsyncContext[LowerCase]) {
  import Data._
  import ctx._

  protected object Converters {
    private def encodingTaggedType[T: Encoder, Type <: TaggedType[T]#Type]: MappedEncoding[Type, T] = MappedEncoding[Type, T](identity)
    private def decodingTaggedType[T: Decoder, Type <: TaggedType[T]#Type]: MappedEncoding[T, Type] = MappedEncoding[T, Type](_.asInstanceOf[Type])

    // cannot resolve automatically for any T, so need specialized implicit defs
    implicit def encodingStringTaggedType[Type <: TaggedType[String]#Type]: MappedEncoding[Type, String] = encodingTaggedType[String, Type]
    implicit def encodingLongTaggedType[Type <: TaggedType[Long]#Type]: MappedEncoding[Type, Long] = encodingTaggedType[Long, Type]
    implicit def encodingIntTaggedType[Type <: TaggedType[Int]#Type]: MappedEncoding[Type, Int] = encodingTaggedType[Int, Type]
    implicit def decodingStringTaggedType[Type <: TaggedType[String]#Type]: MappedEncoding[String, Type] = decodingTaggedType[String, Type]
    implicit def decodingLongTaggedType[Type <: TaggedType[Long]#Type]: MappedEncoding[Long, Type] = decodingTaggedType[Long, Type]
    implicit def decodingIntTaggedType[Type <: TaggedType[Int]#Type]: MappedEncoding[Int, Type] = decodingTaggedType[Int, Type]

    private def encodeJson[T : io.circe.Encoder](json: T): String = json.asJson.noSpaces
    private def decodeJson[T : io.circe.Decoder](json: String): T = decode[T](json) match {
      case Right(v) => v
      case Left(e) => throw new Exception(s"Failed to decode json: '$json': $e")
    }
    implicit val encodingConnectionContent: MappedEncoding[ConnectionContent, String] = MappedEncoding[ConnectionContent, String](encodeJson[ConnectionContent])
    implicit val decodingConnectionContent: MappedEncoding[String, ConnectionContent] = MappedEncoding[String, ConnectionContent](decodeJson[ConnectionContent])
    implicit val encodingPostContent: MappedEncoding[PostContent, String] = MappedEncoding[PostContent, String](encodeJson[PostContent])
    implicit val decodingPostContent: MappedEncoding[String, PostContent] = MappedEncoding[String, PostContent](decodeJson[PostContent])

    implicit val encodingAccessLevel: MappedEncoding[AccessLevel, String] = MappedEncoding[AccessLevel, String] {
      case AccessLevel.Read => "read"
      case AccessLevel.ReadWrite => "readwrite"
    }
    implicit val decodingAccessLevel: MappedEncoding[String, AccessLevel] = MappedEncoding[String, AccessLevel]{
      case "read" => AccessLevel.Read
      case "readwrite" => AccessLevel.ReadWrite
    }

    implicit class LocalDateTimeQuillOps(ldt: LocalDateTime) {
      def > = ctx.quote((date: LocalDateTime) => infix"$ldt > $date".as[Boolean])
      def >= = ctx.quote((date: LocalDateTime) => infix"$ldt >= $date".as[Boolean])
      def < = ctx.quote((date: LocalDateTime) => infix"$ldt < $date".as[Boolean])
      def <= = ctx.quote((date: LocalDateTime) => infix"$ldt <= $date".as[Boolean])
    }


    implicit class JsonPostContentQuillOps(json: PostContent) {
      def ->> = ctx.quote((field: String) => infix"$json->>$field".as[String])
      def jsonType = ctx.quote(infix"$json->>'type'".as[PostContent.Type])
    }
    implicit class JsonConnectionContentQuillOps(json: ConnectionContent) {
      def ->> = ctx.quote((field: String) => infix"$json->>$field".as[String])
      def jsonType = ctx.quote(infix"$json->>'type'".as[ConnectionContent.Type])
    }

    implicit val userSchemaMeta = schemaMeta[User]("\"user\"") // user is a reserved word, needs to be quoted
    // Set timestamps in backend
  //   implicit val postInsertMeta = insertMeta[RawPost](_.created, _.modified)

    implicit class IngoreDuplicateKey[T](q: Insert[T]) {
      def ignoreDuplicates = quote(infix"$q ON CONFLICT DO NOTHING".as[Insert[T]])
    }
  }
  import Converters._

  //TODO: get rid of raw post? bring isDeleted to frontend as timestamp to maybe hide long-deleted posts by default but show feshly deleted ones.
  case class RawPost(id: PostId, content: PostContent, isDeleted: Boolean, author: UserId, created: LocalDateTime, modified: LocalDateTime, joinDate: LocalDateTime, joinLevel:AccessLevel)
  object RawPost {
    def apply(post: Post, isDeleted: Boolean): RawPost = RawPost(post.id, post.content, isDeleted, post.author, post.created, post.modified, post.joinDate, post.joinLevel)
  }

  //TODO should actually rollback transactions when batch action had partial error
  object post {
    // post ids are unique, so the methods can assume that at max 1 row was touched in each operation

    //TODO need to check rights before we can do this
    private val insert = quote { (post: RawPost) =>
      val q = query[RawPost].insert(post)
      // when adding a new post, we undelete it in case it was already there
      //TODO this approach hides conflicts on post ids!!
      //TODO what about title
      //TODO can undelete posts that i do not own
      infix"$q ON CONFLICT(id) DO UPDATE SET isdeleted = false".as[Insert[RawPost]]
    }

    def createPublic(post: Post)(implicit ec: ExecutionContext): Future[Boolean] = createPublic(Set(post))
    def createPublic(posts: Set[Post])(implicit ec: ExecutionContext): Future[Boolean] = {
      val rawPosts = posts.map(RawPost(_, false))
      ctx.run(liftQuery(rawPosts.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1))
    }

    def get(postId: PostId)(implicit ec: ExecutionContext): Future[Option[Post]] = {
      ctx.run(query[Post].filter(_.id == lift(postId)).take(1))
        .map(_.headOption)
    }

    def get(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[List[Post]] = {
      //TODO
      //ctx.run(query[Post].filter(p => liftQuery(postIds) contains p.id))
      val q = quote {
        infix"""
        select post.* from unnest(${lift(postIds.toList)} :: varchar(36)[]) inputPostId join post on post.id = inputPostId
      """.as[Query[Post]]
      }

      ctx.run(q)
    }

    def update(post: Post)(implicit ec: ExecutionContext): Future[Boolean] = update(Set(post))
    def update(posts: Set[Post])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(posts.toList).foreach(post => query[RawPost].filter(_.id == post.id).update(_.content -> post.content)))
        .map(_.forall(_ == 1))
    }

    def delete(postId: PostId)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(postId))
    def delete(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(postIds.toList).foreach(postId => query[RawPost].filter(_.id == postId).update(_.isDeleted -> lift(true))))
        .map(_.forall(_ == 1))
    }

    def undelete(postId: PostId)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(postId))
    def undelete(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(postIds.toList).foreach(postId => query[RawPost].filter(_.id == postId).update(_.isDeleted -> lift(false))))
        .map(_.forall(_ == 1))
    }

    def getMembers(postId: PostId)(implicit ec: ExecutionContext): Future[List[User]] = {
      ctx.run {
        for {
          membership <- query[Membership].filter(_.postId == lift(postId))
          user <- query[User].filter(_.id == membership.userId)
        } yield user
      }
    }

    def addMemberWithCurrentJoinLevel(postId: PostId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = addMemberWithCurrentJoinLevel(postId :: Nil, userId).map(_.nonEmpty)
    def addMemberWithCurrentJoinLevel(postIds: List[PostId], userId: UserId)(implicit ec: ExecutionContext): Future[Seq[PostId]] = {
      val now = LocalDateTime.now(ZoneOffset.UTC)
      val insertMembership = quote {
        val q = query[Post]
          .filter(p => liftQuery(postIds).contains(p.id) && lift(now) < p.joinDate)
          .map(p => Membership(lift(userId), p.id, p.joinLevel))
        infix"""
          insert into membership(userid, postid, level)
          $q ON CONFLICT(postid, userid) DO UPDATE set level =
            CASE EXCLUDED.level WHEN 'read' THEN membership.level
                                WHEN 'readwrite' THEN 'readwrite'
            END
        """.as[Insert[Membership]]
        //TODO: https://github.com/getquill/quill/issues/1093
            // returning postid
        // """.as[ActionReturning[Membership, PostId]]
      }

      // val r = ctx.run(liftQuery(postIds).foreach(insertMembership(_)))
      ctx.run(insertMembership)
        //TODO: fix query with returning
        .map { _ => postIds }
    }

    def addMemberEvenIfLocked(postId: PostId, userId: UserId, accessLevel: AccessLevel)(implicit ec: ExecutionContext): Future[Boolean] = addMemberEvenIfLocked(postId :: Nil, userId, accessLevel).map(_.nonEmpty)
    def addMemberEvenIfLocked(postIds: List[PostId], userId: UserId, accessLevel: AccessLevel)(implicit ec: ExecutionContext): Future[Seq[PostId]] = {
      val insertMembership = quote { (postId: PostId) =>
        val q = query[Membership].insert(Membership(lift(userId), postId, lift(accessLevel)))
        infix"""
          $q ON CONFLICT(postid, userid) DO UPDATE set level =
            CASE EXCLUDED.level WHEN 'read' THEN membership.level
                                WHEN 'readwrite' THEN 'readwrite'
            END
        """.as[Insert[Membership]].returning(_.postId)
      }
      ctx.run(liftQuery(postIds).foreach(insertMembership(_)))
    }
    def setJoinDate(postId: PostId, joinDate: JoinDate)(implicit ec: ExecutionContext):Future[Boolean] = {
      val joinDateLocalDateTime = Data.epochMilliToLocalDateTime(joinDate.timestamp)
      ctx.run(query[RawPost].filter(_.id == lift(postId)).update(_.joinDate -> lift(joinDateLocalDateTime))).map(_ == 1)
    }
  }

  object notifications {
    private case class NotifiedUsersData(userId: UserId, postIds: List[PostId])
    private def notifiedUsersFunction(postIds: List[PostId]) = quote {
      infix"select * from notified_users(${lift(postIds)})".as[Query[NotifiedUsersData]]
    }

    def notifiedUsers(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[Map[UserId, List[PostId]]] = {
      ctx.run {
        notifiedUsersFunction(postIds.toList).map(d => d.userId -> d.postIds)
      }.map(_.toMap)
    }

    def subscribeWebPush(subscription: WebPushSubscription)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(query[WebPushSubscription].insert(lift(subscription)).returning(_.id))
        .map(_ => true)
        .recoverValue(false)
    }

    def delete(subscriptions: Set[WebPushSubscription])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(subscriptions.toList).foreach(s => query[WebPushSubscription].filter(_.id == s.id).delete))
        .map(_.forall(_ == 1))
    }

    def getSubscriptions(userIds: Set[UserId])(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
      ctx.run{
        for {
          s <- query[WebPushSubscription].filter(sub => liftQuery(userIds.toList) contains sub.userId)
        } yield s
      }
    }
    //
    // def getSubscriptions(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
    //   ctx.run{
    //     for {
    //       // m <- query[Membership].filter(m => liftQuery(postIds.toList).contains(m.postId))
    //       s <- query[WebPushSubscription]//.filter(_.userId == m.userId)
    //     } yield s
    //   }
    // }
  }

  object connection {
    private val insert = quote { (c: Connection) =>
      val q = query[Connection].insert(c)
      // if there is unique conflict, we update the content which might contain new values
      infix"$q ON CONFLICT(sourceid,(content->>'type'),targetid) DO UPDATE SET content = EXCLUDED.content".as[Insert[Connection]]
    }

    def apply(connection: Connection)(implicit ec: ExecutionContext): Future[Boolean] = apply(Set(connection))
    def apply(connections: Set[Connection])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(connections.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1))
        .recoverValue(false)
    }

    def delete(connection: Connection)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(connection))
    def delete(connections: Set[Connection])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(connections.toList).foreach(connection => query[Connection].filter(c => c.sourceId == connection.sourceId && c.content.jsonType == connection.content.tpe && c.targetId == connection.targetId).delete))
        .map(_.forall(_ <= 1))
    }
  }

  object user {

    def allMembershipsQuery(userId: UserId) = quote {
      query[Membership].filter(m => m.userId == lift(userId))
    }

    def allTopLevelPostsQuery = quote {
      query[Post]
        .leftJoin(query[Connection])
        .on((p, c) => c.content.jsonType == ConnectionContent.Parent.tpe && c.sourceId == p.id)
        .filter{ case (_, c) => c.isEmpty }
        .map{ case (p, _) => p }
    }

    def allPostsQuery(userId: UserId) = quote {
      for {
        m <- allMembershipsQuery(userId)
        p <- query[Post].join(p => p.id == m.postId)
      } yield p
    }

    def visiblePosts(userId: UserId, postIds: Iterable[PostId])(implicit ec: ExecutionContext): Future[List[PostId]] = {
      ctx.run{
        for {
          m <- query[Membership].filter(m => m.userId == lift(userId) && liftQuery(postIds.toList).contains(m.postId))
          child <- query[Connection].filter(c => c.targetId == m.postId && c.content.jsonType == lift(ConnectionContent.Parent.tpe)).map(_.sourceId).distinct // get all direct children
        } yield child
      }
    }

    def getAllPosts(userId: UserId)(implicit ec: ExecutionContext): Future[List[Post]] = ctx.run { allPostsQuery(userId) }


    def apply(id: UserId, name: String, digest: Array[Byte], channelPostId: PostId)(implicit ec: ExecutionContext): Future[Option[User]] = {
      val user = User(id, name, isImplicit = false, 0, channelPostId)
      val channelsPostContent: PostContent = PostContent.Channels
      //TODO: author for channelsPost should not be '1'. Author should not even be part of user.
      val q = quote {
        infix"""
        with insp as (insert into post (id,content,author,joinlevel) values (${lift(channelPostId)}, ${lift(channelsPostContent)}, 1, 'read')),
             insu as (insert into "user" values(${lift(user.id)}, ${lift(user.name)}, ${lift(user.revision)}, ${lift(user.isImplicit)}, ${lift(user.channelPostId)})),
             insm as (insert into membership (userid, postid, level) values(${lift(user.id)}, ${lift(user.channelPostId)}, 'read'))
                      insert into password(id, digest) select id, ${lift(digest)}
      """.as[Insert[User]]
        //TODO: update post and set author to userid
      }

      ctx.run(q)
        .collect { case 1 => Option(user) }
        .recoverValue(None)
    }

    def createImplicitUser(id: UserId, name: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      val channelPostId = PostId.fresh
      val channelsPostContent: PostContent = PostContent.Channels
      val user = User(id, name, isImplicit = true, 0, channelPostId)
      val q = quote {
        infix"""
        with insp as (insert into post (id,content,author,joinlevel) values (${lift(channelPostId)}, ${lift(channelsPostContent)}, 1, 'read')),
             insu as (insert into "user" values(${lift(user.id)}, ${lift(user.name)}, ${lift(user.revision)}, ${lift(user.isImplicit)}, ${lift(user.channelPostId)}))
                     insert into membership (userid, postid, level) values(${lift(user.id)}, ${lift(user.channelPostId)}, 'read')
      """.as[Insert[User]]
        //TODO: update post and set author to userid
      }
      ctx.run(q)
        .collect { case 1 => Option(user) }
        .recoverValue(None)
    }

    //TODO one query
    def activateImplicitUser(id: UserId, name: String, passwordDigest: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.transaction { implicit ec =>
        ctx.run(query[User].filter(u => u.id == lift(id) && u.isImplicit))
          .flatMap(_.headOption.map { user =>
            val updatedUser = user.copy(
              name = name,
              isImplicit = false,
              revision = user.revision + 1
            )
            for {
              _ <- ctx.run(query[User].filter(_.id == lift(id)).update(lift(updatedUser)))
              _ <- ctx.run(query[Password].insert(lift(Password(id, passwordDigest))))
            } yield Option(updatedUser)
          }.getOrElse(Future.successful(None)))
      }.recoverValue(None)
    }
    //TODO: one query.
    // def activateImplicitUser(id: UserId, name: String, digest: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
    //    val user = newRealUser(name)
    //    val q = quote { s"""
    //      with existingUser as (
    //        UPDATE "user" SET isimplicit = true, revision = revision + 1 WHERE id = ${lift(id)} and isimplicit = true RETURNING revision
    //      )
    //      INSERT INTO password(id, digest) values(${lift(id)}, ${lift(digest)}) RETURNING existingUser.revision;
    //    """}

    //    ctx.executeActionReturning(q, identity, _(0).asInstanceOf[Int], "revision")
    //      .map(rev => Option(user.copy(revision = rev)))
    //      // .recoverValue(None)
    // }

    def mergeImplicitUser(implicitId: UserId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      if (implicitId == userId) Future.successful(false)
      else ctx.transaction { implicit ec =>
        get(implicitId).flatMap { user =>
          val isAllowed = user.fold(false)(_.isImplicit)
          if (isAllowed) {
            val q = quote {
              infix"""select mergeFirstUserIntoSecond(${lift(implicitId)}, ${lift(userId)})""".as[Delete[User]]
            }
            ctx.run(q).map(_ == 1)
          } else Future.successful(false)
        }
      }
    }

    def get(id: UserId)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.run(query[User].filter(_.id == lift(id)).take(1))
        .map(_.headOption)
    }

    def getUserAndDigest(name: String)(implicit ec: ExecutionContext): Future[Option[(User, Array[Byte])]] = {
      ctx.run {
        query[User]
          .filter(_.name == lift(name))
          .join(query[Password])
          .on((u, p) => u.id == p.id)
          .map { case (u, p) => (u, p.digest) }
          .take(1)
      }.map(_.headOption)
    }

    def byName(name: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.run {
        query[User]
          .filter(u => u.name == lift(name) && u.isImplicit == false)
          .take(1)
      }.map(_.headOption)
    }

    def checkIfEqualUserExists(user: User)(implicit ec: ExecutionContext): Future[Boolean] = {
      import user._
      ctx.run {
        query[User]
          .filter(u => u.id == lift(id) && u.revision == lift(revision) && u.isImplicit == lift(isImplicit) && u.name == lift(name))
          .take(1)
      }.map(_.nonEmpty)
    }

    def isMember(postId: PostId, userId: UserId, minAccessLevel: AccessLevel)(implicit ec: ExecutionContext): Future[Boolean] = {
      val allowedLevels = minAccessLevel match {
        case AccessLevel.Read => AccessLevel.Read :: AccessLevel.ReadWrite :: Nil
        case AccessLevel.ReadWrite => AccessLevel.ReadWrite :: Nil
      }
      def find(allowedLevels: List[AccessLevel]) = quote {
        query[Membership].filter(m => m.postId == lift(postId) && m.userId == lift(userId) && lift(allowedLevels).contains(m.level)).nonEmpty
      }
      ctx.run(find(allowedLevels))
    }
  }

  object graph {
    private def graphPage(parents: Seq[PostId], children: Seq[PostId], requestingUserId: UserId) = quote {
      infix"select * from graph_page(${lift(parents)}, ${lift(children)}, ${lift(requestingUserId)})".as[Query[GraphRow]]
    }
    private def graphPageWithOrphans(parents: Seq[PostId], children: Seq[PostId], requestingUserId: UserId) = quote {
      infix"select * from graph_page_with_orphans(${lift(parents)}, ${lift(children)}, ${lift(requestingUserId)})".as[Query[GraphRow]]
    }

    def getPage(parentIds: Seq[PostId], childIds: Seq[PostId], requestingUserId:UserId)(implicit ec: ExecutionContext): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx.run {
        graphPage(parentIds, childIds, requestingUserId)
      }.map(Graph.from)
    }

    def getPageWithOrphans(parentIds: Seq[PostId], childIds: Seq[PostId], requestingUserId:UserId)(implicit ec: ExecutionContext): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx.run {
        graphPageWithOrphans(parentIds, childIds, requestingUserId)
      }.map(Graph.from)
    }
  }
}
