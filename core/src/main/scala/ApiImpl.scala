package wust.backend

import io.getquill.context.async.TransactionalExecutionContext
import monix.reactive.Observable
import scribe.writer.file.LogPath
import wust.api._
import wust.backend.DbConversions._
import wust.backend.Dsl._
import wust.db.{Data, Db, SuccessResult}
import wust.graph._
import wust.ids._

import scala.collection.breakOut
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ApiImpl(dsl: GuardDsl, db: Db)(implicit ec: ExecutionContext) extends Api[ApiFunction] {
  import ApiEvent._
  import dsl._

  override def changeGraph(
      changes: List[GraphChanges],
      onBehalf: Authentication.Token
  ): ApiFunction[Boolean] = Effect.assureDbUser { (_, _) =>
    onBehalfOfUser(onBehalf)(auth => changeGraphInternal(changes, auth.user))
  }
  override def changeGraph(changes: List[GraphChanges]): ApiFunction[Boolean] =
    Effect.assureDbUser { (_, user) =>
      changeGraphInternal(changes, user)
    }

  //TODO assure timestamps of posts are correct
  //TODO: only accept one GraphChanges object: we need an api for multiple.
  private def changeGraphInternal(
      changes: List[GraphChanges],
      user: AuthUser.Persisted
  ): Future[ApiData.Effect[Boolean]] = {

    //  addNodes // none of the ids can already exist
    //  addEdges // needs permissions on all involved nodeids, or nodeids are in addNodes
    //  delEdges // needs permissions on all involved nodeids

    val changesAreAllowed = changes.forall { changes =>
      //TODO check conns
      // addPosts.forall(_.author == user.id) //&& conns.forall(c => !c.content.isReadOnly)

      // Author checks: I am the only author
      // TODO: memberships can only be added / deleted if the user hat the rights to do so, currently not possible. maybe allow?
      //TODO: consistent timestamps (not in future...)
      //TODO: white-list instead of black-list what a user can do?
      def validAddEdges = changes.addEdges.forall {
        case Edge.Author(authorId, _, nodeId) =>
          authorId == user.id && changes.addNodes.map(_.id).contains(nodeId)
        case _: Edge.Member => false //TODO: map to addMember API call
        case _              => true
      }

      // assure all nodes have an author edge
      def validNodes = {
        val allPostsWithAuthor = changes.addEdges.collect {
          case Edge.Author(_, _, postId) => postId
        }
        changes.addNodes.forall {
          case node:Node.Content => allPostsWithAuthor.contains(node.id)
          case _                      => false
        }
      }

      def validDeleteEdges = changes.delEdges.forall {
        case _: Edge.Author => false
        case _ => true
      }

      validAddEdges && validNodes && validDeleteEdges
    }

    // TODO: task instead of this function
    val checkAllChanges: () => Future[SuccessResult.type] = () => {
      val usedIdsFromDb = changes.flatMap(_.involvedNodeIds) diff changes.flatMap(
        _.addNodes
          .map(_.id) // we leave out addNodes, since they do not exist yet. and throws on conflict anyways
      )
      db.user.inaccessibleNodes(user.id, usedIdsFromDb).flatMap { conflictingIds =>
        if (conflictingIds.isEmpty) Future.successful(SuccessResult)
        else {
          Future.failed(new Exception(
            s"Graph changes not allowed, there are inaccessible node ids in this change set: ${conflictingIds
              .map(_.toUuid)}"
          ))
        }
      }
    }

    def applyChangesToDb(changes: GraphChanges)(implicit ec: TransactionalExecutionContext): () => Future[SuccessResult.type] = () => {
      import changes.consistent._

      for {
        _ <- db.node.create(addNodes.map(forDb)(breakOut))
        _ <- db.edge.create(addEdges.map(forDb)(breakOut))
        _ <- db.edge.delete(delEdges.map(forDb)(breakOut))
        _ <- db.node.addMember(addNodes.map(_.id)(breakOut), user.id, AccessLevel.ReadWrite)
      } yield SuccessResult
    }

    if (changesAreAllowed) {
      val result: Future[SuccessResult.type] = db.ctx.transaction { implicit ec =>
        (checkAllChanges +: changes.map(applyChangesToDb)).foldLeft(Future.successful(SuccessResult)) {
          (previousSuccess, operation) => previousSuccess.flatMap { _ => operation() }
        }
      }

      result.map { _ =>
        val compactChanges = changes.foldLeft(GraphChanges.empty)(_ merge _).consistent
        Returns(true, Seq(NewGraphChanges(user.toNode, compactChanges)))
      }.recover { case NonFatal(e) =>
        scribe.warn("Cannot apply changes", e)
        Returns(false)
      }
    } else Future.successful(Returns.error(ApiError.Forbidden))
  }

  //TODO: error handling
  override def addMember(
      nodeId: NodeId,
      newMemberId: UserId,
      accessLevel: AccessLevel
  ): ApiFunction[Boolean] = Effect.assureDbUser { (_, user) =>
    db.ctx.transaction { implicit ec =>
      canAccessNode(user.id, nodeId) {
        for {
          Some(user) <- db.user.get(newMemberId)
          added <- db.node.addMember(nodeId, newMemberId, accessLevel)
        } yield
          Returns(
            added,
            if (added)
              Seq(
                NewGraphChanges(
                  user,
                  GraphChanges(
                    addEdges = Set(Edge.Member(newMemberId, EdgeData.Member(accessLevel), nodeId)),
                  )
                )
              )
            else Nil
          )
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

  override def getNode(
                            nodeId: NodeId,
                            onBehalf: Authentication.Token
                          ): ApiFunction[Option[Node]] = Action { _ =>
    onBehalfOfUser(onBehalf)(auth => getNodeInternal(auth.user, nodeId))
  }

  override def getNode(nodeId: NodeId): ApiFunction[Option[Node]] =
    Action.requireUser { (_, user) =>
      getNodeInternal(user, nodeId)
    }

  private def getNodeInternal(user: AuthUser, nodeId: NodeId): Future[Option[Node]] = db.node.get(user.id, nodeId).map(_.map(forClient(_)))


  override def getUserId(name: String): ApiFunction[Option[UserId]] = Action {
    db.user.byName(name).map(_.map(_.id))
  }

  override def getGraph(page: Page): ApiFunction[Graph] = Action.requireUser { (state, user) =>
    getPage(user.id, page)
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

  override def chooseTaskNodes(
      heuristic: NlpHeuristic,
      posts: List[NodeId],
      num: Option[Int]
  ): ApiFunction[List[Heuristic.ApiResult]] = Action.assureDbUser { (state, user) =>
    ???
    //    getPage(user.id, Page.empty).map(PostHeuristic(_, heuristic, posts, num))
  }

  override def currentTime: Dsl.ApiFunction[EpochMilli] = Action { Future.successful(EpochMilli.now) }

  override def log(message: String): ApiFunction[Boolean] = Action { state =>
    val msgId = state.auth.fold("anonymous")(_.user.id.toCuidString)
    ApiLogger.client.info(s"[$msgId] $message")
    Future.successful(true)
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  private def getPage(userId: UserId, page: Page)(implicit ec: ExecutionContext): Future[Graph] = {
    // TODO: also include the transitive parents of the page-parentId to be able no navigate upwards
    db.graph.getPage(page.parentId.toSeq, userId).map(forClient)
  }
}

object ApiLogger {
  import scribe._
  import scribe.format._
  import scribe.writer._

  val client: Logger = {
   val loggerName = "client-log"
   val formatter = formatter"$date $levelPaddedRight - $message$newLine"
   val writer = FileWriter().path(LogPath.daily(prefix = loggerName)).maxLogs(max = 3).maxSize(maxSizeInBytes = 100 * 1024 * 1024)
   Logger(loggerName)
     .clearHandlers()
     .withHandler(formatter = formatter, minimumLevel = Some(Level.Info), writer = writer)
     .replace()
  }
}
