package backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.roundeights.hasher.Hasher

import graph._

object Db {
  import io.getquill._

  private lazy val ctx = new PostgresAsyncContext[LowerCase]("db")
  import ctx._

  object post {
    def apply(title: String): Future[Post] = {
      val post = Post(title)
      val q = quote { query[Post].insert(lift(post)).returning(_.id) }
      ctx.run(q).map(id => post.copy(id = id))
    }

    def get(id: AtomId): Future[Option[Post]] = {
      val q = quote { query[Post].filter(_.id == lift(id)).take(1) }
      ctx.run(q).map(_.headOption)
    }

    def update(post: Post): Future[Boolean] = {
      val q = quote { query[Post].filter(_.id == lift(post.id)).update(lift(post)) }
      ctx.run(q).map(_ == 1)
    }

    def delete(id: AtomId): Future[Boolean] = {
      val q = quote { query[Post].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object connects {
    def apply(sourceId: AtomId, targetId: AtomId): Future[Option[Connects]] = {
      val connects = Connects(sourceId, targetId)
      val q = quote {
        query[Connects].insert(lift(connects)).returning(x => x.id)
      }
      val newId = ctx.run(q).map(Some(_)).recover {
        case e: /*GenericDatabaseException*/ Exception => None
      }

      newId.map(_.map(id => connects.copy(id = id)))
    }

    def delete(id: AtomId): Future[Boolean] = {
      val q = quote { query[Connects].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object contains {
    def apply(childId: AtomId, parentId: AtomId): Future[Option[Contains]] = {
      val contains = Contains(childId, parentId)
      val q = quote {
        query[Contains].insert(lift(contains)).returning(x => x.id)
      }
      val newId = ctx.run(q).map(Some(_)).recover {
        case e: /*GenericDatabaseException*/ Exception => None
      }

      newId.map(_.map(id => contains.copy(id = id)))
    }

    def delete(id: AtomId): Future[Boolean] = {
      val q = quote { query[Contains].filter(_.id == lift(id)).delete }
      ctx.run(q).map(_ => true)
    }
  }

  object user {
    private def passwordDigest(password: String) = Hasher(password).bcrypt

    private val createUserAndPassword = quote { (name: String, digest: Array[Byte]) =>
      infix"""with ins as (
        insert into "user"(id, name) values(DEFAULT, $name) returning id
      ) insert into password(id, digest) select id, $digest from ins returning id;""".as[Insert[Long]]
    }

    def apply(name: String, password: String): Future[Option[User]] = {
      val digest = passwordDigest(password)
      val q = quote(createUserAndPassword(lift(name), lift(digest)))
      ctx.run(q).map(id => Some(User(id, name))).recover { case _: Exception => None }
    }

    def get(id: AtomId): Future[Option[User]] = {
      val q = quote(querySchema[User]("\"user\"").filter(_.id == lift(id)).take(1))
      ctx.run(q).map(_.headOption)
    }

    def get(name: String, password: String): Future[Option[User]] = {
      val q = quote {
        querySchema[User]("\"user\"").filter(_.name == lift(name)).join(query[Password]).on((u, p) => u.id == p.id).take(1)
      }

      ctx.run(q).map(_.headOption.filter {
        case (user, pw) =>
          passwordDigest(password) hash = pw.digest
      }.map(_._1))
    }
  }

  object graph {
    def get(): Future[Graph] = {
      for (
        posts <- ctx.run(query[Post]);
        connects <- ctx.run(query[Connects]);
        contains <- ctx.run(query[Contains])
      ) yield Graph(
        posts.by(_.id),
        connects.by(_.id),
        contains.by(_.id)
      )
    }
  }
}
