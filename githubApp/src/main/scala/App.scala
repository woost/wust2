package wust.github

import covenant.http._, ByteBufferImplicits._
import sloth._
import java.nio.ByteBuffer

import boopickle.shapeless.Default._
import chameleon.ext.boopickle._
import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange}
import mycelium.client.SendType
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.stream.ActorMaterializer
import cats.data.EitherT
import cats.free.Free
import cats.implicits._
import com.typesafe.config.ConfigFactory
import io.circe._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.collection.mutable
import github4s.Github
import github4s.Github._
import github4s.GithubResponses.{GHException, GHResponse, GHResult}
import github4s.free.domain.{Comment, Issue, NewOAuthRequest, OAuthToken, User => GHUser}
import github4s.jvm.Implicits._
import monix.execution.Scheduler
import monix.reactive.Observable
import cats.implicits._
import github4s.app.GitHub4s

import scala.util.{Failure, Success, Try}
import scalaj.http.HttpResponse
import com.redis._

import scala.collection.concurrent.TrieMap

object Constants {
  //TODO
  val githubId = PostId("wust-github")
  val issueTagId = PostId("github-issue")
  val commentTagId = PostId("github-comment")

  val wustOwner = "woost"
  val wustRepo = "bug"

  val wustUser = UserId("wust") // TODO get rid of this static user assumption

}

object DBConstants {
  val githubUserId = "githubUserId"
  val wustUserId = "wustUserId"
  val githubToken = "githubToken"
  val wustToken = "wustToken"
}

class GithubApiImpl(client: WustClient, server: ServerConfig, github: GithubConfig, redis: RedisClient)(implicit ec: ExecutionContext) extends PluginApi {
  def connectUser(auth: Authentication.Token): Future[Option[String]] = {
    client.auth.verifyToken(auth).map {
      case Some(verifiedAuth) =>
        scribe.info(s"User has valid auth: ${verifiedAuth.user.name}")
        // Step 1: Add wustId -> wustToken
        AuthClient.addWustToken(redis, verifiedAuth.user.id, verifiedAuth.token)

        // Generate url called by client (e.g. WebApp)
        AuthClient.generateAuthUrl(verifiedAuth.user.id, server, github)
      case None =>
        scribe.info(s"Invalid auth")
        None
    }
  }
}

object AuthClient {
  import shapeless.syntax.std.function._
  import shapeless.Generic

  /** Mappings for Wust <-> Github Interactions
    * wustUserId -> (wustToken, githubUserId)
    * githubUserId -> (githubToken, wustUserId)
    */
  def addWustToken(client: RedisClient, wustUserId: UserId, wustToken: String): Option[Long] = {
    client.hset1(s"wu_$wustUserId", DBConstants.wustToken, wustToken)
  }
  def addGithubUser(client: RedisClient, wustUserId: UserId, githubUserId: Int): Option[Long] = {
    client.hset1(s"wu_$wustUserId", DBConstants.githubUserId, s"gh_$githubUserId")
  }
  def addGithubToken(client: RedisClient, githubUserId: Int, githubToken: String): Option[Long] = {
    client.hset1(s"gh_$githubUserId", DBConstants.githubToken, githubToken)
  }
  def addWustUser(client: RedisClient, githubUserId: Int, wustUserId: UserId): Option[Long] = {
    client.hset1(s"gh_$githubUserId", DBConstants.wustUserId, wustUserId)
  }

  var oAuthRequests: TrieMap[String, UserId] = TrieMap.empty[String, UserId]

  def confirmOAuthRequest(code: String, state: String): Boolean = {
    val currRequest = oAuthRequests.get(state) match {
      case Some(_) => true
      case _ =>
        scribe.error(s"Could not confirm oAuthRequest. No such request in queue")
        false
    }
    code.nonEmpty && currRequest
  }

