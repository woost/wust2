package wust.backend

import monix.reactive.Observable
import wust.api._
import wust.backend.DbConversions._
import wust.backend.Dsl._
import wust.db.Db
import wust.graph._
import wust.ids._
import scala.collection.breakOut

import scala.concurrent.{ExecutionContext, Future}

class ApiImpl(dsl: GuardDsl, db: Db)(implicit ec: ExecutionContext) extends Api[ApiFunction] {
  import ApiEvent._
  import dsl._

  override def changeGraph(changes: List[GraphChanges], onBehalf: Authentication.Token): ApiFunction[Boolean] = Effect.assureDbUser { (_, _) =>
    onBehalfOfUser(onBehalf)(auth => changeGraph(changes, auth.user))
  }
  override def changeGraph(changes: List[GraphChanges]): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    changeGraph(changes, user)
  }

  //TODO assure timestamps of posts are correct
  //TODO: only accept one GraphChanges object: we need an api for multiple.
  private def changeGraph(changes: List[GraphChanges], user: User.Persisted): Future[ApiData.Effect[Boolean]] = {
    //TODO more permissions!
    val changesAreAllowed = {
      val addPosts = changes.flatMap(_.addPosts)
      val conns = changes.flatMap(c => c.addConnections ++ c.delConnections)
      addPosts.forall(_.author == user.id) && conns.forall(c => !Label.isMeta(c.label))
    }

    if (changesAreAllowed) {
      val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
        changes.foldLeft(Future.successful(true)){ (previousSuccess, changes) =>
          import changes.consistent._

          previousSuccess.flatMap { success =>
            if (success) {
              for {
                true <- db.post.createPublic(addPosts)
                _ <- db.connection(addConnections) // TODO: are redundant connections handled?
                true <- db.post.update(updatePosts)
                true <- db.post.delete(delPosts)
                true <- db.connection.delete(delConnections)
                _ <- db.post.addMemberEvenIfLocked(addPosts.map(_.id).toList, user.id, AccessLevel.ReadWrite) //TODO: check
              } yield true
            } else Future.successful(false)
          }
        }
      }

      result.map { success =>
        if (success) {
          val compactChanges = changes.foldLeft(GraphChanges.empty)(_ merge _)
          Returns(true, Seq(NewGraphChanges(compactChanges)))
        } else Returns(false)
      }
    } else Future.successful(Returns.error(ApiError.Forbidden))
  }

  override def getPost(id: PostId): ApiFunction[Option[Post]] = Action(db.post.get(id).map(_.map(forClient))) //TODO: check if public or user has access
  override def getUser(id: UserId): ApiFunction[Option[User]] = Action(db.user.get(id).map(_.map(forClient)))

  //TODO: error handling
  override def addMember(postId: PostId, newMemberId: UserId, accessLevel: AccessLevel): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isPostMember(postId, user.id, AccessLevel.ReadWrite) {
        for {
          Some(user) <- db.user.get(newMemberId)
          added <- db.post.addMemberEvenIfLocked(postId, newMemberId, accessLevel)
        } yield Returns(added, if(added) Seq(NewMembership(newMemberId, postId), NewUser(user)) else Nil)
      }
    }
  }
  override def setJoinDate(postId: PostId, joinDate: JoinDate): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isPostMember(postId, user.id, AccessLevel.ReadWrite) {
        for {
          updatedJoinDate <- db.post.setJoinDate(postId, joinDate)
          Some(updatedPost) <- db.post.get(postId)
        } yield {
          Returns(updatedJoinDate, Seq(NewGraphChanges.ForAll(GraphChanges.updatePost(updatedPost))))
        }
      }
    }
  }

