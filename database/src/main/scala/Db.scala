package wust.db

import io.getquill._
import wust.ids._
import com.typesafe.config.Config

import scala.concurrent.{ ExecutionContext, Future, Await }
import scala.concurrent.duration._
import scalaz.Tag
import wust.ids._
import wust.util._
import scala.util.{ Try, Success, Failure }

object Db {
  def apply(config: Config) = {
    new Db(new PostgresAsyncContext[LowerCase](config))
  }
}

class Db(val ctx: PostgresAsyncContext[LowerCase]) {
  import data._
  import ctx._

  implicit val encodeGroupId = MappedEncoding[GroupId, IdType](Tag.unwrap _)
  implicit val decodeGroupId = MappedEncoding[IdType, GroupId](GroupId _)
  implicit val encodeUserId = MappedEncoding[UserId, IdType](Tag.unwrap _)
  implicit val decodeUserId = MappedEncoding[IdType, UserId](UserId _)
  implicit val encodePostId = MappedEncoding[PostId, UuidType](Tag.unwrap _)
  implicit val decodePostId = MappedEncoding[UuidType, PostId](PostId _)

  implicit val userSchemaMeta = schemaMeta[User]("\"user\"") // user is a reserved word, needs to be quoted

  case class RawPost(id: PostId, title: String, isDeleted: Boolean)
  object RawPost {
    def apply(post: Post, isDeleted: Boolean): RawPost = RawPost(post.id, post.title, isDeleted)
  }

  implicit class IngoreDuplicateKey[T](q: Insert[T]) {
    def ignoreDuplicates = quote(infix"$q ON CONFLICT DO NOTHING".as[Insert[T]])
  }

  object post {
    // post ids are unique, so the methods can assume that at max 1 row was touched in each operation

    private val insert = quote { (post: RawPost) => query[RawPost].insert(post).ignoreDuplicates } //TODO FIXME this should only DO NOTHING if id and title are equal to db row. now this will hide conflict on post ids!!

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

