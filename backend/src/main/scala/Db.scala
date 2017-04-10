package wust.backend

import wust.util.collection._
import wust.graph._
import wust.api.User

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import io.getquill._

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

  case class Password(id: Long, digest: Array[Byte]) //TODO id: User, encoder: _.id and for the others
  case class Ownership(postId: PostId, groupId: Long)
  case class UsergroupMember(groupId: Long, userId: Option[Long])
  case class Usergroup(id: Long)
  object Usergroup {
    def apply(): Usergroup = Usergroup(0L)
    //TODO this should be a setting, it corresponds to the id of the public user group (V6__user_ownership.sql)
    def default = Usergroup(1L)
  }

  object post {
    val createOwnedPost = quote { (title: String, groupId: Long) =>
      infix"""with ins as (
        insert into post(id, title) values(DEFAULT, $title) returning id
      ) insert into ownership(postId, groupId) select id, ${groupId} from ins""".as[Insert[Ownership]]
    }

    def apply(title: String, groupOpt: Option[Usergroup]): Future[Post] = {
      val post = Post(title)
      val group = groupOpt.getOrElse(Usergroup.default)

      //TODO
      //     val q = quote { createOwnedPost(lift(title), lift(group.id))).returning(_.postId) }
      //     ctx.run(q).map(id => post.copy(id = id))
      ctx.transaction { ev =>
        for {
          postId <- ctx.run(query[Post].insert(lift(post)).returning(_.id))
          ownershipId <- ctx.run(query[Ownership].insert(lift(Ownership(postId, group.id))))
        } yield post.copy(id = postId)
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

    def newPost(title: String, targetId: ConnectableId, group: Option[Usergroup]): Future[(Post, Connects)] = {
      // TODO
      // val q = quote { createPostAndConnects(lift(title), lift(targetId)) }
      // ctx.run(q).map(conn => (Post(conn.sourceId, title), conn))

      for {
        post <- post(title, group)
        connects <- apply(post.id, targetId)
      } yield (post, connects)
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

    //TODO in user create transaction with one query?
    private def createUsergroupForUser(id: Long): Unit = ctx.transaction { ev =>
      //TODO
      // val q = quote(query[Usergroup].insert(lift(Usergroup())).returning(_.id))
      val q = quote(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[Usergroup]].returning(_.id))
      val runned = ctx.run(q)
      runned.foreach { group =>
        val q = quote(query[UsergroupMember].insert(lift(UsergroupMember(group, Option(id)))))
        ctx.run(q).foreach(println _)
      }
      runned
    }

    def apply(name: String, password: String): Future[Option[User]] = {
      val user = User(name)
      val digest = passwordDigest(password)
      val q = quote { createUserAndPassword(lift(name), lift(digest)).returning(_.id) }
      val dbUser = ctx.run(q)
        .map(id => Option(user.copy(id = id)))
        .recover { case _: Exception => None }

      dbUser.foreach(_.foreach(u => createUsergroupForUser(u.id)))
      dbUser
    }

    def createImplicitUser(): Future[User] = {
      val user = User()
      val q = quote { query[User].insert(lift(user)).returning(_.id) }
      val dbUser = ctx.run(q).map(id => user.copy(id = id))

      dbUser.foreach(u => createUsergroupForUser(u.id))
      dbUser
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

    def group(user: User): Future[Usergroup] = {
      //TODO: this is faking it, just take one group for user.
      //really need to know the private usergroup of the user!
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

    def check(user: User): Future[Boolean] = {
      // TODO: in query
      get(user.id).map(_.map { dbUser =>
        dbUser.revision == user.revision && dbUser.isImplicit == user.isImplicit && dbUser.name == user.name
      }.getOrElse(false))
    }
  }

  object graph {
    def get(user: Option[User]): Future[Graph] = {
      val postQuery = quote(query[Post]
        .join(query[Ownership])
        .on((p, o) => p.id == o.postId) //: (Post, Ownership)
        .join(query[UsergroupMember].filter(s => s.userId == lift(user.map(_.id)) || s.userId.isEmpty))
        .on((po, u) => po._2.groupId == u.groupId)
        .map { case (po, u) => po._1 })

      //TODO: we get more edges than needed, because some posts are filtered out by ownership
      val postFut = ctx.run(postQuery)
      val connectsFut = ctx.run(query[Connects])
      val containsFut = ctx.run(query[Contains])
      for {
        posts <- postFut
        connects <- connectsFut
        contains <- containsFut
      } yield {
        Graph(
        posts.by(_.id),
        connects.by(_.id),
        contains.by(_.id)
      )}
    }
  }
}
