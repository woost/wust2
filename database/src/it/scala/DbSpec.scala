package wust.dbSpec

import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

import wust.ids._
import wust.db._

// TODO: Query-Probing: https://github.com/getquill/quill#query-probing
// "Query probing validates queries against the database at compile time, failing the compilation if it is not valid. The query validation does not alter the database state."
class DbSpec extends DbIntegrationTestSpec with MustMatchers {
  def await[T](future: Future[T]) = {
    future.onFailure { case e => println(e.getMessage) }
    Await.result(future, 10.seconds)
  }

  "post" - {
    "create public post" in { db =>
      import db._, db.ctx, ctx._
      for {
        post <- db.post.createPublic("t")

        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
        queriedOwnerships <- ctx.run(query[Ownership].filter(_.postId == lift(post.id)))
      } yield {
        post.title mustEqual "t"
        queriedPosts.head.title mustEqual "t"
        queriedOwnerships mustBe empty
      }
    }

    "create public post with apply" in { db =>
      import db._, db.ctx, ctx._
      for {
        (post, None) <- db.post("t", groupIdOpt = None)

        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
        queriedOwnerships <- ctx.run(query[Ownership].filter(_.postId == lift(post.id)))
      } yield {
        post.title mustEqual "t"
        queriedPosts.head.title mustEqual "t"
        queriedOwnerships mustBe empty
      }
    }

    "create owned post" in { db =>
      import db._, db.ctx, ctx._
      for {
        // groupId <- ctx.run(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
        (post, ownership) <- db.post.createOwned("t", groupId)

        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
        queriedOwnerships <- ctx.run(query[Ownership].filter(_.postId == lift(post.id)))
      } yield {
        post.title mustEqual "t"
        ownership mustEqual Ownership(post.id, groupId)

        queriedPosts.head.title mustEqual "t"
        queriedOwnerships.head mustEqual Ownership(post.id, groupId)
      }
    }

    "create owned post with apply" in { db =>
      import db._, db.ctx, ctx._
      for {
        // groupId <- ctx.run(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
        (post, Some(ownership)) <- post("t", Option(groupId))

        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
        queriedOwnerships <- ctx.run(query[Ownership].filter(_.postId == lift(post.id)))
      } yield {
        post.title mustEqual "t"
        ownership mustEqual Ownership(post.id, groupId)

        queriedPosts.head.title mustEqual "t"
        queriedOwnerships.head mustEqual Ownership(post.id, groupId)
      }
    }

    "get existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        Post(postId, _) <- db.post.createPublic("t")
        getPost <- db.post.get(postId)
      } yield {
        getPost mustEqual Option(Post(postId, "t"))
      }
    }

    "get non-existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        getPost <- db.post.get(17134)
      } yield {
        getPost mustEqual None
      }
    }

    "update existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        post <- db.post.createPublic("t")
        updatedPost <- db.post.update(post.copy(title = "harals"))
        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
      } yield {
        updatedPost mustBe true
        queriedPosts.head mustEqual post.copy(title = "harals")
      }
    }

    "update non-existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        updatedPost <- db.post.update(Post(1135, "harals"))
        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(PostId(1135))))
      } yield {
        updatedPost mustBe false
        queriedPosts mustBe empty
      }
    }

    "delete existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        post <- db.post.createPublic("t")
        deleted <- db.post.delete(post.id)
        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
      } yield {
        deleted mustBe true
        queriedPosts mustBe empty
      }
    }

    "delete non-existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        deleted <- db.post.delete(135481)
        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(PostId(135481))))
      } yield {
        deleted mustBe false
        queriedPosts mustBe empty
      }
    }
  }

  "connection" - {
    "create between two existing posts" in { db =>
      import db._, db.ctx, ctx._
      for {
        sourcePost <- db.post.createPublic("s")
        targetPost <- db.post.createPublic("t")
        Some(connection) <- db.connection(sourcePost.id, targetPost.id)
        //TODO: queryConnection
      } yield {
        connection.sourceId mustEqual sourcePost.id
        connection.targetId mustEqual targetPost.id
      }
    }

    "create between two posts, source not existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        targetPost <- db.post.createPublic("t")
        connectionOpt <- db.connection(131565, targetPost.id)
      } yield {
        connectionOpt mustEqual None
      }
    }

    "create between two posts, target not existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        sourcePost <- db.post.createPublic("s")
        connectionOpt <- db.connection(sourcePost.id, PostId(131565))
      } yield {
        connectionOpt mustEqual None
      }
    }

    "create between two posts, both not existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        connectionOpt <- db.connection(16816, PostId(131565))
      } yield {
        connectionOpt mustEqual None
      }
    }

    "create from post to other connection" in { db =>
      import db._, db.ctx, ctx._
      for {
        sourcePost <- db.post.createPublic("s")
        aPost <- db.post.createPublic("a")
        bPost <- db.post.createPublic("b")
        Some(targetConnection) <- db.connection(aPost.id, bPost.id)

        Some(connection) <- db.connection(sourcePost.id, targetConnection.id)
        //TODO: queryConnection
      } yield {
        connection.sourceId mustEqual sourcePost.id
        connection.targetId mustEqual targetConnection.id
      }
    }

    "create connection with new public post" in { db =>
      import db._, db.ctx, ctx._
      for {
        targetPost <- db.post.createPublic("t")

        Some((post, connection, None)) <- db.connection.newPost("response", targetPost.id, groupIdOpt = None)

        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
        queriedConnections <- ctx.run(query[Connection].filter(_.id == lift(connection.id)))
        queriedOwnerships <- ctx.run(query[Ownership].filter(_.postId == lift(post.id)))
      } yield {
        post.title mustEqual "response"
        connection.targetId mustEqual targetPost.id
        connection.sourceId mustEqual post.id

        queriedPosts.head mustEqual post
        queriedConnections.head.id mustEqual connection.id
        queriedConnections.head.sourceId mustEqual connection.sourceId
        queriedConnections.head.targetId.id mustEqual connection.targetId.id
        queriedOwnerships mustBe empty
      }
    }

    "create connection with new owned post" in { db =>
      import db._, db.ctx, ctx._
      for {
        targetPost <- db.post.createPublic("t")
        groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))

        Some((post, connection, Some(ownership))) <- db.connection.newPost("response", targetPost.id, groupIdOpt = Option(groupId))

        queriedPosts <- ctx.run(query[Post].filter(_.id == lift(post.id)))
        queriedConnections <- ctx.run(query[Connection].filter(_.id == lift(connection.id)))
        queriedOwnerships <- ctx.run(query[Ownership].filter(_.postId == lift(post.id)))
      } yield {
        post.title mustEqual "response"
        connection.targetId mustEqual targetPost.id
        connection.sourceId mustEqual post.id

        queriedPosts.head mustEqual post
        queriedConnections.head.id mustEqual connection.id
        queriedConnections.head.sourceId mustEqual connection.sourceId
        queriedConnections.head.targetId.id mustEqual connection.targetId.id
        queriedOwnerships.head mustEqual Ownership(post.id, groupId)
      }
    }

    "create connection with new public post to non-existing targetId" in { db =>
      import db._, db.ctx, ctx._
      for {
        connectionResultOpt <- db.connection.newPost("response", PostId(612345), groupIdOpt = None)

        queriedPosts <- ctx.run(query[Post].filter(_.title == lift("response")))
      } yield {
        connectionResultOpt mustEqual None
        queriedPosts mustBe empty
      }
    }

    "delete existing connection" in { db =>
      import db._, db.ctx, ctx._
      for {
        sourcePost <- db.post.createPublic("s")
        targetPost <- db.post.createPublic("t")
        Some(connection) <- db.connection(sourcePost.id, targetPost.id)

        deleted <- db.connection.delete(connection.id)
      } yield {
        deleted mustEqual true
      }
    }

    "delete non-existing connection" in { db =>
      import db._, db.ctx, ctx._
      for {
        deleted <- db.connection.delete(165151)
      } yield {
        deleted mustEqual false
      }
    }
  }

  "containment" - {
    "create between two existing posts" in { db =>
      import db._, db.ctx, ctx._
      for {
        parentPost <- db.post.createPublic("s")
        childPost <- db.post.createPublic("t")
        Some(containment) <- db.containment(parentPost.id, childPost.id)
        //TODO: queryContainment
      } yield {
        containment.parentId mustEqual parentPost.id
        containment.childId mustEqual childPost.id
      }
    }

    "create between two posts, parent not existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        childPost <- db.post.createPublic("t")
        containmentOpt <- db.containment(131565, childPost.id)
      } yield {
        containmentOpt mustEqual None
      }
    }

    "create between two posts, child not existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        parentPost <- db.post.createPublic("s")
        containmentOpt <- db.containment(parentPost.id, PostId(131565))
      } yield {
        containmentOpt mustEqual None
      }
    }

    "create between two posts, both not existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        containmentOpt <- db.containment(16816, PostId(131565))
      } yield {
        containmentOpt mustEqual None
      }
    }

    "delete existing containment" in { db =>
      import db._, db.ctx, ctx._
      for {
        parentPost <- db.post.createPublic("s")
        childPost <- db.post.createPublic("t")
        Some(containment) <- db.containment(parentPost.id, childPost.id)

        deleted <- db.containment.delete(containment.id)
      } yield {
        deleted mustEqual true
      }
    }

    "delete non-existing containment" in { db =>
      import db._, db.ctx, ctx._
      for {
        deleted <- db.containment.delete(165151)
      } yield {
        deleted mustEqual false
      }
    }
  }

  "user" - {
    import com.roundeights.hasher.Hasher

    "create non-existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(user) <- db.user("heigo", "parwin")
        queriedUsers <- ctx.run(query[User].filter(_.id == lift(user.id)))
        queriedPasswords <- ctx.run(query[Password].filter(_.id == lift(user.id)))
        queriedGroups <- ctx.run(query[UserGroup])
      } yield {
        user.name mustEqual "heigo"
        user.isImplicit mustEqual false
        user.revision mustEqual db.user.initialRevision
        queriedUsers.head mustEqual user
        (Hasher("parwin").bcrypt.hash = queriedPasswords.head.digest) mustEqual true
        queriedGroups mustBe empty
      }
    }

    "try to create existing with same password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        None <- db.user("heigo", "parwin")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        queriedUsers mustEqual List(existingUser)
        (Hasher("parwin").bcrypt.hash = queriedPasswords.head.digest) mustEqual true
      }
    }

    "try to create existing with different password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        None <- db.user("heigo", "reidon")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        queriedUsers mustEqual List(existingUser)
        (Hasher("parwin").bcrypt.hash = queriedPasswords.head.digest) mustEqual true
      }
    }

    "create implicit user" in { db =>
      import db._, db.ctx, ctx._
      for {
        user <- db.user.createImplicitUser()
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        user.name must startWith("anon-")
        user.isImplicit mustEqual true
        user.revision mustEqual db.user.initialRevision
        queriedUsers.head mustEqual user
        queriedPasswords mustBe empty
      }
    }

    "create two implicit users" in { db =>
      import db._, db.ctx, ctx._
      for {
        user1 <- db.user.createImplicitUser()
        user2 <- db.user.createImplicitUser()
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        user1.name must not equal (user2.name)
        queriedUsers.toSet mustEqual Set(user1, user2)
        queriedPasswords mustBe empty
      }
    }

    "activate implicit user to non-existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        implUser <- db.user.createImplicitUser()
        Some(user) <- db.user.activateImplicitUser(implUser.id, "ganiz", "faura")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        user.name mustEqual "ganiz"
        user.isImplicit mustEqual false
        user.revision mustEqual (db.user.initialRevision + 1)
        queriedUsers mustEqual List(user)
        (Hasher("faura").bcrypt.hash = queriedPasswords.head.digest) mustEqual true
      }
    }

    "try to activate implicit user to existing with same password" in { db =>
      import db._, db.ctx, ctx._
      for {
        implUser <- db.user.createImplicitUser()
        Some(existingUser) <- db.user("ganiz", "heuriso")
        None <- db.user.activateImplicitUser(implUser.id, "ganiz", "heuriso")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        queriedUsers must contain theSameElementsAs List(existingUser, implUser)
        queriedPasswords.size mustEqual 1
        (Hasher("heuriso").bcrypt.hash = queriedPasswords.head.digest) mustEqual true
      }
    }

    "try to activate implicit user to existing with different password" in { db =>
      import db._, db.ctx, ctx._
      for {
        implUser <- db.user.createImplicitUser()
        Some(existingUser) <- db.user("ganiz", "heuriso")
        None <- db.user.activateImplicitUser(implUser.id, "ganiz", "faura")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        queriedUsers must contain theSameElementsAs List(existingUser, implUser)
        queriedPasswords.size mustEqual 1
        (Hasher("heuriso").bcrypt.hash = queriedPasswords.head.digest) mustEqual true
      }
    }

    "get existing by id" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        Some(user) <- db.user.get(existingUser.id)
      } yield {
        user mustEqual existingUser
      }
    }

    "get non-existing by id" in { db =>
      import db._, db.ctx, ctx._
      for {
        userOpt <- db.user.get(UserId(11351))
      } yield {
        userOpt mustEqual None
      }
    }

    "get existing by name,password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        Some(user) <- db.user.get("heigo", "parwin")
      } yield {
        user mustEqual existingUser
      }
    }

    "get non-existing by name,password" in { db =>
      import db._, db.ctx, ctx._
      for {
        userOpt <- db.user.get("a", "b")
      } yield {
        userOpt mustEqual None
      }
    }

    "get existing with wrong password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        userOpt <- db.user.get("heigo", "brutula")
      } yield {
        userOpt mustEqual None
      }
    }

    "get existing with wrong username" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        userOpt <- db.user.get("ürgens", "parwin")
      } yield {
        userOpt mustEqual None
      }
    }

    "check if existing user exists" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        exists <- db.user.checkIfEqualUserExists(existingUser)
      } yield {
        exists mustBe true
      }
    }

    "check if existing user exists (wrong id)" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        exists <- db.user.checkIfEqualUserExists(existingUser.copy(id = UserId(187)))
      } yield {
        exists mustBe false
      }
    }

    "check if existing user exists (wrong name)" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        exists <- db.user.checkIfEqualUserExists(existingUser.copy(name = "heikola"))
      } yield {
        exists mustBe false
      }
    }

    "check if existing user exists (wrong isImplicit)" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        exists <- db.user.checkIfEqualUserExists(existingUser.copy(isImplicit = true))
      } yield {
        exists mustBe false
      }
    }

    "check if existing user exists (wrong revision)" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        exists <- db.user.checkIfEqualUserExists(existingUser.copy(revision = 3))
      } yield {
        exists mustBe false
      }
    }
  }

  "group" - {
    "hasAccessToPost" - {
      "post in pubic group" in { db =>
        val Some(user) = await(db.user("u", "123456"))
        val (post, _) = await(db.post("p", groupIdOpt = None))
        db.group.hasAccessToPost(user.id, post.id).map(_ must be(true))
      }

      "post in private group (user not member)" in { db =>
        val Some(user) = await(db.user("u2", "123456"))
        val Some(user2) = await(db.user("other", "123456"))
        val (group, _) = await(db.group.createForUser(user2.id))
        val (post, _) = await(db.post.createOwned("p", group.id))
        db.group.hasAccessToPost(user.id, post.id).map(_ must be(false))
      }

      "post in private group (user is member)" in { db =>
        val Some(user) = await(db.user("u3", "123456"))
        val (group, _) = await(db.group.createForUser(user.id))
        val (post, _) = await(db.post.createOwned("p", group.id))
        db.group.hasAccessToPost(user.id, post.id).map(_ must be(true))
      }
    }
  }

  "graph" - {
    "without user" - {
      "public posts, connections and containments" in { db =>
        import db._, db.ctx, ctx._
        for {
          postA <- db.post.createPublic("A")
          postB <- db.post.createPublic("B")
          postC <- db.post.createPublic("C")
          Some(conn) <- db.connection(postA.id, postB.id)
          Some(cont) <- db.containment(postB.id, postC.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(None)
        } yield {
          posts must contain theSameElementsAs List(postA, postB, postC)
          connections.map(c => c.copy(targetId = PostId(c.targetId.id))) must contain theSameElementsAs List(conn)
          containments must contain theSameElementsAs List(cont)
          userGroups mustBe empty
          ownerships mustBe empty
          users mustBe empty
          memberships mustBe empty
        }
      }

      "public posts, connections and containments, private posts" in { db =>
        import db._, db.ctx, ctx._
        for {
          Some(user) <- db.user("heigo", "parwin")
          (group, membership) <- db.group.createForUser(user.id)

          postA <- db.post.createPublic("A")
          (postB, ownershipB) <- db.post.createOwned("B", group.id)
          postC <- db.post.createPublic("C")
          Some(conn) <- db.connection(postA.id, postB.id)
          Some(cont) <- db.containment(postB.id, postC.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(None)
        } yield {
          posts must contain theSameElementsAs List(postA, postC)
          connections.map(c => c.copy(targetId = PostId(c.targetId.id))) must contain theSameElementsAs List()
          containments must contain theSameElementsAs List()
          userGroups mustBe empty
          ownerships mustBe empty
          users mustBe empty
          memberships mustBe empty
        }
      }
    }

    "with user" - {
      "public posts, connections and containments" in { db =>
        import db._, db.ctx, ctx._
        for {
          Some(user) <- db.user("heigo", "parwin")
          postA <- db.post.createPublic("A")
          postB <- db.post.createPublic("B")
          postC <- db.post.createPublic("C")
          Some(conn) <- db.connection(postA.id, postB.id)
          Some(cont) <- db.containment(postB.id, postC.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List(postA, postB, postC)
          connections.map(c => c.copy(targetId = PostId(c.targetId.id))) must contain theSameElementsAs List(conn)
          containments must contain theSameElementsAs List(cont)
          userGroups mustBe empty
          ownerships mustBe empty
          users must contain theSameElementsAs List(user)
          memberships mustBe empty
        }
      }

      "group without posts" in { db =>
        import db._, db.ctx, ctx._
        for {
          Some(user) <- db.user("heigo", "parwin")
          (group, membership) <- db.group.createForUser(user.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List()
          connections.map(c => c.copy(targetId = PostId(c.targetId.id))) must contain theSameElementsAs List()
          containments must contain theSameElementsAs List()
          userGroups mustEqual List(group)
          ownerships mustBe empty
          users must contain theSameElementsAs List(user)
          memberships must contain theSameElementsAs memberships
        }
      }

      "public posts, own private posts" in { db =>
        import db._, db.ctx, ctx._
        for {
          Some(user) <- db.user("heigo", "parwin")
          (group, membership) <- db.group.createForUser(user.id)

          postA <- db.post.createPublic("A")
          (postB, ownershipB) <- db.post.createOwned("B", group.id)
          postC <- db.post.createPublic("C")
          Some(conn) <- db.connection(postA.id, postB.id)
          Some(cont) <- db.containment(postB.id, postC.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List(postA, postB, postC)
          connections.map(c => c.copy(targetId = PostId(c.targetId.id))) must contain theSameElementsAs List(conn)
          containments must contain theSameElementsAs List(cont)
          userGroups must contain theSameElementsAs List(group)
          ownerships must contain theSameElementsAs List(ownershipB)
          users must contain theSameElementsAs List(user)
          memberships must contain theSameElementsAs List(membership)
        }
      }

      "public posts, own private posts, invisible posts" in { db =>
        import db._, db.ctx, ctx._
        for {
          Some(user) <- db.user("heigo", "parwin")
          (group, membership) <- db.group.createForUser(user.id)

          Some(otherUser) <- db.user("gurkulo", "meisin")
          (otherGroup, otherMembership) <- db.group.createForUser(otherUser.id)

          postA <- db.post.createPublic("A")
          (postB, ownershipB) <- db.post.createOwned("B", group.id)
          (postC, ownershipC) <- db.post.createOwned("C", otherGroup.id)

          Some(conn) <- db.connection(postA.id, postB.id)
          Some(cont) <- db.containment(postB.id, postC.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List(postA, postB)
          connections.map(c => c.copy(targetId = PostId(c.targetId.id))) must contain theSameElementsAs List(conn)
          containments must contain theSameElementsAs List()
          userGroups must contain theSameElementsAs List(group)
          ownerships must contain theSameElementsAs List(ownershipB)
          users must contain theSameElementsAs List(user)
          memberships must contain theSameElementsAs List(membership)
        }
      }
    }
  }
}
