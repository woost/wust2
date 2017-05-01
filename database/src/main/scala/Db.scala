package wust

import io.getquill._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import wust.ids._

package object db {
  case class User(id: UserId, name: String, isImplicit: Boolean, revision: Int)
  object User {
    private def implicitUserName = "anon-" + java.util.UUID.randomUUID.toString
    val initialRevision = 0
    def apply(name: String): User = User(UserId(0), name, isImplicit = false, initialRevision)
    def apply(): User = User(UserId(0), implicitUserName, isImplicit = true, initialRevision)
  }

  case class Post(id: PostId, title: String)
  object Post { def apply(title: String): Post = Post(0L, title) }

  case class Connection(id: ConnectionId, sourceId: PostId, targetId: ConnectableId)
  object Connection {
    def apply(in: PostId, out: ConnectableId): Connection = Connection(0L, in, out)
  }

  //TODO: rename to Containment
  case class Containment(id: ContainmentId, parentId: PostId, childId: PostId)
  object Containment {
    def apply(parentId: PostId, childId: PostId): Containment =
      Containment(0L, parentId, childId)
  }

  case class Password(id: UserId, digest: Array[Byte])
  case class Membership(groupId: GroupId, userId: Option[UserId])
  case class GroupInvite(groupId: GroupId, token: String)
  case class UserGroup(id: GroupId)
  object Group {
    def apply(): UserGroup = UserGroup(GroupId(0))
    //TODO this should be a setting, it corresponds to the id of the public user group (V6__user_ownership.sql)
    def default = UserGroup(GroupId(1))
  }
  case class Ownership(postId: PostId, groupId: GroupId)

  lazy val ctx = new PostgresAsyncContext[LowerCase]("db")
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
    val createOwnedPostQuote = quote { (title: String, groupId: GroupId) =>
      infix"""with ins as (
        insert into post(id, title) values(DEFAULT, $title) returning id
      ) insert into ownership(postId, groupId) select id, $groupId from ins"""
        .as[Insert[Ownership]]
    }

    //TODO: this is a duplicate for apply -> merge
    def createOwnedPost(title: String, groupId: GroupId): Future[PostId] = {
      ctx.run(
        createOwnedPostQuote(lift(title), lift(groupId)).returning(_.postId)
      )
    }

    def apply(title: String, groupId: GroupId): Future[(Post, Ownership)] = {
      val post = Post(title)

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

    def get(id: PostId): Future[Option[Post]] = {
      val q = quote { query[Post].filter(_.id == lift(id)).take(1) }
      ctx.run(q).map(_.headOption)
    }

    def update(post: Post): Future[Boolean] = {
      val q = quote {
        query[Post].filter(_.id == lift(post.id)).update(lift(post))
      }
      ctx.run(q).map(_ == 1)
    }

    def delete(id: PostId): Future[Boolean] = {
      val q = quote { query[Post].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object connection {
    val createPostAndConnection = quote {
      (title: String, targetId: ConnectableId) =>
        infix"""with ins as (
        insert into post(id, title) values(DEFAULT, $title) returning id
      ) insert into connection(id, sourceId, targetId) select 0, id, ${targetId.id} from ins"""
          .as[Insert[Connection]]
    }

    def newPost(
      title: String,
      targetId: ConnectableId,
      groupId: GroupId
    ): Future[(Post, Connection, Ownership)] = {
      // TODO
      // val q = quote { createPostAndConnection(lift(title), lift(targetId)) }
      // ctx.run(q).map(conn => (Post(conn.sourceId, title), conn))

      for {
        (post, ownership) <- post(title, groupId)
        connection <- apply(post.id, targetId)
      } yield (post, connection, ownership)
    }

    def apply(sourceId: PostId, targetId: ConnectableId): Future[Connection] = {
      val connection = Connection(sourceId, targetId)
      val q = quote {
        query[Connection].insert(lift(connection)).returning(x => x.id)
      }
      ctx.run(q).map(id => connection.copy(id = id))
    }

    def delete(id: ConnectionId): Future[Boolean] = {
      val q = quote { query[Connection].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object containment {
    def apply(parentId: PostId, childId: PostId): Future[Containment] = {
      val containment = Containment(parentId, childId)
      val q = quote {
        query[Containment].insert(lift(containment)).returning(x => x.id)
      }
      ctx.run(q).map(id => containment.copy(id = id))
    }

    def delete(id: ContainmentId): Future[Boolean] = {
      val q = quote { query[Containment].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  //TODO object group
  object user {
    import com.roundeights.hasher.Hasher

    def passwordDigest(password: String) = Hasher(password).bcrypt

    val createUserAndPassword = quote { (name: String, digest: Array[Byte]) =>
      val revision = lift(User.initialRevision)
      infix"""with ins as (
        insert into "user"(id, name, isImplicit, revision) values(DEFAULT, $name, false, $revision) returning id
      ) insert into password(id, digest) select id, $digest from ins"""
        .as[Insert[User]]
    }

    val createPasswordAndUpdateUser = quote {
      (id: UserId, name: String, digest: Array[Byte]) =>
        infix"""with ins as (
        insert into password(id, digest) values($id, $digest)
      ) update "user" where id = $id and isImplicit = true set name = $name, revision = revision + 1, isImplicit = false returning revision"""
          .as[Query[Int]] //TODO update? but does not support returning?
    }

    def createGroupInvite(groupId: GroupId, token: String): Future[Boolean] = {
      val q = quote(infix"""
        insert into groupInvite(groupId, token) values(${lift(groupId)}, ${lift(token)}) on conflict (groupId) do update set token = ${lift(token)}
      """.as[Insert[GroupInvite]])
      ctx.run(q).map(_ == 1)
    }

    def groupIdFromInvite(token: String): Future[Option[GroupId]] = {
      val q = quote { query[GroupInvite].filter(_.token == lift(token)).map(_.groupId).take(1) }
      ctx.run(q).map(_.headOption)
    }

    def createGroupForUser(userId: UserId): Future[(UserGroup, Membership)] =
      ctx.transaction { _ =>
        //TODO report quill bug:
        // val q = quote(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        // --> produces: "INSERT INTO "group" () VALUES ()"
        // --> should be: "INSERT INTO "group" (id) VALUES (DEFAULT)"
        for {
          groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
          m <- ctx.run(query[Membership].insert(lift(Membership(groupId, Option(userId))))) //TODO: what is m? What does it return?
        } yield (UserGroup(groupId), Membership(groupId, Option(UserId(m))))
      }

    def addMember(groupId: GroupId, userId: UserId): Future[Membership] = {
      val q = quote(
        infix"""insert into membership(groupId, userId) values (${lift(groupId)}, ${lift(userId)})""".as[Insert[Membership]]
      )
      ctx.run(q).map(_ => Membership(groupId, Option(userId)))
    }

    def hasAccessToPost(userId: UserId, postId: PostId): Future[Boolean] = {
      val q = quote {
        query[Ownership]
          .filter(o => o.postId == lift(postId))
          .join(query[Membership].filter(m => m.userId.forall(_ == lift(userId)) || m.userId.isEmpty))
          .on((o, m) => o.groupId == m.groupId)
          .nonEmpty
      }

      ctx.run(q)
    }

    def apply(name: String, password: String): Future[Option[User]] = {
      val user = User(name)
      val digest = passwordDigest(password)
      val userIdQuote = quote {
        createUserAndPassword(lift(name), lift(digest)).returning(_.id)
      }
      val userId = ctx
        .run(userIdQuote)
        .map(id => Option(user.copy(id = id)))
        .recover { case _: Exception => None }

      //TODO in user create transaction with one query?
      userId.flatMap {
        case Some(user) =>
          createGroupForUser(user.id).map(_ => Option(user))
        case None => Future.successful(None)
      }
    }

    def createImplicitUser(): Future[User] = {
      val user = User()
      val q = quote { query[User].insert(lift(user)).returning(_.id) }
      val dbUser = ctx.run(q).map(id => user.copy(id = id))

      //TODO in user create transaction with one query?
      dbUser.flatMap(user => createGroupForUser(user.id).map(_ => user))
    }

    def activateImplicitUser(
      id: UserId,
      name: String,
      password: String
    ): Future[Option[User]] = {
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
              revision = user.revision + 1
            )
            for {
              _ <- ctx.run(
                query[User].filter(_.id == lift(id)).update(lift(updatedUser))
              )
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

    def group(user: User): Future[UserGroup] = { //TODO: Long
      //TODO: this is faking it, just take one group for user.
      //really need to know the private group of the user! --> column in user-table referencing group?
      val q = quote {
        query[Membership]
          .filter(_.userId == lift(Option(user.id)))
          .join(query[UserGroup])
          .on((m, g) => m.groupId == g.id)
          .map(_._2)
          .take(1)
      }

      ctx.run(q).map(_.head)
    }

    def checkEqualUserExists(user: User): Future[Boolean] = {
      import user._
      val q = quote(query[User]
        .filter(u => u.id == lift(id) && u.revision == lift(revision) && u.isImplicit == lift(isImplicit) && u.name == lift(name))
        .take(1))
      ctx.run(q).map(_.nonEmpty)
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

      userIdOpt match {
        case Some(userId) =>
          val myMemberships = quote {
            query[Membership].filter(m =>
              m.userId == lift(userIdOpt) || m.userId.isEmpty)
          }

          val myGroupsMemberships = quote { myMemberships.join(query[Membership]).on((myM, m) => myM.groupId == m.groupId).map { case (myM, m) => m } }
          // val myGroupsMembers = quote { myGroupsMemberships.join(query[User]).on((m, u) => m.userId.forall(_ == u.id)).map { case (m, u) => u } } //TODO: userid could be null
          val myGroupsMembers = quote {
            for {
              myM <- query[Membership].filter(myM => myM.userId == lift(Option(userId)))
              otherM <- query[Membership].filter(otherM => otherM.groupId == myM.groupId)
              u <- query[User].join(u => otherM.userId.forall(_ == u.id))
            } yield u
          }

          val visibleOwnerships = ownerships(myMemberships.map(_.groupId))

          //TODO: we get more edges than needed, because some posts are filtered out by ownership
          val postsFut = ctx.run(ownedPosts(visibleOwnerships))
          val connectionsFut = ctx.run(query[Connection])
          val containmentsFut = ctx.run(query[Containment])
          val myGroupsFut = ctx.run(myMemberships.map(_.groupId))
          val myGroupsMembershipsFut = ctx.run(myGroupsMemberships)
          val myGroupsMembersFut = ctx.run(myGroupsMembers)

          val ownershipsFut = ctx.run(visibleOwnerships)
          for {
            posts <- postsFut
            connection <- connectionsFut
            containments <- containmentsFut
            myGroups <- myGroupsFut
            ownerships <- ownershipsFut
            users <- myGroupsMembersFut
            memberships <- myGroupsMembershipsFut
          } yield {
            (
              posts,
              connection,
              containments,
              myGroups.map(UserGroup.apply),
              ownerships,
              users,
              memberships
            )
          }

        case None => // not logged in, can only see posts of public groups
          val publicGroupIds = quote { query[UserGroup].join(query[Membership]).on((u, m) => u.id == m.groupId && m.userId.isEmpty).map { case (u, m) => m.groupId } }

          val postsFut = ctx.run(ownedPosts(ownerships(publicGroupIds)))
          val connectionsFut = ctx.run(query[Connection])
          val containmentsFut = ctx.run(query[Containment])
          for {
            posts <- postsFut
            connection <- connectionsFut
            containments <- containmentsFut
          } yield {
            (
              posts,
              connection,
              containments,
              Nil, Nil, Nil, Nil
            )
          }
      }
    }
  }
}