  def getToken(oAuthRequest: NewOAuthRequest): Option[OAuthToken] = {
    (Github(None).auth.getAccessToken _)
      .toProduct(Generic[NewOAuthRequest].to(oAuthRequest))
      .exec[cats.Id, HttpResponse[String]]() match {
      case Right(resp) =>
        scribe.info(s"Received OAuthToken: ${resp.result}")
        Some(resp.result: OAuthToken)
      case Left(err) =>
        scribe.error(s"Could not receive OAuthToken: ${err.getMessage}")
        None
    }
  }

  def generateAuthUrl(userId: UserId, server: ServerConfig, github: GithubConfig): Option[String] = {
    val scopes = List("read:org", "read:user", "repo", "write:discussion")
    val redirectUri = s"http://${server.host}:${server.port}/${server.authPath}"

    import github4s.jvm.Implicits._
    Github(None).auth.authorizeUrl(github.clientId, redirectUri, scopes)
      .exec[cats.Id, HttpResponse[String]]() match {
      case Right(GHResult(result, _, _)) =>
        oAuthRequests.putIfAbsent(result.state, userId) match {
          case None => Some(result.url)
          case _ =>
            scribe.error("Duplicate state in url generation")
            None
        }
      case Left(err) =>
        scribe.error(s"Could not generate url: ${err.getMessage}")
        None
    }
  }
}

object AppServer {
  import akka.http.scaladsl.server.RouteResult._
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.Http
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  private def createIssue(issue: Issue) = GraphChanges.empty
  private def editIssue(issue: Issue) = GraphChanges.empty
  private def deleteIssue(issue: Issue) = GraphChanges.empty
  private def createComment(issue: Issue, comment: Comment) = GraphChanges.empty
  private def editComment(issue: Issue, comment: Comment) = GraphChanges.empty
  private def deleteComment(issue: Issue, comment: Comment) = GraphChanges.empty

  def run(server: ServerConfig, github: GithubConfig, redis: RedisClient, wustReceiver: WustReceiver)(implicit system: ActorSystem): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    import system.dispatcher

    import io.circe.generic.auto._
    import cats.implicits._

    val apiRouter = Router[ByteBuffer, Future]
      .route[PluginApi](new GithubApiImpl(wustReceiver.client, server, github, redis))

    val corsSettings = CorsSettings.defaultSettings.copy(
      allowedOrigins = HttpOriginRange(server.allowedOrigins.map(HttpOrigin(_)): _*))

    case class IssueEvent(action: String, issue: Issue)
    case class IssueCommentEvent(action: String, issue: Issue, comment: Comment)

