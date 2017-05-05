package wust.dbSpec

import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

import wust.ids._

// TODO: Query-Probing: https://github.com/getquill/quill#query-probing
// "Query probing validates queries against the database at compile time, failing the compilation if it is not valid. The query validation does not alter the database state."
class DbSpec extends DbIntegrationTestSpec with MustMatchers {
  val publicGroup = GroupId(1) //TODO: load from config
  def await[T](future: Future[T]) = {
    future.onFailure { case e => println(e.getMessage) }
    Await.result(future, 10.seconds)
  }

  "post" - {
    "post without id" in { db =>
      db.Post("title").id mustEqual PostId(0)
    }

    "connection without id" in { db =>
      db.Connection(1, ConnectionId(2)).id mustEqual ConnectionId(0)
    }

    "containment without id" in { db =>
      db.Containment(1, 2).id mustEqual ContainmentId(0)
    }

    "add" in { db =>
      db.post("t", publicGroup).map {
        case (post, _) =>
          post.title mustEqual "t"
      }
    }

    "get" in { db =>
      db.post("t", publicGroup).flatMap {
        case (post, _) =>
          db.post.get(post.id).map { dbPost =>
            dbPost.isDefined mustEqual true
            dbPost.get.id mustEqual post.id
            dbPost.get.title mustEqual post.title
          }
      }
    }

    "update" in { db =>
      db.post("t", publicGroup).flatMap {
        case (post, _) =>
          db.post.update(post.copy(title = "harals")).flatMap { _ =>
            db.post.get(post.id).map { dbPost =>
              dbPost.isDefined mustEqual true
              dbPost.get.id mustEqual post.id
              dbPost.get.title mustEqual "harals"
            }
          }
      }
    }

    "delete" in { db =>
      db.post("t", publicGroup).flatMap {
        case (post, _) =>
          db.post.delete(post.id).flatMap { _ =>
            db.post.get(post.id).map { dbPost =>
              dbPost.isDefined mustEqual false
            }
          }
      }
    }
  }

  //TODO: check if actually in db
  "connection" - {

    "newPost" in { db =>
      val (post, _) = await(db.post("t", publicGroup))
      db.connection.newPost("nu", post.id, publicGroup).map {
        case (newPost, connection, _) =>
          newPost.title mustEqual "nu"
          connection.sourceId mustEqual newPost.id
          connection.targetId mustEqual post.id
      }
    }

    "add" in { db =>
      val (post, _) = await(db.post("t", publicGroup))
      db.post("nu", publicGroup).flatMap {
        case (newPost, _) =>
          db.connection(newPost.id, post.id).map { connection =>
            connection.sourceId mustEqual newPost.id
            connection.targetId mustEqual post.id
          }
      }
    }

    "add hyper" in { db =>
      val (post, _) = await(db.post("t", publicGroup))
      db.connection.newPost("nu", post.id, publicGroup).flatMap {
        case (newPost, connection, _) =>
          db.connection(newPost.id, connection.id).map { connection2 =>
            connection2.sourceId mustEqual newPost.id
            connection2.targetId mustEqual connection.id
          }
      }
    }

    "delete" in { db =>
      val (post, _) = await(db.post("t", publicGroup))
      db.connection.newPost("nu", post.id, publicGroup).flatMap {
        case (_, connection, _) =>
          db.connection.delete(connection.id).map { success =>
            success mustEqual true
          }
      }
    }
  }

  //TODO: check if actually in db
  "containment" - {

    "add" in { db =>
      val (post, _) = await(db.post("t", publicGroup))
      db.post("nu", publicGroup).flatMap {
        case (newPost, _) =>
          db.containment(newPost.id, post.id).map { containment =>
            containment.parentId mustEqual newPost.id
            containment.childId mustEqual post.id
          }
      }
    }

    "delete" in { db =>
      val (post, _) = await(db.post("t", publicGroup))
      db.post("nu", publicGroup).flatMap {
        case (newPost, _) =>
          db.containment(newPost.id, post.id).flatMap { containment =>
            db.containment.delete(containment.id).map { success =>
              success mustEqual true
            }
          }
      }
    }
  }

  "user" - {
    "hasAccessToPost" - {
      "post in pubic group" in { db =>
        val Some(user) = await(db.user("u", "123456"))
        val (post, _) = await(db.post("p", publicGroup))
        db.user.hasAccessToPost(user.id, post.id).map(_ must be(true))
      }

      "post in private group (user not member)" in { db =>
        val Some(user) = await(db.user("u2", "123456"))
        val Some(user2) = await(db.user("other", "123456"))
        val (group, _) = await(db.user.createGroupForUser(user2.id))
        val post = await(db.post.createOwnedPost("p", group.id))
        db.user.hasAccessToPost(user.id, post.id).map(_ must be(false))
      }

      "post in private group (user is member)" in { db =>
        val Some(user) = await(db.user("u3", "123456"))
        val (group, _) = await(db.user.createGroupForUser(user.id))
        val post = await(db.post.createOwnedPost("p", group.id))
        db.user.hasAccessToPost(user.id, post.id).map(_ must be(true))
      }
    }
  }

  //TODO: user
}
