package wust.db

import io.getquill._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._
import wust.ids._
import scala.util.{ Try, Success, Failure }

object Db extends Db(new PostgresAsyncContext[LowerCase]("db"))

class Db(val ctx: PostgresAsyncContext[LowerCase]) {
  import ctx._

  implicit val encodeGroupId = MappedEncoding[GroupId, IdType](_.id)
  implicit val decodeGroupId = MappedEncoding[IdType, GroupId](GroupId(_))
  implicit val encodeUserId = MappedEncoding[UserId, IdType](_.id)
  implicit val decodeUserId = MappedEncoding[IdType, UserId](UserId(_))
  implicit val encodePostId = MappedEncoding[PostId, IdType](_.id)
  implicit val decodePostId = MappedEncoding[IdType, PostId](PostId(_))
  implicit val encodeConnectionId = MappedEncoding[ConnectionId, IdType](_.id)
  implicit val decodeConnectionId =
    MappedEncoding[IdType, ConnectionId](ConnectionId(_))
  implicit val encodeContainmentId = MappedEncoding[ContainmentId, IdType](_.id)
  implicit val decodeContainmentId =
    MappedEncoding[IdType, ContainmentId](ContainmentId(_))
  implicit val encodeConnectableId =
    MappedEncoding[ConnectableId, IdType](_.id)
  implicit val decodeConnectableId =
    MappedEncoding[IdType, ConnectableId](UnknownConnectableId)

  implicit val userSchemaMeta = schemaMeta[User]("\"user\"") // user is a reserved word, needs to be quoted

  object post {
    // val createOwnedPostQuote = quote { (title: String, groupId: GroupId) =>
    //   infix"""with ins as (
    //     insert into post(id, title) values(DEFAULT, $title) returning id
    //   ) insert into ownership(postId, groupId) select id, $groupId from ins"""
    //     .as[Insert[Ownership]]
    // }

    // //TODO: this is a duplicate for apply -> merge
    // def createOwnedPost(title: String, groupId: GroupId): Future[PostId] = {
    //   ctx.run(
    //     createOwnedPostQuote(lift(title), lift(groupId)).returning(_.postId)
    //   )
    // }

    def createPublic(title: String): Future[Post] = {
      val post = Post(DEFAULT, title)

      //TODO
      //     val q = quote { createOwnedPost(lift(title), lift(group.id))).returning(_.postId) }
      //     ctx.run(q).map(id => post.copy(id = id))
      for {
        postId <- ctx.run(query[Post].insert(lift(post)).returning(_.id))
      } yield post.copy(id = postId)
    }

    def createOwned(title: String, groupId: GroupId): Future[(Post, Ownership)] = {
      val post = Post(DEFAULT, title)

      //TODO
      //     val q = quote { createOwnedPost(lift(title), lift(group.id))).returning(_.postId) }
      //     ctx.run(q).map(id => post.copy(id = id))
      ctx.transaction { _ =>
        for {
          postId <- ctx.run(query[Post].insert(lift(post)).returning(_.id))
          _ <- ctx.run(query[Ownership].insert(lift(Ownership(postId, groupId))))
        } yield (post.copy(id = postId), Ownership(postId, groupId))
      }
    }

    def apply(title: String, groupIdOpt: Option[GroupId]): Future[(Post, Option[Ownership])] = {
      groupIdOpt match {
        case Some(groupId) => createOwned(title, groupId).map {
          case (post, ownership) => (post, Option(ownership))
        }
        case None => createPublic(title).map { post =>
          (post, None)
        }
      }
    }

    def get(postId: PostId): Future[Option[Post]] = {
      val q = quote { query[Post].filter(_.id == lift(postId)).take(1) }
      ctx.run(q).map(_.headOption)
    }

    def update(post: Post): Future[Boolean] = {
      val q = quote {
        query[Post].filter(_.id == lift(post.id)).update(lift(post))
      }
      ctx.run(q).map(_ == 1)
    }

    def delete(postId: PostId): Future[Boolean] = {
      val postQ = quote { query[Post].filter(_.id == lift(postId)) }
      //TODO: quill delete.returning(_.id) to avoid 2 queries
      ctx.transaction { _ =>
        for {
          exists <- ctx.run(postQ.nonEmpty)
          _ <- ctx.run(postQ.delete)
        } yield exists
      }
    }
  }

  object connection {
    def apply(sourceId: PostId, targetId: ConnectableId): Future[Option[Connection]] = {
      val connection = Connection(DEFAULT, sourceId, targetId)
      val q = quote {
        query[Connection].insert(lift(connection)).returning(x => x.id)
      }
      ctx.run(q).map(id => Option(connection.copy(id = id))).recover { case _ => None }
    }