    val route = {
      pathPrefix("api") {
        cors(corsSettings) {
          AkkaHttpRoute.fromFutureRouter(apiRouter)
        }
      } ~ path(server.authPath) {
        get {
          parameters('code, 'state) { (code, state) =>
            if(AuthClient.confirmOAuthRequest(code, state)) {
              val tokenRequest = NewOAuthRequest(github.clientId, github.clientSecret, code, s"http://${server.host}:${server.port}/${server.authPath}", state)
              val token = AuthClient.getToken(tokenRequest).map{ ghToken =>
                scribe.info(s"Verified token request(code, state) - Persisting token for user: ${AuthClient.oAuthRequests(state)}")
                ghToken.access_token
              }
              Github(token).users.getAuth.exec[cats.Id, HttpResponse[String]]() match {
                case Right(r) =>
                  val wustUserId = AuthClient.oAuthRequests(state)
                  val githubUserId = r.result.id
                  // Github data
                  AuthClient.addGithubToken(redis, githubUserId, token.get)
                  AuthClient.addWustUser(redis, githubUserId, wustUserId)
                  // Wust data
                  AuthClient.addGithubUser(redis, wustUserId, githubUserId)
                  AuthClient.oAuthRequests.remove(state)
                case Left(e) => println(s"Could not authenticate with OAuthToken: ${e.getMessage}")
              }
            } else {
              scribe.error(s"Could not verify request(code, state): ($code, $state)")
            }
            redirect(s"http://${server.host}:12345/#usersettings", StatusCodes.SeeOther)
          }
        }
      } ~ path(server.webhookPath) {
        post {
          decodeRequest {
            headerValueByName("X-GitHub-Event") {
              case "issues" => entity(as[IssueEvent]) { issueEvent =>
                issueEvent.action match {
                  case "created" =>
                    scribe.info("Received Webhook: created issue")
//                    if(EventCoordinator.createOrIgnore(issueEvent.issue))
                      wustReceiver.push(List(createIssue(issueEvent.issue)))
                  case "edited" =>
                    scribe.info("Received Webhook: edited issue")
                    wustReceiver.push(List(editIssue(issueEvent.issue)))
                  case "deleted" =>
                    scribe.info("Received Webhook: deleted issue")
                    wustReceiver.push(List(deleteIssue(issueEvent.issue)))
                  case a => scribe.error(s"Received unknown IssueEvent action: $a")
                }
                complete(StatusCodes.Success)
              }
              case "issue_comment" => entity(as[IssueCommentEvent]) {issueCommentEvent =>
                issueCommentEvent.action match {
                  case "created" =>
                    scribe.info("Received Webhook: created comment")
                    wustReceiver.push(List(createComment(issueCommentEvent.issue, issueCommentEvent.comment)))
                  case "edited" =>
                    scribe.info("Received Webhook: edited comment")
                    wustReceiver.push(List(editComment(issueCommentEvent.issue, issueCommentEvent.comment)))
                  case "deleted" =>
                    scribe.info("Received Webhook: deleted comment")
                    wustReceiver.push(List(deleteComment(issueCommentEvent.issue, issueCommentEvent.comment)))
                  case a => scribe.error(s"Received unknown IssueCommentEvent: $a")
                }
                complete(StatusCodes.Success)
              }
              case "ping" =>
                scribe.info("Received ping")
                complete(StatusCodes.Accepted)
              case e =>
                scribe.error(s"Received unknown GitHub Event Header: $e")
                complete(StatusCodes.Accepted)
            }
          }
        }
      }
    }

    Http().bindAndHandle(route, interface = server.host, port = server.port).onComplete {
      case Success(binding) =>
        val separator = "\n" + ("#" * 60)
        val readyMsg = s"\n##### GitHub App Server online at ${binding.localAddress} #####"
        scribe.info(s"$separator$readyMsg$separator")
      case Failure(err) => scribe.error(s"Cannot start GitHub App Server: $err")
    }
  }
}

sealed trait GithubCall

case class CreateIssue(owner: String, repo: String, title: String, content: String, postId: PostId) extends GithubCall
case class EditIssue(owner: String, repo: String, externalNumber: Int, status: String, title: String, content: String, postId: PostId) extends GithubCall
case class DeleteIssue(owner: String, repo: String, externalNumber: Int, title: String, content: String, postId: PostId) extends GithubCall
case class CreateComment(owner: String, repo: String, externalIssueNumber: Int, content: String, postId: PostId) extends GithubCall
case class EditComment(owner: String, repo: String, externalId: Int, content: String, postId: PostId) extends GithubCall
case class DeleteComment(owner: String, repo: String, externalId: Int, postId: PostId) extends GithubCall

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(graphChanges: List[GraphChanges]): Result[List[GraphChanges]]
}

