package wust.db

import java.time.LocalDateTime

import com.typesafe.config.Config
import io.getquill._
import wust.ids._
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

  implicit val encodeUserId = MappedEncoding[UserId, UuidType](identity)
  implicit val decodeUserId = MappedEncoding[UuidType, UserId](UserId(_))
  implicit val encodePostId = MappedEncoding[PostId, UuidType](identity)
  implicit val decodePostId = MappedEncoding[UuidType, PostId](PostId(_))
  implicit val encodeLabel = MappedEncoding[Label, String](identity)
  implicit val decodeLabel = MappedEncoding[String, Label](Label(_))

  implicit val userSchemaMeta = schemaMeta[User]("\"user\"") // user is a reserved word, needs to be quoted
  // Set timestamps in backend
  // implicit val postInsertMeta = insertMeta[RawPost](_.created, _.modified)

  case class RawPost(id: PostId, content: String, isDeleted: Boolean, author: UserId, created: LocalDateTime, modified: LocalDateTime)
  object RawPost {
    def apply(post: Post, isDeleted: Boolean): RawPost = RawPost(post.id, post.content, isDeleted, post.author, post.created, post.modified)
  }

  implicit class IngoreDuplicateKey[T](q: Insert[T]) {
    def ignoreDuplicates = quote(infix"$q ON CONFLICT DO NOTHING".as[Insert[T]])
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

    def addMember(postId: PostId, userId: UserId)(implicit ec: ExecutionContext): Future[Option[(User, Membership)]] = {
      ctx.transaction { implicit ec =>
        for {
          _ <- ctx.run(infix"""insert into membership(postId, userId) values (${lift(postId)}, ${lift(userId)}) on conflict do nothing""".as[Insert[Membership]])
          user <- ctx.run(query[User].filter(_.id == lift(userId)))
        } yield Option((user.head, Membership(userId, postId)))
      }.recoverValue(None)
    }
  }

  object notifications {
    def subscribeWebPush(subscription: WebPushSubscription)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(query[WebPushSubscription].insert(lift(subscription))).map(_ == 1)
    }

    def delete(subscriptions: Set[WebPushSubscription])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(subscriptions.toList).foreach(s => query[WebPushSubscription].filter(_.id == s.id).delete))
        .map(_.forall(_ == 1))
    }

    def getSubscriptions(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
      ctx.run{
        for {
          // m <- query[Membership].filter(m => liftQuery(postIds.toList).contains(m.postId))
          s <- query[WebPushSubscription]//.filter(_.userId == m.userId)
        } yield s
      }
    }
  }

  object connection {
    private val insert = quote { (connection: Connection) => query[Connection].insert(connection).ignoreDuplicates }

    def apply(connection: Connection)(implicit ec: ExecutionContext): Future[Boolean] = apply(Set(connection))
    def apply(connections: Set[Connection])(implicit ec: ExecutionContext): Future[Boolean] = {
      // This is a quill batch action:
      //TODO: insert label
      ctx.run(liftQuery(connections.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1))
        .recoverValue(false)
    }

    def delete(connection: Connection)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(connection))
    def delete(connections: Set[Connection])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(connections.toList).foreach(connection => query[Connection].filter(c => c.sourceId == connection.sourceId && c.label == connection.label && c.targetId == connection.targetId).delete))
        .map(_.forall(_ <= 1))
    }
  }

  object user {

    def allMembershipsQuery(userId:UserId) = quote {
      query[Membership].filter(m => m.userId == lift(userId))
    }

    def AllPostsQuery(userId:UserId) = quote {
      for {
        m <- allMembershipsQuery(userId)
        p <- query[Post].join(p => p.id == m.postId)
      } yield p
    }

    def visiblePosts(userId:UserId, postIds: Iterable[PostId])(implicit ec: ExecutionContext):Future[List[PostId]] = {
      ctx.run{
        for {
          m <- query[Membership].filter(m => m.userId == lift(userId) && liftQuery(postIds.toList).contains(m.postId))
          child <- query[Connection].filter(c => c.targetId == m.postId && c.label == lift(Label.parent)).map(_.sourceId).distinct // get all direct children
        } yield child
      }
    }

    def getAllPosts(userId:UserId)(implicit ec: ExecutionContext):Future[List[Post]] = ctx.run { AllPostsQuery(userId) }


    def apply(id: UserId, name: String, digest: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
      val user = User(id, name, isImplicit = false, 0)
      val q = quote { infix"""
        with ins as (
          insert into "user" values(${lift(user.id)}, ${lift(user.name)}, ${lift(user.revision)}, ${lift(user.isImplicit)}) returning id
        ) insert into password(id, digest) select id, ${lift(digest)} from ins
      """.as[Insert[User]] }

      ctx.run(q)
        .collect { case 1 => Option(user) }
        .recoverValue(None)
    }

    def createImplicitUser(id: UserId, name: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      val user = User(id, name, isImplicit = true, 0)
      val q = quote { query[User].insert(lift(user)) }
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

    //TODO: http://stackoverflow.com/questions/5347050/sql-to-list-all-the-tables-that-reference-a-particular-column-in-a-table (at compile-time?)
    //TODO: this needs to be tested and udpated
    //TODO: missing push notifications
    def mergeImplicitUser(implicitId: UserId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      if (implicitId == userId) Future.successful(false)
      else {
        val q = quote { infix"""
          with postOwner as (
            UPDATE rawpost SET author = ${lift(userId)} WHERE author = ${lift(implicitId)} RETURNING author
          ), existingUser as (
            DELETE FROM "user" WHERE id = ${lift(implicitId)} AND isimplicit = true AND EXISTS (SELECT id FROM "user" WHERE id = ${lift(userId)} AND isimplicit = false) RETURNING id
          ), update as (
            DELETE FROM membership using existingUser WHERE userid = existingUser.id RETURNING groupId
          )
          INSERT INTO membership select groupid, ${lift(userId)} from update ON CONFLICT DO NOTHING;
        """.as[Delete[User]] }

        //TODO: cannot detect failures?
        ctx.run(q).map(_ => true)
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

    def isMember(postId: PostId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(query[Membership].filter(m => m.postId == lift(postId) && m.userId == lift(userId)).nonEmpty)
    }

    def hasAccessToPost(userId: UserId, postId: PostId)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run {
        val postIsPublic = query[Membership].filter(m => m.postId == lift(postId)).isEmpty
        val userIsMemberOfPost = query[Membership].filter(m => m.postId == lift(postId) && m.userId == lift(userId)).nonEmpty
        postIsPublic || userIsMemberOfPost
      }
    }
  }

  object graph {
    def getAllVisiblePosts(userIdOpt: Option[UserId])(implicit ec: ExecutionContext): Future[Graph] = {

      val publicPosts = quote {
        query[Post]
          .leftJoin(query[Membership])
          .on((p, m) => p.id == m.postId)
          .filter { case (_, m) => m.isEmpty }
          .map { case (p, _) => p }
      }

      userIdOpt match {
        case Some(userId) =>
          val myPostsMemberships = quote {
            for {
              p <- user.AllPostsQuery(userId)
              m <- query[Membership].filter(_.postId == p.id)
            } yield m
          }

          val myPostsMembers = quote {
            for {
              m <- myPostsMemberships
              u <- query[User].join(_.id == m.userId)
            } yield u
          }

          val postsFut = ctx.run { user.AllPostsQuery(userId) ++ publicPosts }
          val connectionsFut = ctx.run(query[Connection])
          val userFut = ctx.run(query[User].filter(_.id == lift(userId)))
          val myGroupsMembersFut = ctx.run(myPostsMembers)
          val myGroupsMembershipsFut = ctx.run(myPostsMemberships)

          for {
            posts <- postsFut
            connection <- connectionsFut
            user <- userFut
            users <- myGroupsMembersFut
            memberships <- myGroupsMembershipsFut
          } yield {
            val postSet = posts.map(_.id).toSet
            (
              posts,
              connection.filter(c => (postSet contains c.sourceId) && (postSet contains c.targetId)),
              (users ++ user).toSet,
              memberships
            )
          }

        case None => // not logged in, can only see public posts
          val postsFut = ctx.run(publicPosts)
          val connectionsFut = ctx.run(query[Connection])
          for {
            posts <- postsFut
            connection <- connectionsFut
          } yield {
            val postSet = posts.map(_.id).toSet
            (
              posts,
              connection.filter(c => (postSet contains c.sourceId) && (postSet contains c.targetId)),
              Nil, Nil
            )
          }
      }
    }
  }
}