    def update(post: Post)(implicit ec: ExecutionContext): Future[Boolean] = update(Set(post))
    def update(posts: Set[Post])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(posts.toList).foreach(post => query[RawPost].filter(_.id == post.id).update(_.title -> post.title)))
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
        .map(_.forall(_ <= 1))
    }

    def getGroups(postId: PostId)(implicit ec: ExecutionContext): Future[List[UserGroup]] = {
      ctx.run {
        for {
          ownership <- query[Ownership].filter(_.postId == lift(postId))
          usergroup <- query[UserGroup].filter(_.id == ownership.groupId)
        } yield usergroup
      }
    }

    def getGroupIds(postIds: Set[PostId])(implicit ec: ExecutionContext): Future[Map[PostId, Set[GroupId]]] = {
      val q = quote {
        infix"""
        select ownership.* from unnest(${lift(postIds.toList)} :: varchar(36)[]) inputPostId join ownership on ownership.postid = inputPostId
      """.as[Query[Ownership]]
      }

      ctx.run(q).map(_.groupBy(_.postId).mapValues(_.map(_.groupId).toSet))
    }
  }

  object ownership {
    private val insert = quote { (ownership: Ownership) => query[Ownership].insert(ownership).ignoreDuplicates }

    def apply(ownership: Ownership)(implicit ec: ExecutionContext): Future[Boolean] = apply(Set(ownership))
    def apply(ownerships: Set[Ownership])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(ownerships.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1))
    }

    def delete(ownership: Ownership)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(ownership))
    def delete(ownerships: Set[Ownership])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(ownerships.toList).foreach(ownership => query[Ownership].filter(c => c.groupId == ownership.groupId && c.postId == ownership.postId).delete))
        .map(_.forall(_ <= 1))
    }
  }

  object connection {
    private val insert = quote { (connection: Connection) => query[Connection].insert(connection).ignoreDuplicates }

    def apply(connection: Connection)(implicit ec: ExecutionContext): Future[Boolean] = apply(Set(connection))
    def apply(connections: Set[Connection])(implicit ec: ExecutionContext): Future[Boolean] = {
      // This is a quill batch action:
      ctx.run(liftQuery(connections.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1)).recoverValue(false)
    }

    def delete(connection: Connection)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(connection))
    def delete(connections: Set[Connection])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(connections.toList).foreach(connection => query[Connection].filter(c => c.sourceId == connection.sourceId && c.targetId == connection.targetId).delete))
        .map(_.forall(_ <= 1))
    }
  }

  object containment {
    private val insert = quote { (containment: Containment) => query[Containment].insert(containment).ignoreDuplicates }

    def apply(containment: Containment)(implicit ec: ExecutionContext): Future[Boolean] = apply(Set(containment))
    def apply(containments: Set[Containment])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(containments.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1)).recoverValue(false)
    }

    def delete(containment: Containment)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(containment))
    def delete(containments: Set[Containment])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(liftQuery(containments.toList).foreach(containment => query[Containment].filter(c => c.parentId == containment.parentId && c.childId == containment.childId).delete))
        .map(_.forall(_ <= 1))
    }
  }

  object user {
    private val initialRevision = 0
    private def implicitUserName = "anon-" + java.util.UUID.randomUUID.toString
    private def newRealUser(name: String): User = User(DEFAULT, name, isImplicit = false, initialRevision)
    private def newImplicitUser(): User = User(DEFAULT, implicitUserName, isImplicit = true, initialRevision)

    def apply(name: String, digest: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
      val user = newRealUser(name)
      val q = quote { infix"""
        with ins as (
          insert into "user" values(DEFAULT, ${lift(user.name)}, ${lift(user.revision)}, false) returning id
        ) insert into password(id, digest) select id, ${lift(digest)} from ins
      """.as[Insert[User]].returning(_.id) }

      ctx.run(q)
        .map(id => Option(user.copy(id = id)))
        .recoverValue(None)
    }

    def createImplicitUser()(implicit ec: ExecutionContext): Future[User] = {
      val user = newImplicitUser()
      val q = quote { query[User].insert(lift(user)).returning(_.id) }
      ctx.run(q).map(id => user.copy(id = id))
    }

    //TODO one query
    def activateImplicitUser(id: UserId, name: String, passwordDigest: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.transaction { implicit ec =>
        ctx.run(query[User].filter(u => u.id == lift(id) && u.isImplicit == true))
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

    //TODO: http://stackoverflow.com/questions/5347050/sql-to-list-all-the-tables-that-reference-a-particular-column-in-a-table (at compile-time?)
    def mergeImplicitUser(implicitId: UserId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      if (implicitId == userId) Future.successful(false)
      else {
        val q = quote { infix"""
          with existingUser as (
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
  }

  object group {
    def get(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[UserGroup]] = {
      ctx.run(query[UserGroup].filter(_.id == lift(groupId))).map(_.headOption)
    }
    def createForUser(userId: UserId)(implicit ec: ExecutionContext): Future[Option[(User, Membership, UserGroup)]] = {
      ctx.transaction { implicit ec =>
        //TODO report quill bug:
        // val q = quote(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        // --> produces: "INSERT INTO "usergroup" () VALUES ()"
        // --> should be: "INSERT INTO "usergroup" (id) VALUES (DEFAULT)"
        val userOptFut = ctx.run(query[User].filter(_.id == lift(userId))).map(_.headOption)
        userOptFut.flatMap { userOpt =>
          userOpt match {
            case Some(_) =>
              for {
                groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
                m <- ctx.run(query[Membership].insert(lift(Membership(userId, groupId)))) //TODO: what is m? What does it return?
                user <- ctx.run(query[User].filter(_.id == lift(userId)))
              } yield Option((user.head, Membership(userId, groupId), UserGroup(groupId)))
            case None => Future.successful(None)
          }
        }
      }.recoverValue(None)
    }

    def addMember(groupId: GroupId, userId: UserId)(implicit ec: ExecutionContext): Future[Option[(User, Membership, UserGroup)]] = {
      ctx.transaction { implicit ec =>
        for {
          _ <- ctx.run(infix"""insert into membership(groupId, userId) values (${lift(groupId)}, ${lift(userId)}) on conflict do nothing""".as[Insert[Membership]])
          user <- ctx.run(query[User].filter(_.id == lift(userId)))
          userGroup <- ctx.run(query[UserGroup].filter(_.id == lift(groupId)))
        } yield Option(user.head, Membership(userId, groupId), userGroup.head)
      }.recoverValue(None)
    }

    def isMember(groupId: GroupId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(query[Membership].filter(m => m.groupId == lift(groupId) && m.userId == lift(userId)).nonEmpty)
    }

    def hasAccessToPost(userId: UserId, postId: PostId)(implicit ec: ExecutionContext): Future[Boolean] = {
      //TODO: more efficient
      val q1 = quote {
        query[Ownership]
          .filter(o => o.postId == lift(postId))
          .isEmpty
      }

      val q2 = quote {
        query[Ownership]
          .filter(o => o.postId == lift(postId))
          .join(query[Membership].filter(_.userId == lift(userId)))
          .on((o, m) => o.groupId == m.groupId)
          .nonEmpty
      }

      for {
        noOwnership <- ctx.run(q1)
        ownershipWhereUserIsMember <- ctx.run(q2)
      } yield noOwnership || ownershipWhereUserIsMember
    }

    def members(groupId: GroupId)(implicit ec: ExecutionContext): Future[List[(User, Membership)]] = {
      ctx.run(for {
        usergroup <- query[UserGroup].filter(_.id == lift(groupId))
        membership <- query[Membership].filter(_.groupId == usergroup.id)
        user <- query[User].filter(_.id == membership.userId)
      } yield (user, membership))
    }

    def memberships(userId: UserId)(implicit ec: ExecutionContext): Future[List[(UserGroup, Membership)]] = {
      ctx.run(
        for {
          membership <- query[Membership].filter(m => m.userId == lift(userId))
          usergroup <- query[UserGroup].filter(_.id == membership.groupId)
        } yield (usergroup, membership)
      )
    }

    def setInviteToken(groupId: GroupId, token: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run {
        infix"""
          insert into groupInvite(groupId, token) values(${lift(groupId)}, ${lift(token)}) on conflict (groupId) do update set token = ${lift(token)}
        """.as[Insert[GroupInvite]]
      }.map(_ => true).recoverValue(false)
    }

    def getInviteToken(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[String]] = {
      ctx.run(query[GroupInvite].filter(_.groupId == lift(groupId)).map(_.token)).map(_.headOption)
    }

    def fromInvite(token: String)(implicit ec: ExecutionContext): Future[Option[UserGroup]] = {
      ctx.run {
        for {
          invite <- query[GroupInvite].filter(_.token == lift(token)).take(1)
          usergroup <- query[UserGroup].filter(_.id == invite.groupId)
        } yield usergroup
      }.map(_.headOption)
    }

    def getOwnedPosts(groupId: GroupId)(implicit ec: ExecutionContext): Future[List[Post]] = {
      ctx.run {
        for {
          ownership <- query[Ownership].filter(o => o.groupId == lift(groupId))
          post <- query[Post].join(p => p.id == ownership.postId)
        } yield post
      }
    }
  }

  object graph {
    def getAllVisiblePosts(userIdOpt: Option[UserId])(implicit ec: ExecutionContext): Future[Graph] = {
      def ownerships(groupIds: Quoted[Query[GroupId]]) = quote {
        for {
          gid <- groupIds
          o <- query[Ownership].join(o => o.groupId == gid)
        } yield o
      }

      def ownedPosts(ownerships: Quoted[Query[Ownership]]) = quote {
        for {
          o <- ownerships
          p <- query[Post].join(p => p.id == o.postId)
        } yield p
      }

      val publicPosts = quote {
        query[Post].leftJoin(query[Ownership]).on((p, o) => p.id == o.postId).filter { case (p, o) => o.isEmpty }.map { case (p, o) => p }
      }

      userIdOpt match {
        case Some(userId) =>
          val myMemberships = quote {
            query[Membership].filter(m => m.userId == lift(userId))
          }

          val myGroupsMemberships = quote {
            for {
              myM <- myMemberships
              otherM <- query[Membership].filter(otherM => otherM.groupId == myM.groupId)
            } yield otherM
          }

          val myGroupsMembers = quote {
            for {
              otherM <- myGroupsMemberships
              u <- query[User].join(u => u.id == otherM.userId)
            } yield u
          }

          val visibleOwnerships = ownerships(myMemberships.map(_.groupId))

          //TODO: we get more edges than needed, because some posts are filtered out by ownership
          val userFut = ctx.run(query[User].filter(_.id == lift(userId)))
          val postsFut = for (owned <- ctx.run(ownedPosts(visibleOwnerships)); public <- ctx.run(publicPosts)) yield owned ++ public
          val connectionsFut = ctx.run(query[Connection])
          val containmentsFut = ctx.run(query[Containment])
          val myGroupsFut = ctx.run(myMemberships.map(_.groupId))
          val myGroupsMembersFut = ctx.run(myGroupsMembers)
          val myGroupsMembershipsFut = ctx.run(myGroupsMemberships)

          val ownershipsFut = ctx.run(visibleOwnerships)
          for {
            posts <- postsFut
            connection <- connectionsFut
            containments <- containmentsFut
            myGroups <- myGroupsFut
            ownerships <- ownershipsFut
            user <- userFut
            users <- myGroupsMembersFut
            memberships <- myGroupsMembershipsFut
          } yield {
            val postSet = posts.map(_.id).toSet
            (
              posts,
              connection.filter(c => (postSet contains c.sourceId) && (postSet contains c.targetId)),
              containments.filter(c => (postSet contains c.parentId) && (postSet contains c.childId)),
              myGroups.map(UserGroup.apply),
              ownerships,
              (users ++ user).toSet,
              memberships
            )
          }

        case None => // not logged in, can only see posts of public groups
          val postsFut = ctx.run(publicPosts)
          val connectionsFut = ctx.run(query[Connection])
          val containmentsFut = ctx.run(query[Containment])
          for {
            posts <- postsFut
            connection <- connectionsFut
            containments <- containmentsFut
          } yield {
            val postSet = posts.map(_.id).toSet
            (
              posts,
              connection.filter(c => (postSet contains c.sourceId) && (postSet contains c.targetId)),
              containments.filter(c => (postSet contains c.parentId) && (postSet contains c.childId)),
              Nil, Nil, Nil, Nil
            )
          }
      }
    }
  }
}
