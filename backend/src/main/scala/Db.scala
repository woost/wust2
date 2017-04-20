package wust.backend

import wust.util.collection._
import wust.graph._
import wust.api
import wust.api.User

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.getquill._
import collection.breakOut

object Db {
  lazy val ctx = new PostgresAsyncContext[LowerCase]("db")
  import ctx._

  implicit val encodePostId = MappedEncoding[PostId, IdType](_.id)
  implicit val decodePostId = MappedEncoding[IdType, PostId](PostId(_))
  implicit val encodeConnectsId = MappedEncoding[ConnectsId, IdType](_.id)
  implicit val decodeConnectsId = MappedEncoding[IdType, ConnectsId](ConnectsId(_))
  implicit val encodeContainsId = MappedEncoding[ContainsId, IdType](_.id)
  implicit val decodeContainsId = MappedEncoding[IdType, ContainsId](ContainsId(_))
  implicit val encodeConnectableId = MappedEncoding[ConnectableId, IdType](_.id)
  implicit val decodeConnectableId = MappedEncoding[IdType, ConnectableId](UnknownConnectableId(_))

  implicit val userSchemaMeta = schemaMeta[User]("\"user\"") // user is a reserved word, needs to be quoted

  case class Password(id: Long, digest: Array[Byte])
  case class UsergroupMember(groupId: Long, userId: Option[Long]) //TODO: rename to UserGroupMember
  case class Usergroup(id: Long) //TODO: rename to UserGroup
  object Usergroup {
    def apply(): Usergroup = Usergroup(0L)
    //TODO this should be a setting, it corresponds to the id of the public user group (V6__user_ownership.sql)
    def default = Usergroup(1L)
  }

  object post {
    val createOwnedPostQuote = quote { (title: String, groupId: Long) =>
      infix"""with ins as (
        insert into post(id, title) values(DEFAULT, $title) returning id
      ) insert into ownership(postId, groupId) select id, $groupId from ins""".as[Insert[Ownership]]
    }

    def createOwnedPost(title: String, groupId: Long): Future[PostId] = {
      ctx.run(createOwnedPostQuote(lift(title), lift(groupId)).returning(_.postId))
    }