//  override def addMemberByName(postId: PostId, userName: String): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
//    db.ctx.transaction { implicit ec =>
//      isPostMember(postId, user.id) {
//        for {
//          Some(user) <- db.user.byName(userName)
//          Some((_, dbMembership)) <- db.post.addMember(postId, user.id)
//        } yield Returns(true, Seq(NewMembership(dbMembership), NewUser(user)))
//      }
//    }
//  }

  //TODO: get graph forces new db user...
  ///TODO the caller aka frontend knows whether we need a membership or whether we want to add this as channel.
  //therefore most calls to getgraph dont need membership+channel insert. this would improve performance.
  override def getGraph(page: Page): ApiFunction[Graph] = Effect.assureDbUser { (state, user) =>
    def defaultGraph = Future.successful(Graph.empty)
    if (page.parentIds.isEmpty) getPage(user.id, page).map(Returns(_))
    else for {
      newMemberPostIds <- db.post.addMemberWithCurrentJoinLevel(page.parentIds.toList, user.id)
      graph <- getPage(user.id, page)
    } yield {
      Returns(graph, newMemberPostIds.map(NewMembership(user.id, _)))
    }
  }

  override def importGithubUrl(url: String): ApiFunction[Boolean] = Action.assureDbUser { (_, user) =>

    // TODO: Reuse graph changes instead
    val (owner, repo, issueNumber) = GitHubImporter.urlExtractor(url)
    val postsOfUrl = GitHubImporter.getIssues(owner, repo, issueNumber, user)
    val importEvents = postsOfUrl.flatMap { case (posts, connections) =>
      db.ctx.transaction { implicit ec =>
        for {
          true <- db.post.createPublic(posts)
          true <- db.connection(connections)
          changes = GraphChanges(addPosts = posts, addConnections = connections)
        } yield NewGraphChanges.ForAll(changes) :: Nil
      }
    }

    Future.successful(Returns(true, asyncEvents = Observable.fromFuture(importEvents)))
  }

  override def importGitterUrl(url: String): ApiFunction[Boolean] = Action.assureDbUser { (_, user) =>
    // TODO: Reuse graph changes instead
//    val postsOfUrl = Set(Post(PostId(scala.util.Random.nextInt.toString), url, user.id))
    val postsOfUrl = GitterImporter.getRoomMessages(url, user)
    val importEvents = postsOfUrl.flatMap { case (posts, connections) =>
      db.ctx.transaction { implicit ec =>
        for {
          true <- db.post.createPublic(posts)
          true <- db.connection(connections)
          changes = GraphChanges(addPosts = posts, addConnections = connections)
        } yield NewGraphChanges.ForAll(changes) :: Nil
      }
    }

    Future.successful(Returns(true, asyncEvents = Observable.fromFuture(importEvents)))
  }

  override def chooseTaskPosts(heuristic: NlpHeuristic, posts: List[PostId], num: Option[Int]): ApiFunction[List[Heuristic.ApiResult]] = Action.assureDbUser { (state, user) =>
    getPage(user.id, Page.empty).map(PostHeuristic(_, heuristic, posts, num))
  }

  override def log(message: String): ApiFunction[Boolean] = Action { state =>
    val msgId = state.auth.fold("anonymous")(_.user.id)
    ApiLogger.client.info(s"[$msgId] $message")
    Future.successful(true)
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  private def getPage(userId: UserId, page: Page)(implicit ec: ExecutionContext): Future[Graph] = {
    // TODO: also include the direct parents of the parentIds to be able no navigate upwards
    (page.mode match {
      case PageMode.Default => db.graph.getPage(page.parentIds.toList, page.childrenIds.toList, userId)
      case PageMode.Orphans => db.graph.getPageWithOrphans(page.parentIds.toList, page.childrenIds.toList, userId)
    }).map { dbGraph => forClient(dbGraph) }
  }
}

object ApiLogger {
  import scribe._
  import scribe.format._
  import scribe.writer._

  val client: Logger = {
    val s = "client-log"
    val formatter = formatter"$date $levelPaddedRight - $message$newLine"
    Logger.update(s)(
      _.clearHandlers()
        .withHandler(formatter = formatter, minimumLevel = Some(Level.Info), writer = FileWriter.daily(prefix = s))
    )
  }
}