    def newPost(title: String, targetConnectableId: ConnectableId, groupIdOpt: Option[GroupId]): Future[Option[(Post, Connection, Option[Ownership])]] = {
      // TODO in one query:
      // val createPostAndConnection = quote {
      //   (title: String, targetId: ConnectableId) =>
      //     infix"""with ins as (
      //     insert into post(id, title) values(DEFAULT, $title) returning id
      //   ) insert into connection(id, sourceId, targetId) select 0, id, ${targetId.id} from ins"""
      //       .as[Insert[Connection]]
      // }

      // val q = quote { createPostAndConnection(lift(title), lift(targetId)) }
      // ctx.run(q).map(conn => (Post(conn.sourceId, title), conn))
      val targetIdOptFut: Future[Option[ConnectableId]] = targetConnectableId match {
        case postId: PostId =>
          ctx.run(query[Post].filter(_.id == lift(postId)).nonEmpty).map { exists =>
            if (exists) Some(postId)
            else None
          }
        case connectionId: ConnectionId =>
          ctx.run(query[Connection].filter(_.id == lift(connectionId)).nonEmpty).map { exists =>
            if (exists) Some(connectionId)
            else None
          }
        case _ => Future.successful(None)
      }

      targetIdOptFut.flatMap {
        case Some(targetId) =>
          for {
            (post, ownershipOpt) <- post(title, groupIdOpt)
            Some(connection) <- apply(post.id, targetId)
          } yield Option((post, connection, ownershipOpt))
        case None =>
          Future.successful(None)
      }
    }

    def delete(connId: ConnectionId): Future[Boolean] = {
      val connQ = quote { query[Connection].filter(_.id == lift(connId)) }
      //TODO: quill delete.returning(_.id) to avoid 2 queries
      ctx.transaction { _ =>
        for {
          exists <- ctx.run(connQ.nonEmpty)
          _ <- ctx.run(connQ.delete)
        } yield exists
      }
    }
  }

  object containment {
    def apply(parentId: PostId, childId: PostId): Future[Option[Containment]] = {
      val containment = Containment(DEFAULT, parentId, childId)
      val q = quote {
        query[Containment].insert(lift(containment)).returning(x => x.id)
      }
      ctx.run(q).map(id => Option(containment.copy(id = id))).recover { case _ => None }
    }

    def delete(contId: ContainmentId): Future[Boolean] = {
      val contQ = quote { query[Containment].filter(_.id == lift(contId)) }
      //TODO: quill delete.returning(_.id) to avoid 2 queries
      ctx.transaction { _ =>
        for {
          exists <- ctx.run(contQ.nonEmpty)
          _ <- ctx.run(contQ.delete)
        } yield exists
      }
    }
  }

  object user {
    import com.roundeights.hasher.Hasher
    def passwordDigest(password: String) = Hasher(password).bcrypt

    val initialRevision = 0
    private def implicitUserName = "anon-" + java.util.UUID.randomUUID.toString
    private def newRealUser(name: String): User = User(DEFAULT, name, isImplicit = false, initialRevision)
    private def newImplicitUser(): User = User(DEFAULT, implicitUserName, isImplicit = true, initialRevision)

    private val createUserAndPassword = quote { (name: String, digest: Array[Byte]) =>
      val revision = lift(initialRevision)
      infix"""with ins as (
        insert into "user"(id, name, isImplicit, revision) values(DEFAULT, $name, false, $revision) returning id
      ) insert into password(id, digest) select id, $digest from ins"""
        .as[Insert[User]]
    }

    private val createPasswordAndUpdateUser = quote {
      (id: UserId, name: String, digest: Array[Byte]) =>
        infix"""with ins as (
        insert into password(id, digest) values($id, $digest)
      ) update "user" where id = $id and isImplicit = true set name = $name, revision = revision + 1, isImplicit = false returning revision"""
          .as[Query[Int]] //TODO update? but does not support returning?
    }

    def apply(name: String, password: String): Future[Option[User]] = {
      val user = newRealUser(name)
      val digest = passwordDigest(password)
      val userIdQuote = quote {
        createUserAndPassword(lift(name), lift(digest)).returning(_.id)
      }
      ctx.run(userIdQuote)
        .map(id => Option(user.copy(id = id)))
        .recover { case _: Exception => None }
    }

    def createImplicitUser(): Future[User] = {
      val user = newImplicitUser()
      val q = quote { query[User].insert(lift(user)).returning(_.id) }
      val dbUser = ctx.run(q).map(id => user.copy(id = id))

      //TODO in user create transaction with one query?
      dbUser.flatMap(user => group.createForUser(user.id).map(_ => user))
    }

