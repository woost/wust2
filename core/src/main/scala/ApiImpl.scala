package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.db.Db
import wust.graph._
import wust.ids._
import wust.util.RandomUtil
import monix.reactive.Observable

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import cats.implicits._

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
  //TODO: only accept one GraphChanges object
  private def changeGraph(changes: List[GraphChanges], user: User.Persisted): Future[ApiData.Effect[Boolean]] = {
    //TODO more permissions!
    val changesAreAllowed = {
      val addPosts = changes.flatMap(_.addPosts)
      addPosts.forall(_.author == user.id)
    }

    if (changesAreAllowed) {
      val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
        changes.foldLeft(Future.successful(true)){ (previousSuccess, changes) =>
          import changes.consistent._

          previousSuccess.flatMap { success =>
            if (success) {
              for {
                true <- db.post.createPublic(addPosts)
                true <- db.connection(addConnections)
                true <- db.post.update(updatePosts)
                true <- db.post.delete(delPosts)
                true <- db.connection.delete(delConnections)
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

  override def addCurrentUserAsMember(postId: PostId): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    //TODO: only add member if post is not public
    db.ctx.transaction { implicit ec =>
      for {
        Some((_, dbMembership)) <- db.post.addMember(postId, user.id)
      } yield Returns(true, Seq(NewMembership(dbMembership), NewUser(user)))
    }
  }
  //TODO: error handling
  override def addMember(postId: PostId, userId: UserId): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      for {
        Some(user) <- db.user.get(userId)
        Some((_, dbMembership)) <- db.post.addMember(postId, userId)
      } yield Returns(true, Seq(NewMembership(dbMembership), NewUser(user)))
    }
  }

  override def addMemberByName(postId: PostId, userName: String): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isPostMember(postId, user.id) {
        for {
          Some(user) <- db.user.byName(userName)
          Some((_, dbMembership)) <- db.post.addMember(postId, user.id)
        } yield Returns(true, Seq(NewMembership(dbMembership), NewUser(user)))
      }
    }
  }

  override def getGraph(selection: Page): ApiFunction[Graph] = Action { state =>
    val userIdOpt = state.auth.dbUserOpt.map(_.id)
    val graph = selection match {
      case Page(parentIds) if parentIds.isEmpty =>
        db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent) // TODO: consistent should not be necessary here
      case Page(parentIds) =>
        getUnion(userIdOpt, parentIds).map(_.consistent) // TODO: consistent should not be necessary here
    }

    graph
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
        } yield ApiEvent.NewGraphChanges.ForAll(changes) :: Nil
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
        } yield ApiEvent.NewGraphChanges.ForAll(changes) :: Nil
      }
    }

    Future.successful(Returns(true, asyncEvents = Observable.fromFuture(importEvents)))
  }

  override def chooseTaskPosts(heuristic: NlpHeuristic, posts: List[PostId], num: Option[Int]): ApiFunction[List[Heuristic.ApiResult]] = Action { state =>
    Future { PostHeuristic(state.graph, heuristic, posts, num) }
  }

  override def log(message: String): ApiFunction[Boolean] = Action { state =>
    ApiLogger.client.info(s"[${state.auth.user}] $message")
    Future.successful(true)
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  private def getUnion(userIdOpt: Option[UserId], rawParentIds: Set[PostId])(implicit ec: ExecutionContext): Future[Graph] = {
    //TODO: in stored procedure
    // we also include the direct parents of the parentIds to be able no navigate upwards
    db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val parentIds = rawParentIds filter graph.postsById.isDefinedAt
      val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
      val descendantsWithDirectParents = descendants ++ parentIds.flatMap(graph.parents)
      graph removePosts graph.postIds.filterNot(descendantsWithDirectParents)
    }
  }
}

object ApiLogger {
  import scribe._
  import scribe.formatter.FormatterBuilder
  import scribe.writer._

  val client: Logger = {
    val s = "client-log"

    val formatter = FormatterBuilder()
      .date()
      .string(" ")
      .level
      .string(" - ")
      .message.newLine
    Logger.byName(s).clearHandlers()
    Logger.byName(s).addHandler(LogHandler(formatter = formatter, writer = FileWriter.daily(name = s), level = Level.Info))
    Logger.byName(s)
  }
}