class WustReceiver(val client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(graphChanges: List[GraphChanges]): Future[Either[String, List[GraphChanges]]] = {
    scribe.info(s"pushing new graph change: $graphChanges")
    //TODO use onBehalf with different token
    // client.api.changeGraph(graphChanges, onBehalf = token).map{ success =>
    client.api.changeGraph(graphChanges).map{ success =>
      if(success) Right(graphChanges)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  val mCallBuffer: mutable.Set[GithubCall] = mutable.Set.empty[GithubCall]

  object GraphTransition {
    def empty: GraphTransition = new GraphTransition(Graph.empty, Seq.empty[GraphChanges], Graph.empty)
  }
  case class GraphTransition(prevGraph: Graph, changes: Seq[GraphChanges], resGraph: Graph)

  def run(config: WustConfig, github: GithubClient)(implicit system: ActorSystem): WustReceiver = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val scheduler: Scheduler = Scheduler(system.dispatcher)

    //TODO: service discovery or some better configuration for the wust host
    val protocol = if (config.port == 443) "wss" else "ws"
    val location = s"$protocol://core.${config.host}:${config.port}/ws"
    val wustClient = WustClient(location)
    val client = wustClient.sendWith(SendType.WhenConnected, 30 seconds)
    val highPriorityClient = wustClient.sendWith(SendType.WhenConnected.highPriority, 30 seconds)

    highPriorityClient.auth.assumeLogin(Constants.wustUser)
    highPriorityClient.auth.register(config.user, config.password)
    wustClient.observable.connected.foreach { _ =>
      highPriorityClient.auth.login(config.user, config.password)
    }

    val changes = GraphChanges(addPosts = Set(Post(Constants.githubId, "wust-github", Constants.wustUser)))
    client.api.changeGraph(List(changes))

    println("Running WustReceiver")

    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = wustClient.observable.event
      .map(e => {println(s"triggering collect on $e"); e.collect { case ev: ApiEvent.GraphContent => println("received api event"); ev }})
      .collect { case list if list.nonEmpty => println("api event non-empty"); list }

    val graphObs: Observable[GraphTransition] = graphEvents.scan(GraphTransition.empty) { (prevTrans, events) =>
      println(s"Got events: $events")
      val changes = events collect { case ApiEvent.NewGraphChanges(_changes) => _changes }
      val nextGraph = events.foldLeft(prevTrans.resGraph)(EventUpdate.applyEventOnGraph)
      GraphTransition(prevTrans.resGraph, changes, nextGraph)
    }

    val githubApiCalls: Observable[Seq[GithubCall]] = graphObs.map { graphTransition =>
      createCalls(github, graphTransition)
    }

    println("Calling side-effect in github app")
    githubApiCalls.foreach(_.foreach {
      case c: CreateIssue =>
        mCallBuffer += c
        github.createIssue(c).foreach {
          case Right(issue) => EventCoordinator.addFutureCompletion(c, issue)
          case Left(e) => scribe.error(e)
        }
      case c: EditIssue =>
        mCallBuffer += c
        github.editIssue(c).foreach {
          case Right(issue) => EventCoordinator.addFutureCompletion(c, issue)
          case Left(e) => scribe.error(e)
        }
      case c: DeleteIssue =>
        mCallBuffer += c
        github.deleteIssue(c).foreach {
          case Right(issue) => EventCoordinator.addFutureCompletion(c, issue)
          case Left(e) => scribe.error(e)
        }
      case c: CreateComment =>
        mCallBuffer += c
        github.createComment(c).foreach { res =>
          val tag = Connection(c.postId, Label.parent, Constants.commentTagId)
          println(s"Sending add comment tag: $tag")
          valid(client.api.changeGraph(List(GraphChanges(addConnections = Set(tag)))), "Could not redirect comment to add tag")
          res match {
            case Right(comment) => EventCoordinator.addFutureCompletion(c, comment)
            case Left(e) => scribe.error(e)
          }
        }
      case c: EditComment =>
        mCallBuffer += c
        github.editComment(c).foreach {
          case Right(comment) => EventCoordinator.addFutureCompletion(c, comment)
          case Left(e) => scribe.error(e)
        }
      case c: DeleteComment =>
        mCallBuffer += c
        github.deleteComment(c).foreach {
          case Right(comment) => EventCoordinator.addFutureCompletion(c, comment)
          case Left(e) => scribe.error(e)
        }

      case _ => println("Could not match to github api call")
    })

    new WustReceiver(client)
  }

  private def createCalls(github: GithubClient, graphTransition: GraphTransition): Seq[GithubCall] = {


    def getAncestors(prevGraph: Graph, graph: Graph, graphChanges: GraphChanges): Map[PostId, Iterable[PostId]] = {

      val addAncestors = graphChanges.addPosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((m, p) => {
        m + (p.id -> graph.ancestors(p.id))
      })

      val updateAncestors = graphChanges.updatePosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((m, p) => {
        m + (p.id -> graph.ancestors(p.id))
      })

      val delAncestors = graphChanges.delPosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((m, pid) => {
        m + (pid -> prevGraph.ancestors(pid))
      })

      addAncestors ++ updateAncestors ++ delAncestors
    }

    def issuePostOfDesc(graph: Graph, pid: PostId): Option[Post] = {
      graph.connectionsByLabel(Label("describes"))
        .find(c => c.targetId == pid)
        .map(c => graph.postsById(c.sourceId))
    }

    // TODO: PostId <=> ExternalId mapping
    graphTransition.changes.flatMap{ gc: GraphChanges =>

      val currGraph = graphTransition.resGraph
      val prevGraph = graphTransition.prevGraph
      val ancestors = getAncestors(prevGraph, currGraph, gc)

      val githubChanges = gc.copy(
        addPosts = gc.addPosts.filter(p => ancestors(p.id).exists(_ == Constants.githubId)),
        delPosts = gc.delPosts.filter(pid => ancestors(pid).exists(_ == Constants.githubId)),
        updatePosts = gc.updatePosts.filter(p => ancestors(p.id).exists(_ == Constants.githubId))
      )

      // Delete
      val githubDeletePosts = githubChanges.delPosts
      val issuesToDelete: Set[PostId] = githubDeletePosts.filter(pid => prevGraph.inChildParentRelation(pid, Constants.issueTagId))
      val commentsToDelete: Set[PostId] = githubDeletePosts.filter(pid => prevGraph.inChildParentRelation(pid, Constants.commentTagId))

      val deleteIssuesCall = issuesToDelete
        .flatMap { pid =>
          val externalId = Try(pid.toString.toInt).toOption
          externalId.map(eid => DeleteIssue(owner = Constants.wustOwner,
            repo = Constants.wustRepo,
            externalNumber = eid,
            title = prevGraph.postsById(pid).content,
            content = issuePostOfDesc(prevGraph, pid).map(_.content).getOrElse(""),
            postId = pid))
        }

      val deleteCommentsCall = commentsToDelete
        .flatMap { pid =>
          val externalId = Try(pid.toString.toInt).toOption
          externalId.map(eid => DeleteComment(owner = Constants.wustOwner,
            repo = Constants.wustRepo,
            externalId = eid,
            postId = pid))
        }


      // Update
      val githubUpdatePosts = githubChanges.updatePosts
      val issuesToUpdate: Set[Post] = githubUpdatePosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.issueTagId))
      val commentsToUpdate: Set[Post] = githubUpdatePosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.commentTagId))

      val editIssuesCall = issuesToUpdate
        .flatMap { p =>
          val externalId = Try(p.id.toString.toInt).toOption
          val desc = issuePostOfDesc(currGraph, p.id).map(_.content)
          (externalId, desc).mapN((eid, d) => EditIssue(owner = Constants.wustOwner,
            repo = Constants.wustRepo,
            externalNumber = eid,
            status = "open",
            title = p.content,
            content = d,
            postId = p.id))
        }

      val editCommentsCall = commentsToUpdate
        .flatMap { p =>
          val externalId = Try(p.id.toString.toInt).toOption
          externalId.map(eid => EditComment(owner = Constants.wustOwner,
            repo = Constants.wustRepo,
            externalId = eid,
            content = p.content,
            postId = p.id))
        }


      // Add
      val githubAddPosts = githubChanges.addPosts
      val issuesToAdd: Set[Post] = githubAddPosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.issueTagId))
      val commentsToAdd: Set[Post] = githubAddPosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.commentTagId))

      val redirectCommentsToAdd: Set[Post] = githubAddPosts.filter(post => { // TODO: In this case: Push comment tag to backend!
        !currGraph.inChildParentRelation(post.id, Constants.issueTagId) &&
          currGraph.inDescendantAncestorRelation(post.id, Constants.issueTagId)
      })

      val createIssuesCall = issuesToAdd
        .map(p => CreateIssue(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          title = p.content,
          content = issuePostOfDesc(currGraph, p.id).map(_.content).getOrElse(""),
          postId = p.id
        ))

      val createCommentsCall = commentsToAdd
        .flatMap { p =>
          val issueNumber = currGraph.getParents(p.id)
            .find(pid => currGraph.inChildParentRelation(pid, Constants.issueTagId))
            .flatMap(pid => Try(pid.toString.toInt).toOption)

          issueNumber.map(in => CreateComment(owner = Constants.wustOwner,
            repo = Constants.wustRepo,
            externalIssueNumber = in, //Get issue id here
            content = p.content,
            postId = p.id))
        }

      val redirectCreateCommentsCall = redirectCommentsToAdd
        .flatMap { p =>
          val commentParents = currGraph.getParents(p.id) // TODO: Not working
          val findIssue = commentParents.find(pid => currGraph.inChildParentRelation(pid, Constants.issueTagId))
          val issueNumber = findIssue.flatMap(pid => Try(pid.toString.toInt).toOption)

          issueNumber.map( in => CreateComment(owner = Constants.wustOwner,
            repo = Constants.wustRepo,
            externalIssueNumber = in,
            content = p.content,
            postId = p.id))
        }

      val combinedCalls = (createIssuesCall ++ createCommentsCall ++ redirectCreateCommentsCall ++ editIssuesCall ++ editCommentsCall ++ deleteIssuesCall ++ deleteCommentsCall).toSeq