    def activateImplicitUser(id: UserId, name: String, password: String): Future[Option[User]] = {
      val digest = passwordDigest(password)
      //TODO
      // val user = User(id, name)
      // val q = quote { createPasswordAndUpdateUser(lift(id), lift(name), lift(digest)) }
      // ctx.run(q).map(revision => Some(user.copy(revision = revision)))
      ctx
        .run(query[User].filter(_.id == lift(id)))
        .flatMap(_.headOption
          .map { user =>
            val updatedUser = user.copy(
              name = name,
              isImplicit = false,
              revision = user.revision + 1)
            for {
              _ <- ctx.run(
                query[User].filter(_.id == lift(id)).update(lift(updatedUser)))
              _ <- ctx.run(query[Password].insert(lift(Password(id, digest))))
            } yield Option(updatedUser)
          }
          .getOrElse(Future.successful(None)))
        .recover { case _: Exception => None }
    }

    //TODO: http://stackoverflow.com/questions/5347050/sql-to-list-all-the-tables-that-reference-a-particular-column-in-a-table (at compile-time?)
    // def mergeImplicitUser(id: UserId, userId: UserId): Future[Boolean]

    def get(id: UserId): Future[Option[User]] = {
      val q = quote(query[User].filter(_.id == lift(id)).take(1))
      ctx.run(q).map(_.headOption)
    }

    def get(name: String, password: String): Future[Option[User]] = {
      val q = quote {
        query[User]
          .filter(_.name == lift(name))
          .join(query[Password])
          .on((u, p) => u.id == p.id)
          .take(1)
      }

      ctx
        .run(q)
        .map(_.headOption.collect {
          case (user, pw) if (passwordDigest(password) hash = pw.digest) =>
            user
        })
    }

    def checkIfEqualUserExists(user: User): Future[Boolean] = {
      import user._
      val q = quote(query[User]
        .filter(u => u.id == lift(id) && u.revision == lift(revision) && u.isImplicit == lift(isImplicit) && u.name == lift(name))
        .take(1))
      ctx.run(q).map(_.nonEmpty)
    }
  }

  object group {
    def createForUser(userId: UserId): Future[(UserGroup, Membership)] =
      ctx.transaction { _ =>
        //TODO report quill bug:
        // val q = quote(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        // --> produces: "INSERT INTO "usergroup" () VALUES ()"
        // --> should be: "INSERT INTO "usergroup" (id) VALUES (DEFAULT)"
        for {
          groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
          m <- ctx.run(query[Membership].insert(lift(Membership(groupId, userId)))) //TODO: what is m? What does it return?
        } yield (UserGroup(groupId), Membership(groupId, userId))
      }

    def addMember(groupId: GroupId, userId: UserId): Future[Membership] = {
      val q = quote(
        infix"""insert into membership(groupId, userId) values (${lift(groupId)}, ${lift(userId)})""".as[Insert[Membership]])
      ctx.run(q).map(_ => Membership(groupId, userId))
    }

    def hasAccessToPost(userId: UserId, postId: PostId): Future[Boolean] = {
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

    def members(groupId: GroupId): Future[Iterable[(User, Membership)]] = {
      ctx.run(for {
        usergroup <- query[UserGroup].filter(_.id == lift(groupId))
        membership <- query[Membership].filter(_.groupId == usergroup.id)
        user <- query[User].filter(_.id == membership.userId)
      } yield (user, membership))
    }

    def createInvite(groupId: GroupId, token: String): Future[Boolean] = {
      val q = quote(infix"""
        insert into groupInvite(groupId, token) values(${lift(groupId)}, ${lift(token)}) on conflict (groupId) do update set token = ${lift(token)}
      """.as[Insert[GroupInvite]])
      ctx.run(q).map(_ == 1)
    }

    def fromInvite(token: String): Future[Option[UserGroup]] = {
      val q = quote {
        for {
          invite <- query[GroupInvite].filter(_.token == lift(token)).take(1)
          usergroup <- query[UserGroup].filter(_.id == invite.groupId)
        } yield usergroup
      }
      ctx.run(q).map(_.headOption)
    }

  }

  object graph {
    type Graph = (Iterable[Post], Iterable[Connection], Iterable[Containment], Iterable[UserGroup], Iterable[Ownership], Iterable[User], Iterable[Membership])

    def getAllVisiblePosts(userIdOpt: Option[UserId]): Future[Graph] = {
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
              connection.filter(c => (postSet contains c.sourceId) && (postSet contains PostId(c.targetId.id))),
              containments.filter(c => (postSet contains c.parentId) && (postSet contains c.childId)),
              myGroups.map(UserGroup.apply),
              ownerships,
              (users ++ user).toSet,
              memberships)
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
              connection.filter(c => (postSet contains c.sourceId) && (postSet contains PostId(c.targetId.id))),
              containments.filter(c => (postSet contains c.parentId) && (postSet contains c.childId)),
              Nil, Nil, Nil, Nil)
          }
      }
    }
  }
}
