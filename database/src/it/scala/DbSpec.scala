package wust.db

import org.scalatest._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

import wust.db.data._
import wust.ids._

// TODO: Query-Probing: https://github.com/getquill/quill#query-probing
// "Query probing validates queries against the database at compile time, failing the compilation if it is not valid. The query validation does not alter the database state."
class DbSpec extends DbIntegrationTestSpec with MustMatchers {
  implicit def passwordToDigest(pw: String): Array[Byte] = pw.map(_.toByte).toArray
  implicit class EqualityByteArray(val arr: Array[Byte]) {
    def mustEqualDigest(pw: String) = arr mustEqual passwordToDigest(pw)
  }

  //TODO: still throw exceptions on for example database connection errors

  "post" - {
    "create public post" in { db =>
      import db._, db.ctx, ctx._
      val post = Post("ei-D", "dono")
      for {
        success <- db.post.createPublic(post)

        queriedPosts <- ctx.run(query[Post])
        queriedOwnerships <- ctx.run(query[Ownership])
      } yield {
        success mustBe true
        queriedPosts must contain theSameElementsAs List(post)
        queriedOwnerships mustBe empty
      }
    }

    //TODO: see db.scala
    // "create public post (existing id)" in { db =>
    //   import db._, db.ctx, ctx._
    //   val post = Post("ei-D", "dono")
    //   for {
    //     _ <- db.post.createPublic(post)
    //     success <- db.post.createPublic(Post("ei-D", "dino"))

    //     queriedPosts <- ctx.run(query[Post])
    //     queriedOwnerships <- ctx.run(query[Ownership])
    //   } yield {
    //     success mustBe false
    //     queriedPosts must contain theSameElementsAs List(post)
    //     queriedOwnerships mustBe empty
    //   }
    // }

    "create owned post" in { db =>
      import db._, db.ctx, ctx._
      val post = Post("woink", "klang")
      for {
        // groupId <- ctx.run(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
        success <- db.post.createOwned(post, groupId)

        queriedPosts <- ctx.run(query[Post])
        queriedOwnerships <- ctx.run(query[Ownership])
      } yield {
        success mustBe true

        queriedPosts must contain theSameElementsAs List(post)
        queriedOwnerships must contain theSameElementsAs List(Ownership(post.id, groupId))
      }
    }

    "create owned post (existing)" in { db =>
      import db._, db.ctx, ctx._
      val post = Post("woink", "klang")
      for {
        _ <- db.post.createPublic(post)
        // groupId <- ctx.run(query[UserGroup].insert(lift(UserGroup())).returning(_.id))
        groupId <- ctx.run(infix"insert into usergroup(id) values(DEFAULT)".as[Insert[UserGroup]].returning(_.id))
        success <- db.post.createOwned(post, groupId)

        queriedPosts <- ctx.run(query[Post])
        queriedOwnerships <- ctx.run(query[Ownership])
      } yield {
        success mustBe false

        queriedPosts must contain theSameElementsAs List(post)
        queriedOwnerships mustBe empty
      }
    }

    "get existing post" in { db =>
      import db._, db.ctx, ctx._
      val post = Post("hege", "walt")
      for {
        true <- db.post.createPublic(post)
        getPost <- db.post.get(post.id)
      } yield {
        getPost mustEqual Option(post)
      }
    }

    "get non-existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        getPost <- db.post.get("17134")
      } yield {
        getPost mustEqual None
      }
    }

    "update existing post" in { db =>
      import db._, db.ctx, ctx._
      val post = Post("harnig", "delauf")
      for {
        true <- db.post.createPublic(post)
        success <- db.post.update(post.copy(title = "harals"))
        queriedPosts <- ctx.run(query[Post])
      } yield {
        success mustBe true
        queriedPosts must contain theSameElementsAs List(post.copy(title = "harals"))
      }
    }

    "update non-existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        success <- db.post.update(Post("1135", "harals"))
        queriedPosts <- ctx.run(query[Post])
      } yield {
        success mustBe false
        queriedPosts mustBe empty
      }
    }

    "delete existing post" in { db =>
      import db._, db.ctx, ctx._
      val post = Post("harnig", "delauf")
      for {
        true <- db.post.createPublic(post)
        success <- db.post.delete(post.id)
        queriedPosts <- ctx.run(query[Post])
      } yield {
        success mustBe true
        queriedPosts mustBe empty
      }
    }

    "delete non-existing post" in { db =>
      import db._, db.ctx, ctx._
      for {
        success <- db.post.delete("135481")
        queriedPosts <- ctx.run(query[Post])
      } yield {
        success mustBe true
        queriedPosts mustBe empty
      }
    }
  }

  "connection" - {
    "create between two existing posts" in { db =>
      import db._, db.ctx, ctx._
      val sourcePost = Post("t", "yo")
      val targetPost = Post("r", "yo")
      val connection = Connection("t", "r")
      for {
        true <- db.post.createPublic(sourcePost)
        true <- db.post.createPublic(targetPost)
        success <- db.connection(connection)
        connections <- ctx.run(query[Connection])
      } yield {
        success mustBe true
        connections must contain theSameElementsAs List(connection)
      }
    }

    "create between two existing posts with already existing connection" in { db =>
      import db._, db.ctx, ctx._
      val sourcePost = Post("t", "yo")
      val targetPost = Post("r", "yo")
      val connection = Connection("t", "r")
      for {
        true <- db.post.createPublic(sourcePost)
        true <- db.post.createPublic(targetPost)
        true <- db.connection(connection)
        success <- db.connection(connection)
        connections <- ctx.run(query[Connection])
      } yield {
        success mustBe true
        connections must contain theSameElementsAs List(connection)
      }
    }

    "create between two existing posts with already existing containments" in { db =>
      import db._, db.ctx, ctx._
      val sourcePost = Post("t", "yo")
      val targetPost = Post("r", "yo")
      val connection = Connection("t", "r")
      for {
        true <- db.post.createPublic(sourcePost)
        true <- db.post.createPublic(targetPost)
        true <- db.containment(Containment(sourcePost.id, targetPost.id))
        true <- db.containment(Containment(targetPost.id, sourcePost.id))
        success <- db.connection(connection)
        connections <- ctx.run(query[Connection])
      } yield {
        success mustBe true
        connections must contain theSameElementsAs List(connection)
      }
    }

    "create between two posts, source not existing" in { db =>
      import db._, db.ctx, ctx._
      val targetPost = Post("r", "yo")
      val connection = Connection("t", "r")
      for {
        true <- db.post.createPublic(targetPost)
        success <- db.connection(connection)
        connections <- ctx.run(query[Connection])
      } yield {
        success mustBe false
        connections mustBe empty
      }
    }

    "create between two posts, target not existing" in { db =>
      import db._, db.ctx, ctx._
      val sourcePost = Post("r", "yo")
      val connection = Connection("r", "t")
      for {
        true <- db.post.createPublic(sourcePost)
        success <- db.connection(connection)
        connections <- ctx.run(query[Connection])
      } yield {
        success mustBe false
        connections mustBe empty
      }
    }

    "create between two posts, both not existing" in { db =>
      import db._, db.ctx, ctx._
      val connection = Connection("r", "t")
      for {
        success <- db.connection(connection)
        connections <- ctx.run(query[Connection])
      } yield {
        success mustBe false
        connections mustBe empty
      }
    }

    "delete existing connection" in { db =>
      import db._, db.ctx, ctx._
      val sourcePost = Post("t", "yo")
      val targetPost = Post("r", "yo")
      val connection = Connection("t", "r")
      for {
        true <- db.post.createPublic(sourcePost)
        true <- db.post.createPublic(targetPost)
        true <- db.connection(connection)

        deleted <- db.connection.delete(connection)
        queriedConnections <- ctx.run(query[Connection])
      } yield {
        deleted mustEqual true
        queriedConnections mustBe empty
      }
    }

    "delete non-existing connection" in { db =>
      import db._, db.ctx, ctx._
      val connection = Connection("t", "r")
      for {
        deleted <- db.connection.delete(connection)
        queriedConnections <- ctx.run(query[Connection])
      } yield {
        deleted mustEqual true
        queriedConnections mustBe empty
      }
    }
  }

  "containment" - {
    "create between two existing posts" in { db =>
      import db._, db.ctx, ctx._
      val parent = Post("t", "yo")
      val child = Post("r", "yo")
      val containment = Containment("t", "r")
      for {
        true <- db.post.createPublic(parent)
        true <- db.post.createPublic(child)
        success <- db.containment(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustBe true
        containments must contain theSameElementsAs List(containment)
      }
    }

    "create between two existing posts with already existing containment" in { db =>
      import db._, db.ctx, ctx._
      val parent = Post("t", "yo")
      val child = Post("r", "yo")
      val containment = Containment("t", "r")
      for {
        true <- db.post.createPublic(parent)
        true <- db.post.createPublic(child)
        true <- db.containment(containment)
        success <- db.containment(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustBe true
        containments must contain theSameElementsAs List(containment)
      }
    }

    "create between two existing posts with already existing connection" in { db =>
      import db._, db.ctx, ctx._
      val parent = Post("t", "yo")
      val child = Post("r", "yo")
      val containment = Containment("t", "r")
      for {
        true <- db.post.createPublic(parent)
        true <- db.post.createPublic(child)
        true <- db.connection(Connection(parent.id, child.id))
        true <- db.connection(Connection(child.id, parent.id))
        success <- db.containment(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustBe true
        containments must contain theSameElementsAs List(containment)
      }
    }

    "create between two posts, parent not existing" in { db =>
      import db._, db.ctx, ctx._
      val child = Post("r", "yo")
      val containment = Containment("t", "r")
      for {
        true <- db.post.createPublic(child)
        success <- db.containment(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustBe false
        containments mustBe empty
      }
    }

    "create between two posts, child not existing" in { db =>
      import db._, db.ctx, ctx._
      val parent = Post("t", "yo")
      val containment = Containment("t", "r")
      for {
        true <- db.post.createPublic(parent)
        success <- db.containment(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustBe false
        containments mustBe empty
      }
    }

    "create between two posts, both not existing" in { db =>
      import db._, db.ctx, ctx._
      val containment = Containment("t", "r")
      for {
        success <- db.containment(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustBe false
        containments mustBe empty
      }
    }

    "delete existing containment" in { db =>
      import db._, db.ctx, ctx._
      val parent = Post("t", "yo")
      val child = Post("r", "yo")
      val containment = Containment("t", "r")
      for {
        true <- db.post.createPublic(parent)
        true <- db.post.createPublic(child)
        true <- db.containment(containment)

        success <- db.containment.delete(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustEqual true
        containments mustBe empty
      }
    }

    "delete non-existing containment" in { db =>
      import db._, db.ctx, ctx._
      val containment = Containment("t", "r")
      for {
        success <- db.containment.delete(containment)
        containments <- ctx.run(query[Containment])
      } yield {
        success mustEqual true
        containments mustBe empty
      }
    }
  }

  "user" - {
    "create non-existing" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(user) <- db.user("heigo", "parwin")
        Some((queriedUser, queriedDigest)) <- db.user.getUserAndDigest("heigo")
        queriedGroups <- ctx.run(query[UserGroup])
      } yield {
        user.name mustEqual "heigo"
        user.isImplicit mustEqual false
        user.revision mustEqual 0
        queriedUser mustEqual user
        queriedDigest mustEqualDigest "parwin"
        queriedGroups mustBe empty
      }
    }

    "try to create existing with same password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        None <- db.user("heigo", "parwin")
        Some((queriedUser, queriedDigest)) <- db.user.getUserAndDigest("heigo")
      } yield {
        queriedUser mustEqual existingUser
        queriedDigest mustEqualDigest "parwin"
      }
    }

    "try to create existing with different password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        None <- db.user("heigo", "reidon")
        Some((queriedUser, queriedDigest)) <- db.user.getUserAndDigest("heigo")
      } yield {
        queriedUser mustEqual existingUser
        queriedDigest mustEqualDigest "parwin"
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
        user.revision mustEqual 0
        queriedUsers must contain theSameElementsAs List(user)
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
        Some((queriedUser, queriedDigest)) <- db.user.getUserAndDigest("ganiz")
      } yield {
        user.name mustEqual "ganiz"
        user.isImplicit mustEqual false
        user.revision mustEqual 1
        queriedUser mustEqual user
        queriedDigest mustEqualDigest "faura"
      }
    }

    "try to activate implicit user to existing with same password" in { db =>
      import db._, db.ctx, ctx._
      for {
        implUser <- db.user.createImplicitUser()
        Some(existingUser) <- db.user("ganiz", "heuriso")
        None <- db.user.activateImplicitUser(implUser.id, "ganiz", "heuriso")
        Some((queriedUser, queriedDigest)) <- db.user.getUserAndDigest("ganiz")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        queriedUsers must contain theSameElementsAs List(existingUser, implUser)
        queriedPasswords.size mustEqual 1
        queriedUser mustEqual existingUser
        queriedDigest mustEqualDigest "heuriso"
      }
    }

    "try to activate implicit user to existing with different password" in { db =>
      import db._, db.ctx, ctx._
      for {
        implUser <- db.user.createImplicitUser()
        Some(existingUser) <- db.user("ganiz", "heuriso")
        None <- db.user.activateImplicitUser(implUser.id, "ganiz", "faura")
        Some((queriedUser, queriedDigest)) <- db.user.getUserAndDigest("ganiz")
        queriedUsers <- ctx.run(query[User])
        queriedPasswords <- ctx.run(query[Password])
      } yield {
        queriedUsers must contain theSameElementsAs List(existingUser, implUser)
        queriedPasswords.size mustEqual 1
        queriedUser mustEqual existingUser
        queriedDigest mustEqualDigest "heuriso"
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
        userOpt <- db.user.get(11351)
      } yield {
        userOpt mustEqual None
      }
    }

    "get existing by name,password" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        Some((user, digest)) <- db.user.getUserAndDigest("heigo")
      } yield {
        digest mustEqualDigest "parwin"
        user mustEqual existingUser
      }
    }

    "get non-existing by name,password" in { db =>
      import db._, db.ctx, ctx._
      for {
        userOpt <- db.user.getUserAndDigest("a")
      } yield {
        userOpt mustEqual None
      }
    }

    "get existing with wrong username" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(existingUser) <- db.user("heigo", "parwin")
        userOpt <- db.user.getUserAndDigest("ürgens")
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
        exists <- db.user.checkIfEqualUserExists(existingUser.copy(id = 187))
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
    "create group for existing user" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(user) <- db.user("garna", "utria")
        Some((`user`, membership, group)) <- db.group.createForUser(user.id)
        queryGroups <- ctx.run(query[UserGroup])
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        queryGroups must contain theSameElementsAs List(group)
        queryMemberships must contain theSameElementsAs List(membership)

        membership mustEqual Membership(user.id, group.id)
      }
    }

    "create group for non-existing user" in { db =>
      import db._, db.ctx, ctx._
      for {
        resultOpt <- db.group.createForUser(13153)
        queryGroups <- ctx.run(query[UserGroup])
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        resultOpt mustEqual None
        queryGroups mustBe empty
        queryMemberships mustBe empty
      }
    }

    "add existing user to existing group" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(initialUser) <- db.user("garna", "utria")
        Some((_, _, group)) <- db.group.createForUser(initialUser.id)
        Some(user) <- db.user("furo", "garnaki")

        Some((_, membership, _)) <- db.group.addMember(group.id, user.id)
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        membership mustEqual Membership(user.id, group.id)
        queryMemberships must contain theSameElementsAs List(Membership(initialUser.id, group.id), membership)
      }
    }

    "add existing user to existing group (is already member)" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(initialUser) <- db.user("garna", "utria")
        Some((_, _, group)) <- db.group.createForUser(initialUser.id)
        Some(user) <- db.user("furo", "garnaki")

        Some((_, _, _)) <- db.group.addMember(group.id, user.id)
        Some((_, membership, _)) <- db.group.addMember(group.id, user.id)
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        membership mustEqual Membership(user.id, group.id)
        queryMemberships must contain theSameElementsAs List(Membership(initialUser.id, group.id), membership)
      }
    }

    "add non-existing user to existing group" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(initialUser) <- db.user("garna", "utria")
        Some((_, _, group)) <- db.group.createForUser(initialUser.id)
        Some(user) <- db.user("furo", "garnaki")

        membershipOpt <- db.group.addMember(group.id, 131551)
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        membershipOpt mustEqual None
        queryMemberships must contain theSameElementsAs List(Membership(initialUser.id, group.id))
      }
    }

    "add existing user to non-existing group" in { db =>
      import db._, db.ctx, ctx._
      for {
        Some(user) <- db.user("garna", "utria")

        membershipOpt <- db.group.addMember(13515, user.id)
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        membershipOpt mustEqual None
        queryMemberships mustBe empty
      }
    }

    "add non-existing user to non-existing group" in { db =>
      import db._, db.ctx, ctx._
      for {
        membershipOpt <- db.group.addMember(13515, 68415)
        queryMemberships <- ctx.run(query[Membership])
      } yield {
        membershipOpt mustEqual None
        queryMemberships mustBe empty
      }
    }

    "hasAccessToPost" - {
      "post in pubic group" in { db =>
        val post = Post("id", "p")
        for {
          Some(user) <- db.user("u", "123456")
          true <- db.post.createPublic(post)
          hasAccess <- db.group.hasAccessToPost(user.id, post.id)
        } yield hasAccess mustBe true
      }

      "post in private group (user not member)" in { db =>
        val post = Post("id", "p")
        for {
          Some(user) <- db.user("u2", "123456")
          Some(user2) <- db.user("other", "123456")
          Some((_, _, group)) <- db.group.createForUser(user2.id)
          true <- db.post.createOwned(post, group.id)
          hasAccess <- db.group.hasAccessToPost(user.id, post.id)
        } yield hasAccess mustBe false
      }

      "post in private group (user is member)" in { db =>
        val post = Post("id", "p")
        for {
          Some(user) <- db.user("u3", "123456")
          Some((_, _, group)) <- db.group.createForUser(user.id)
          true <- db.post.createOwned(post, group.id)
          hasAccess <- db.group.hasAccessToPost(user.id, post.id)
        } yield hasAccess mustBe true
      }
    }
  }

  "graph" - {
    "without user" - {
      "public posts, connections and containments" in { db =>
        import db._, db.ctx, ctx._
        val postA = Post("a", "A")
        val postB = Post("b", "B")
        val postC = Post("c", "C")
        for {
          true <- db.post.createPublic(postA)
          true <- db.post.createPublic(postB)
          true <- db.post.createPublic(postC)
          conn = Connection(postA.id, postB.id)
          cont = Containment(postB.id, postC.id)
          true <- db.connection(conn)
          true <- db.containment(cont)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(None)
        } yield {
          posts must contain theSameElementsAs List(postA, postB, postC)
          connections must contain theSameElementsAs List(conn)
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
          Some((_, membership, group)) <- db.group.createForUser(user.id)

          postA = Post("A", "sehe")
          true <- db.post.createPublic(postA)
          postB = Post("B", "cehen")
          ownershipB = Ownership(postB.id, group.id)
          true <- db.post.createOwned(postB, group.id)
          postC = Post("C", "geh")
          true <- db.post.createPublic(postC)
          conn = Connection(postA.id, postB.id)
          cont = Containment(postB.id, postC.id)
          true <- db.connection(conn)
          true <- db.containment(cont)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(None)
        } yield {
          posts must contain theSameElementsAs List(postA, postC)
          connections must contain theSameElementsAs List()
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
          postA = Post("A", "sei")
          true <- db.post.createPublic(postA)
          postB = Post("B", "rete")
          true <- db.post.createPublic(postB)
          postC = Post("C", "dete")
          true <- db.post.createPublic(postC)
          conn = Connection(postA.id, postB.id)
          cont = Containment(postB.id, postC.id)
          true <- db.connection(conn)
          true <- db.containment(cont)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List(postA, postB, postC)
          connections must contain theSameElementsAs List(conn)
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
          Some((_, membership, group)) <- db.group.createForUser(user.id)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List()
          connections must contain theSameElementsAs List()
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
          Some((_, membership, group)) <- db.group.createForUser(user.id)

          postA = Post("A", "heit")
          true <- db.post.createPublic(postA)

          postB = Post("b", "asd")
          ownershipB = Ownership(postB.id, group.id)

          true <- db.post.createOwned(postB, group.id)
          postC = Post("C", "derlei")
          true <- db.post.createPublic(postC)

          conn = Connection(postA.id, postB.id)
          true <- db.connection(conn)
          cont = Containment(postB.id, postC.id)
          true <- db.containment(cont)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List(postA, postB, postC)
          connections must contain theSameElementsAs List(conn)
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
          Some((_, membership, group)) <- db.group.createForUser(user.id)

          Some(otherUser) <- db.user("gurkulo", "meisin")
          Some((_, otherMembership, otherGroup)) <- db.group.createForUser(otherUser.id)

          postA = Post("hei", "selor")
          true <- db.post.createPublic(postA)

          postB = Post("id", "hasnar")
          ownershipB = Ownership(postB.id, group.id)
          postC = Post("2d", "shon")
          ownershipC = Ownership(postC.id, otherGroup.id)
          true <- db.post.createOwned(postB, group.id)
          true <- db.post.createOwned(postC, otherGroup.id)

          conn = Connection(postA.id, postB.id)
          true <- db.connection(conn)
          cont = Containment(postB.id, postC.id)
          true <- db.containment(cont)

          (posts, connections, containments,
            userGroups, ownerships, users, memberships) <- db.graph.getAllVisiblePosts(Option(user.id))
        } yield {
          posts must contain theSameElementsAs List(postA, postB)
          connections must contain theSameElementsAs List(conn)
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