//      println("-" * 200)
//      println(s"Github post ancestors: $ancestors")
//      println(s"Github add posts: $githubAddPosts")
//      println(s"Github edit posts: $githubUpdatePosts")
//      println(s"Github delete posts: $githubDeletePosts")
//      println(s"Created filters: $combinedFilters")
//      println(s"Graph changes in call creation: $gc")
//      println(s"Previous graph in call creation: $prevGraph")
//      println(s"Graph in call creation: $currGraph")
//      println(s"Created calls: $combinedCalls")
//      println("-" * 200)
//      Seq.empty[GithubCall]
      combinedCalls

    }: Seq[GithubCall]

  }

  private def validRecover[T]: PartialFunction[Throwable, Either[String, T]] = { case NonFatal(t) => Left(s"Exception was thrown: $t") }
  private def valid(fut: Future[Boolean], errorMsg: String)(implicit ec: ExecutionContext) = EitherT(fut.map(Either.cond(_, (), errorMsg)).recover(validRecover))
  private def valid[T](fut: Future[T])(implicit ec: ExecutionContext) = EitherT(fut.map(Right(_) : Either[String, T]).recover(validRecover))
}


object GithubClient {
  def apply(config: GithubConfig)(implicit ec: ExecutionContext): GithubClient = {
    new GithubClient(Github(config.accessToken))
  }
}
class GithubClient(client: Github)(implicit ec: ExecutionContext) {

