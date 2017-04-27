package wust

import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import wust.ids._

class DbSpec extends AsyncFreeSpec with MustMatchers {
  val publicGroup = GroupId(1) //TODO: load from config
  def await[T](future: Future[T]) = Await.result(future, 10.seconds)
  "post" - {
    "post without id" in {
      db.Post("title").id mustEqual PostId(0)
    }

    "connects without id" in {
      db.Connects(1, ConnectsId(2)).id mustEqual ConnectsId(0)
    }

    "contains without id" in {
      db.Contains(1, 2).id mustEqual ContainsId(0)
    }

    "add" in db.post("t", publicGroup).map {
      case (post, _) =>
        post.title mustEqual "t"
    }

    "get" in db.post("t", publicGroup).flatMap {
      case (post, _) =>
        db.post.get(post.id).map { dbPost =>
          dbPost.isDefined mustEqual true
          dbPost.get.id mustEqual post.id
          dbPost.get.title mustEqual post.title
        }
    }

    "update" in db.post("t", publicGroup).flatMap {
      case (post, _) =>
        db.post.update(post.copy(title = "harals")).flatMap { _ =>
          db.post.get(post.id).map { dbPost =>
            dbPost.isDefined mustEqual true
            dbPost.get.id mustEqual post.id
            dbPost.get.title mustEqual "harals"
          }
        }
    }

    "delete" in db.post("t", publicGroup).flatMap {
      case (post, _) =>
        db.post.delete(post.id).flatMap { _ =>
          db.post.get(post.id).map { dbPost =>
            dbPost.isDefined mustEqual false
          }
        }
    }
  }

  //TODO: check if actually in db
  "connects" - {
    val (post, _) = await(db.post("t", publicGroup))

    "newPost" in db.connects.newPost("nu", post.id, publicGroup).map {
      case (newPost, connects, _) =>
        newPost.title mustEqual "nu"
        connects.sourceId mustEqual newPost.id
        connects.targetId mustEqual post.id
    }

    "add" in db.post("nu", publicGroup).flatMap {
      case (newPost, _) =>
        db.connects(newPost.id, post.id).map { connects =>
          connects.sourceId mustEqual newPost.id
          connects.targetId mustEqual post.id
        }
    }

    "add hyper" in db.connects.newPost("nu", post.id, publicGroup).flatMap {
      case (newPost, connects, _) =>
        db.connects(newPost.id, connects.id).map { connects2 =>
          connects2.sourceId mustEqual newPost.id
          connects2.targetId mustEqual connects.id
        }
    }

    "delete" in db.connects.newPost("nu", post.id, publicGroup).flatMap {
      case (_, connects, _) =>
        db.connects.delete(connects.id).map { success =>
          success mustEqual true
        }
    }
  }

  //TODO: check if actually in db
  "contains" - {
    val (post, _) = await(db.post("t", publicGroup))

    "add" in db.post("nu", publicGroup).flatMap {
      case (newPost, _) =>
        db.contains(newPost.id, post.id).map { contains =>
          contains.parentId mustEqual newPost.id
          contains.childId mustEqual post.id
        }
    }

    "delete" in db.post("nu", publicGroup).flatMap {
      case (newPost, _) =>
        db.contains(newPost.id, post.id).flatMap { contains =>
          db.contains.delete(contains.id).map { success =>
            success mustEqual true
          }
        }
    }
  }

  "user" - {
    "hasAccessToPost" - {
      import db.user.hasAccessToPost
      "post in pubic group" in {
        val Some(user) = await(db.user("u", "123456"))
        val (post, _) = await(db.post("p", publicGroup))
        hasAccessToPost(user.id, post.id).map(_ must be(true))
      }

      "post in private group (user not member)" in {
        val Some(user) = await(db.user("u2", "123456"))
        val Some(user2) = await(db.user("other", "123456"))
        val (group, m) = await(db.user.createUserGroupForUser(user2.id))
        val post = await(db.post.createOwnedPost("p", group.id))
        hasAccessToPost(user.id, post.id).map(_ must be(false))
      }

      "post in private group (user is member)" in {
        val Some(user) = await(db.user("u3", "123456"))
        val (group, _) = await(db.user.createUserGroupForUser(user.id))
        val post = await(db.post.createOwnedPost("p", group.id))
        hasAccessToPost(user.id, post.id).map(_ must be(true))
      }
    }
  }

  //TODO: user
}
