package wust.core

import io.getquill.context.async.TransactionalExecutionContext
import monix.eval.Task
import monix.execution.Scheduler
import scribe.writer.file.LogPath
import wust.api
import wust.api._
import wust.core.DbConversions._
import wust.core.Dsl._
import wust.core.aws.S3FileUploader
import wust.core.config.ServerConfig
import wust.db.{Data, Db, SuccessResult}
import wust.graph._
import wust.ids._

import scala.collection.breakOut
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ApiImpl(dsl: GuardDsl, db: Db, fileUploader: Option[S3FileUploader], serverConfig: ServerConfig, emailFlow: AppEmailFlow, changeGraphAuthorizer: ChangeGraphAuthorizer[Future], graphChangesNotifier: GraphChangesNotifier)(implicit ec: Scheduler) extends Api[ApiFunction] {
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
  private def changeGraphInternal(allChanges: List[GraphChanges], user: AuthUser.Persisted): Future[ApiData.Effect[Boolean]] = {

    def applyChangesToDb(changes: GraphChanges)(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      import changes.consistent._

      for {
        _ <- db.node.create(addNodes.map(forDb)(breakOut))
        _ <- db.edge.delete(delEdges.map(forDb)(breakOut))
        _ <- db.edge.create(addEdges.map(forDb)(breakOut))
        _ <- db.node.addMember(addNodes.map(_.id)(breakOut), user.id, AccessLevel.ReadWrite)
      } yield SuccessResult
    }

    val changesAuthorization =
      Future.sequence(allChanges.map { changes => changeGraphAuthorizer.authorize(user, changes) })
        .map(_.foldLeft[ChangeGraphAuthorization](ChangeGraphAuthorization.Allow)(ChangeGraphAuthorization.combine))

    changesAuthorization.flatMap {
      case ChangeGraphAuthorization.Allow =>
        val appliedChanges = db.ctx.transaction { implicit ec =>
          allChanges.foldLeft(Future.successful(SuccessResult)) { (previousFuture, changes) =>
            // intentionally chain db-operation future, because transactions
            // can only handle sequential db requests.
            previousFuture.flatMap { _ => applyChangesToDb(changes) }
          }
        }

        appliedChanges.map { _ =>
          val compactChanges = allChanges.foldLeft(GraphChanges.empty)(_ merge _).consistent
          graphChangesNotifier.notify(user, compactChanges)
          Returns(true, Seq(NewGraphChanges.forPublic(user.toNode, compactChanges)))
        }.recover { case NonFatal(e) =>
          scribe.warn("Cannot apply changes", e)
          Returns(false)
        }
      case ChangeGraphAuthorization.Deny(reason) =>
        scribe.warn(s"ChangeGraph was denied, because: $reason")
        Future.successful(Returns.error(ApiError.Forbidden))
    }
  }

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

  private def getNodeInternal(user: AuthUser, nodeId: NodeId): Future[Option[Node]] = db.node.get(user.id, nodeId).map(_.map(forClient))


  override def getUserByEMail(email: String): ApiFunction[Option[Node.User]] = Action {
    db.user.getUserByMail(email).map(_.map(forClient))
  }

  override def getGraph(page: Page): ApiFunction[Graph] = Action.assureDbUserIf(page.parentId.nonEmpty) { (state, user) =>
    getPage(user.id, page)
  }

  override def getUnreadChannels(): ApiFunction[List[NodeId]] = Action.requireUser { (state, user) =>
    ???
  }


  // Simple Api
  // TODO: more efficient
  override def getNodeList(parentId: Option[NodeId], nodeRole: Option[NodeRole] = None): ApiFunction[List[api.SimpleNode]] = Action.assureDbUserIf(parentId.nonEmpty) { (state, user) =>
    getPage(user.id, wust.graph.Page(parentId = parentId)).map { graph =>
      def toSimpleNode(node: Node): Option[SimpleNode] = node match {
        case node: Node.Content if nodeRole.forall(node.role == _) => Some(api.SimpleNode(node.id, node.str, node.role))
        case _ => None
      }

      parentId.flatMap(graph.idToIdx).fold[List[api.SimpleNode]](graph.nodes.flatMap(toSimpleNode)(breakOut)) { parentIdx =>
        graph.notDeletedChildrenIdx(parentIdx).flatMap(idx => toSimpleNode(graph.nodes(idx)))(breakOut)
      }
    }
  }


  override def fileDownloadBaseUrl: ApiFunction[Option[StaticFileUrl]] = Action {
    Future.successful(fileUploader.fold(Option.empty[StaticFileUrl])(_ => Some(s"https://files.${serverConfig.host}"))
  }
  // only real users with email address can upload files
  override def fileUploadConfiguration(key: String, fileSize: Int, fileName: String, fileContentType: String): ApiFunction[FileUploadConfiguration] = Action.requireRealUser { (_, user) =>
    db.user.getUserDetail(user.id).flatMap{
      case Some(Data.UserDetail(userId, Some(inviterEmail), true)) => // only allow verified user with an email to upload files
        fileUploader.fold(Task.pure[FileUploadConfiguration](FileUploadConfiguration.ServiceUnavailable))(_.getFileUploadConfiguration(user.id, key, fileSize = fileSize, fileName = fileName, fileContentType = fileContentType)).runToFuture
      case _ =>
        Future.successful(FileUploadConfiguration.Rejected("Please verify your email address, before you can upload a file!"))
    }
  }
  override def deleteFileUpload(key: String): ApiFunction[Boolean] = Action.requireRealUser { (_, user) =>
    fileUploader.fold(Task.pure(false))(_.deleteFileUpload(user.id, key)).runToFuture
  }
  override def getUploadedFiles: ApiFunction[Seq[UploadedFile]] = Action.requireRealUser { (_, user) =>
    fileUploader.fold(Task.pure(Seq.empty[UploadedFile])) { fileUploader =>
      // TODO: this should be done better and more performant. own upload table? kind of a duplicate of NodeData.File
      // we first check which files are in s3 and then we get the corresponding node data for these files.
      // This way, we have a filename, a content type and so on...alternative: store uploads in own db or get s3 metadata for each key
      val allFiles = fileUploader.getAllObjectSummariesForUser(user.id)
      allFiles.flatMap { files =>
        Task.fromFuture(db.node.getFileNodes(files.map(_.getKey)(breakOut))).map { fileNodes =>
          val fileNodeMap = fileNodes.groupBy(_._2.key)
          files.flatMap { file =>
            fileNodeMap.get(file.getKey).map(_.maxBy(_._1)) match {
              case Some((nodeId, data)) => Some(UploadedFile(nodeId, file.getSize, data))
              case None =>
                // Somehow we do not have a node for this upload, we just delete it. Seems, as if it is not needed
                scribe.warn(s"Found uploaded file with key '${file.getKey}' without any corresponding node. Will delete this file.")
                fileUploader.deleteKeyInS3Bucket(file.getKey).runAsyncAndForget
                None
            }
          }.sortBy(_.nodeId).reverse
        }
      }
    }.runToFuture
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

  override def currentTime: Dsl.ApiFunction[EpochMilli] = Action { Future.successful(EpochMilli.now) }

  override def log(message: String): ApiFunction[Boolean] = Action { state =>
    val msgId = state.auth.fold("anonymous")(_.user.id.toCuidString)
    scribe.info(s"[$msgId] $message")
    Future.successful(true)
  }

  override def feedback(clientInfo: ClientInfo, message: String): ApiFunction[Unit] = Action.requireUser { (_, user) =>
    db.user.getUserDetail(user.id).map { userDetail =>
      val userEmail = userDetail.flatMap(_.email)
      emailFlow.sendEmailFeedback(user.id, userName = user.name, userEmail = userEmail, clientInfo = clientInfo, msg = message)
    }
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  private def getPage(userId: UserId, page: Page)(implicit ec: ExecutionContext): Future[Graph] = {
    // handle public nodes as invite links:
    // the link of a public node acts as an invite link. Therefore when getting the graph of a public node,
    // you automatically become a member of this node, then we get the graph and use normal access management.
    //TODO: hacky and require all callers to assure that user exists in db if parentId is defined...
    val requiredAction = page.parentId.fold(Future.successful(())) { parentId =>
      db.node.addMemberIfCanAccessViaUrlAndNotMember(nodeId = parentId, userId = userId).map(_ => ())
    }

    // TODO: also include the transitive parents of the page-parentId to be able no navigate upwards
    requiredAction.flatMap { _ =>
      db.graph.getPage(page.parentId.toSeq, userId).map(forClient)
    }
  }
}