  import github4s.jvm.Implicits._

  case class Error(desc: String)

  def createIssue(i: CreateIssue): Future[Either[Error, Issue]] = client.issues.createIssue(i.owner, i.repo, i.title, i.content)
      .execFuture[HttpResponse[String]]()
    .map {
      case Right(GHResult(result, _, _)) => Right(result)
      case Left(e) => Left(Error(s"Could not create issue: ${e.getMessage}"))
  }

  def editIssue(i: EditIssue): Future[Either[Error, Issue]]  = client.issues.editIssue(i.owner, i.repo, i.externalNumber, i.status, i.title, i.content)
    .execFuture[HttpResponse[String]]()
    .map {
      case Right(GHResult(result, _, _)) => Right(result)
      case Left(e) => Left(Error(s"Could not edit issue: ${e.getMessage}"))
    }

  def deleteIssue(i: DeleteIssue): Future[Either[Error, Issue]]  = client.issues.editIssue(i.owner, i.repo, i.externalNumber, "closed", i.title, i.content)
    .execFuture[HttpResponse[String]]()
    .map {
      case Right(GHResult(result, _, _)) => Right(result)
      case Left(e) => Left(Error(s"Could not close issue: ${e.getMessage}"))
    }

  def createComment(c: CreateComment): Future[Either[Error, Comment]] = client.issues.createComment(c.owner, c.repo, c.externalIssueNumber, c.content)
    .execFuture[HttpResponse[String]]()
    .map {
      case Right(GHResult(result, _, _)) => Right(result)
      case Left(e) => Left(Error(s"Could not create comment: ${e.getMessage}"))
    }

