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
  private def changeGraph(changes: List[GraphChanges], user: AuthUser.Persisted): Future[ApiData.Effect[Boolean]] = {
    //TODO more permissions!
    val changesAreAllowed = changes.forall { changes =>
      //TODO check conns
      // addPosts.forall(_.author == user.id) //&& conns.forall(c => !c.content.isReadOnly)
      ApiLogger.client.info("WARNING: Allowing everything")

      val touchedNodes = changes.addNodes ++ changes.updateNodes

      // Author checks: I am the only author
      // TODO: memberships can only be added / deleted if the user hat the rights to do so
      //TODO: consistent timestamps (not in future...)
      def validAddEdges = changes.addEdges.forall {
        case Edge.Author(authorId, _, nodeId) =>
          authorId == user.id && touchedNodes.map(_.id).contains(nodeId)
        case _: Edge.Member => false
        case _ => true
      }

      // assure all nodes have an author edge
      def validNodes = {
        val allPostsWithAuthor = changes.addEdges.collect {
          case Edge.Author(_, _, postId) => postId
        }
        touchedNodes.forall {
          case Node.Content(id, _, _) => allPostsWithAuthor.contains(id)
          case _ => false
        }
      }

      validAddEdges && validNodes
    }

    if (changesAreAllowed) {
      val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
        changes.foldLeft(Future.successful(true)){ (previousSuccess, changes) =>
          import changes.consistent._

          previousSuccess.flatMap { success =>
            if (success) {
              for {
                true <- db.node.create(addNodes)
                _ <- db.edge(addEdges) // TODO: are redundant connections handled?
                true <- db.node.update(updateNodes)
                true <- db.node.delete(delNodes)
                true <- db.edge.delete(delEdges)
                _ <- db.node.addMemberEvenIfLocked(addNodes.map(_.id).toList, user.id, AccessLevel.ReadWrite) //TODO: check
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

  //TODO: error handling
  override def addMember(nodeId: NodeId, newMemberId: UserId, accessLevel: AccessLevel): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isPostMember(nodeId, user.id, AccessLevel.ReadWrite) {
        for {
          Some(user) <- db.user.get(newMemberId)
          added <- db.node.addMemberEvenIfLocked(nodeId, newMemberId, accessLevel)
        } yield Returns(added, if(added) Seq(NewGraphChanges(GraphChanges(addEdges = Set(Edge.Member(newMemberId, EdgeData.Member(accessLevel), nodeId)), addNodes = Set(user)))) else Nil)
      }
    }
  }
  override def setJoinDate(nodeId: NodeId, joinDate: JoinDate): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isPostMember(nodeId, user.id, AccessLevel.ReadWrite) {
        for {
          updatedJoinDate <- db.node.setJoinDate(nodeId, joinDate)
          Some(updatedPost) <- db.node.get(nodeId)
        } yield {
          Returns(updatedJoinDate, Seq(NewGraphChanges.ForAll(GraphChanges.updatePost(updatedPost))))
        }
      }
    }
  }

//  override def addMemberByName(nodeId: NodeId, userName: String): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
//    db.ctx.transaction { implicit ec =>
//      isPostMember(nodeId, user.id) {
//        for {
//          Some(user) <- db.user.byName(userName)
//          Some((_, dbMembership)) <- db.post.addMember(nodeId, user.id)
//        } yield Returns(true, Seq(NewMembership(dbMembership), NewUser(user)))
//      }
//    }
//  }

  override def getGraph(page: Page): ApiFunction[Graph] = Action.requireUser { (state, user) =>
    def defaultGraph = Future.successful(Graph.empty)
    if (page.parentIds.isEmpty) getPage(user.id, page).map(Returns(_))
    else getPage(user.id, page).map(Returns(_))
  }


//  override def importGithubUrl(url: String): ApiFunction[Boolean] = Action.assureDbUser { (_, user) =>

    // TODO: Reuse graph changes instead
//    val (owner, repo, issueNumber) = GitHubImporter.urlExtractor(url)
//    val postsOfUrl = GitHubImporter.getIssues(owner, repo, issueNumber, user)
//    val importEvents = postsOfUrl.flatMap { case (posts, connections) =>
//      db.ctx.transaction { implicit ec =>
//        for {
//          true <- db.post.createPublic(posts)
//          true <- db.connection(connections)
//          changes = GraphChanges(addPosts = posts, addConnections = connections)
//        } yield NewGraphChanges.ForAll(changes) :: Nil
//      }
//    }

//    Future.successful(Returns(true, asyncEvents = Observable.fromFuture(importEvents)))


//    Future.successful(true)
//  }

//  override def importGitterUrl(url: String): ApiFunction[Boolean] = Action.assureDbUser { (_, user) =>
    // TODO: Reuse graph changes instead
//    val postsOfUrl = Set(Post(NodeId(scala.util.Random.nextInt.toString), url, user.id))
//    val postsOfUrl = GitterImporter.getRoomMessages(url, user)
//    val importEvents = postsOfUrl.flatMap { case (posts, connections) =>
//      db.ctx.transaction { implicit ec =>
//        for {
//          true <- db.post.createPublic(posts)
//          true <- db.connection(connections)
//          changes = GraphChanges(addPosts = posts, addConnections = connections)
//        } yield NewGraphChanges.ForAll(changes) :: Nil
//      }
//    }

//    Future.successful(Returns(true, asyncEvents = Observable.fromFuture(importEvents)))
//    Future.successful(true)
//  }

  override def chooseTaskNodes(heuristic: NlpHeuristic, posts: List[NodeId], num: Option[Int]): ApiFunction[List[Heuristic.ApiResult]] = Action.assureDbUser { (state, user) =>
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
    val loggerName = "client-log"
    val formatter = formatter"$date $levelPaddedRight - $message$newLine"
    val writer = FileWriter.flat(prefix = loggerName, maxLogs = Some(3), maxBytes = Some(100 * 1024 * 1024))
    Logger(loggerName)
      .clearHandlers()
      .withHandler(formatter = formatter, minimumLevel = Some(Level.Info), writer = writer)
      .replace()
  }
}