    def apply(title: String, groupId: Long): Future[(Post, Ownership)] = {
      val post = Post(title)

      //TODO
      //     val q = quote { createOwnedPost(lift(title), lift(group.id))).returning(_.postId) }
      //     ctx.run(q).map(id => post.copy(id = id))
      ctx.transaction { ev =>
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
      val q = quote { query[Post].filter(_.id == lift(post.id)).update(lift(post)) }
      ctx.run(q).map(_ == 1)
    }

    def delete(id: PostId): Future[Boolean] = {
      val q = quote { query[Post].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object connects {
    val createPostAndConnects = quote { (title: String, targetId: ConnectableId) =>
      infix"""with ins as (
        insert into post(id, title) values(DEFAULT, $title) returning id
      ) insert into connects(id, sourceId, targetId) select 0, id, ${targetId.id} from ins""".as[Insert[Connects]]
    }

    def newPost(title: String, targetId: ConnectableId, groupId: Long): Future[(Post, Connects, Ownership)] = {
      // TODO
      // val q = quote { createPostAndConnects(lift(title), lift(targetId)) }
      // ctx.run(q).map(conn => (Post(conn.sourceId, title), conn))

      for {
        (post, ownership) <- post(title, groupId)
        connects <- apply(post.id, targetId)
      } yield (post, connects, ownership)
    }

    def apply(sourceId: PostId, targetId: ConnectableId): Future[Connects] = {
      val connects = Connects(sourceId, targetId)
      val q = quote { query[Connects].insert(lift(connects)).returning(x => x.id) }
      ctx.run(q).map(id => connects.copy(id = id))
    }

    def delete(id: ConnectsId): Future[Boolean] = {
      val q = quote { query[Connects].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object contains {
    def apply(parentId: PostId, childId: PostId): Future[Contains] = {
      val contains = Contains(parentId, childId)
      val q = quote { query[Contains].insert(lift(contains)).returning(x => x.id) }
      ctx.run(q).map(id => contains.copy(id = id))
    }

    def delete(id: ContainsId): Future[Boolean] = {
      val q = quote { query[Contains].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object user {
    import com.roundeights.hasher.Hasher

    def passwordDigest(password: String) = Hasher(password).bcrypt

    val createUserAndPassword = quote { (name: String, digest: Array[Byte]) =>
      val revision = lift(User.initialRevision)
      infix"""with ins as (
        insert into "user"(id, name, isImplicit, revision) values(DEFAULT, $name, false, $revision) returning id
      ) insert into password(id, digest) select id, $digest from ins""".as[Insert[User]]
    }

    val createPasswordAndUpdateUser = quote { (id: Long, name: String, digest: Array[Byte]) =>
      infix"""with ins as (
        insert into password(id, digest) values($id, $digest)
      ) update "user" where id = $id and isImplicit = true set name = $name, revision = revision + 1, isImplicit = false returning revision""".as[Query[Int]] //TODO update? but does not support returning?
    }

    def createUserGroup(): Future[Long] = {
      val q = quote(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[Usergroup]].returning(_.id))
      ctx.run(q)
    }

    def createUsergroupForUser(userId: Long): Future[Usergroup] = ctx.transaction { ev =>
      //TODO report quill bug:
      // val q = quote(query[Usergroup].insert(lift(Usergroup())).returning(_.id))
      // --> produces: "INSERT INTO usergroup () VALUES ()"
      // --> should be: "INSERT INTO usergroup (id) VALUES (DEFAULT)"
      val q = quote(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[Usergroup]].returning(_.id))
      ctx.run(q).flatMap { group =>
        val q = quote(query[UsergroupMember].insert(lift(UsergroupMember(group, Option(userId)))))
        ctx.run(q).map(_ => Usergroup(group))
      }
    }

    def addMember(groupId: Long, userId: Long): Future[Long] = {
      val q = quote(infix"""insert into usergroupmember(groupId, userId) values (${lift(groupId)}, ${lift(userId)})""".as[Insert[UsergroupMember]])
      ctx.run(q)
    }

    def hasAccessToPost(userId: Long, postId: PostId): Future[Boolean] = {
      val q = quote {
        query[Ownership].filter(o => o.postId == lift(postId))
          .join(query[UsergroupMember].filter(m => m.userId.forall(_ == lift(userId)) || m.userId.isEmpty))
          .on((o, m) => o.groupId == m.groupId)
          .nonEmpty
      }

      ctx.run(q)
    }

    def apply(name: String, password: String): Future[Option[User]] = {
      val user = User(name)
      val digest = passwordDigest(password)
      val userIdQuote = quote { createUserAndPassword(lift(name), lift(digest)).returning(_.id) }
      val userId = ctx.run(userIdQuote)
        .map(id => Option(user.copy(id = id)))
        .recover { case _: Exception => None }

      //TODO in user create transaction with one query?
      userId.flatMap {
        case Some(user) => createUsergroupForUser(user.id).map(_ => Option(user))
        case None => Future.successful(None)
      }
    }

    def createImplicitUser(): Future[User] = {
      val user = User()
      val q = quote { query[User].insert(lift(user)).returning(_.id) }
      val dbUser = ctx.run(q).map(id => user.copy(id = id))

      //TODO in user create transaction with one query?
      dbUser.flatMap(user => createUsergroupForUser(user.id).map(_ => user))
    }

    def activateImplicitUser(id: Long, name: String, password: String): Future[Option[User]] = {
      val digest = passwordDigest(password)
      //TODO
      // val user = User(id, name)
      // val q = quote { createPasswordAndUpdateUser(lift(id), lift(name), lift(digest)) }
      // ctx.run(q).map(revision => Some(user.copy(revision = revision)))
      ctx.run(query[User].filter(_.id == lift(id))).flatMap(_.headOption.map { user =>
        val updatedUser = user.copy(name = name, isImplicit = false, revision = user.revision + 1)
        for {
          newUser <- ctx.run(query[User].filter(_.id == lift(id)).update(lift(updatedUser)))
          pw <- ctx.run(query[Password].insert(lift(Password(id, digest))))
        } yield Option(updatedUser)
      }.getOrElse(Future.successful(None)))
        .recover { case e: Exception => None }
    }

    //TODO: http://stackoverflow.com/questions/5347050/sql-to-list-all-the-tables-that-reference-a-particular-column-in-a-table (at compile-time?)
    // def mergeImplicitUser(id: Long, userId: Long): Future[Boolean]

    def get(id: Long): Future[Option[User]] = {
      val q = quote(query[User].filter(_.id == lift(id)).take(1))
      ctx.run(q).map(_.headOption)
    }

    def get(name: String, password: String): Future[Option[User]] = {
      val q = quote {
        query[User].filter(_.name == lift(name)).join(query[Password]).on((u, p) => u.id == p.id).take(1)
      }

      ctx.run(q).map(_.headOption.collect {
        case (user, pw) if (passwordDigest(password) hash = pw.digest) => user
      })
    }

    def group(user: User): Future[Usergroup] = { //TODO: Long
      //TODO: this is faking it, just take one group for user.
      //really need to know the private usergroup of the user! --> column in user-table referencing group?
      val q = quote {
        query[UsergroupMember]
          .filter(_.userId == lift(Option(user.id)))
          .join(query[Usergroup])
          .on((m, g) => m.groupId == g.id)
          .map(_._2)
          .take(1)
      }

      ctx.run(q).map(_.head)
    }

    //TODO we need some concept for ids...long/user/postid
    def allGroups(userId: Long): Future[Seq[api.UserGroup]] = {
      // all groups where user is explicitly a member

      val q = quote {
        for {
          m <- query[UsergroupMember].filter(m => m.userId == lift(Option(userId)))
          m1 <- query[UsergroupMember].filter(m1 => m1.groupId == m.groupId)
          u <- query[User].join(u => m1.userId.forall(_ == u.id))
        } yield (m.groupId, u.id, u.name)
      }

      ctx.run(q).map {
        _.groupBy(_._1).map {
          case (groupId, users) => api.UserGroup(groupId, users.map {
            case (_, uid, uname) => api.ClientUser(uid, uname)
          })
        }.toSeq
      }
    }

    def checkEqualUserExists(user: User): Future[Boolean] = {
      import user._
      val q = quote(query[User].filter(
        u => u.id == lift(id) && u.revision == lift(revision) && u.isImplicit == lift(isImplicit) && u.name == lift(name)
      ).take(1))
      ctx.run(q).map(_.nonEmpty)
    }
  }

  object graph {
    def getUnion(userId: Option[Long], parentIds: Set[PostId]): Future[Graph] = {
      //TODO: in stored procedure
      getAllVisiblePosts(userId).map { graph =>
        val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
        (graph -- graph.postsById.keys.filterNot(transitiveChildren))
      }
    }

    def getAllVisiblePosts(userId: Option[Long]): Future[Graph] = {
      val myMemberships = quote { query[UsergroupMember].filter(m => (m.userId == lift(userId) || m.userId.isEmpty)) }
      val visibleOwnerships = quote {
        for {
          m <- myMemberships
          o <- query[Ownership].join(o => o.groupId == m.groupId)
        } yield o
      }

      val visiblePosts = quote {
        for {
          o <- visibleOwnerships
          p <- query[Post].join(p => p.id == o.postId)
        } yield p
      }

      //TODO: we get more edges than needed, because some posts are filtered out by ownership
      val postFut = ctx.run(visiblePosts)
      val connectsFut = ctx.run(query[Connects])
      val containsFut = ctx.run(query[Contains])
      val myGroupsFut = ctx.run(myMemberships.map(_.groupId))
      val ownershipsFut = ctx.run(visibleOwnerships)
      for {
        posts <- postFut
        connects <- connectsFut
        contains <- containsFut
        myGroups <- myGroupsFut
        ownerships <- ownershipsFut
      } yield {
        Graph(
          posts,
          connects,
          contains,
          myGroups,
          ownerships
        ).consistent // TODO: consistent should not be necessary here
      }
    }
  }
}