  def editComment(c: EditComment): Future[Either[Error, Comment]] = client.issues.editComment(c.owner, c.repo, c.externalId, c.content)
    .execFuture[HttpResponse[String]]()
    .map {
      case Right(GHResult(result, _, _)) => Right(result)
      case Left(e) => Left(Error(s"Could not edit comment: ${e.getMessage}"))
    }

  def deleteComment(c: DeleteComment): Future[Either[Error, (PostId, Int)]] = client.issues.deleteComment(c.owner, c.repo, c.externalId)
    .execFuture[HttpResponse[String]]()
    .map {
      case Right(GHResult(result, _, _)) => Right((c.postId, c.externalId))
      case Left(e) => Left(Error(s"Could not delete comment: ${e.getMessage}"))
    }

  //      def createIssue(i: CreateIssue): Unit = println(s"received create issue of: $i")
  //      def editIssue(i: EditIssue): Unit = println(s"received edit issue of: $i")
  //      def deleteIssue(i: DeleteIssue): Unit = println(s"received delete issue of: $i")
  //      def createComment(c: CreateComment): Unit = println(s"received create comment of: $c")
  //      def editComment(c: EditComment): Unit = println(s"received edit comment of: $c")
  //      def deleteComment(c: DeleteComment): Unit = println(s"received delete comment of: $c")

  def run(receiver: MessageReceiver): Unit = {
    // TODO: Get events from github hooks
    //    private def toJson[T: Encoder](value: T): String = value.asJson.noSpaces
    //    private def fromJson[T: Decoder](value: String): Option[T] = decode[T](value).right.toOption

  }
}

case object EventCoordinator {
  // TODO: Which data structure do I want here?
  case class Buffer[T](buffer: List[T])
  case class BufferItem(githubCall: GithubCall, completedByFuture: Boolean, completedByHook: Boolean)
  case class BufferCompletion(completedByFuture: Boolean, completedByHook: Boolean)

  val bufferMap: mutable.Map[GithubCall, BufferCompletion] = mutable.Map.empty[GithubCall, BufferCompletion]

  def addHookCompletion(githubCall: GithubCall, issue: Issue): Boolean = ???

  def addFutureCompletion(githubCall: GithubCall, issue: Issue): Boolean = ???
  def addFutureCompletion(githubCall: GithubCall, comment: Comment): Boolean = ???
  def addFutureCompletion(githubCall: GithubCall, mapping: (PostId, Int)): Boolean = ???

  def addCompletion(githubCall: GithubCall, issue: Issue): Boolean = ???

  val mEventCallBuffer: mutable.Set[GithubCall] = mutable.Set.empty[GithubCall]
}

object App extends scala.App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system: ActorSystem = ActorSystem("github")

  Config.load match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      val githubClient = GithubClient(config.github)
      val redisClient = new RedisClient(config.redis.host, config.redis.port)
      val receiver = WustReceiver.run(config.wust, githubClient)
      AppServer.run(config.server, config.github, redisClient, receiver)
  }
}
