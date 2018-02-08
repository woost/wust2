package wust.github

import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import mycelium.client.SendType
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import cats.data.EitherT
import cats.implicits._
import com.typesafe.config.ConfigFactory
import io.circe._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import scala.collection.mutable
import github4s.Github
import github4s.Github._
import github4s.GithubResponses.{GHException, GHResult}
import github4s.free.domain.{Comment, Issue, User => GHUser}
import github4s.jvm.Implicits._
import monix.execution.Scheduler
import monix.reactive.Observable

import scala.util.{Failure, Success, Try}
import scalaj.http.HttpResponse

object Constants {
  //TODO
  val githubId: PostId = "wust-github"
  val issueTagId: PostId = "github-issue"
  val commentTagId: PostId = "github-comment"

  val wustOwner = "woost"
  val wustRepo = "bug"
}

object WebhookServer {
  import akka.http.scaladsl.server.RouteResult._
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.Http
  import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._

  def run(config: GithubConfig, wustReceiver: WustReceiver)(implicit system: ActorSystem) = {
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    import io.circe.generic.auto._

    case class IssueEvent(action: String, issue: Issue)
    case class IssueCommentEvent(action: String, issue: Issue, comment: Comment)
    val route = {
      path(config.path) {
        post {
          decodeRequest {
            headerValueByName("X-GitHub-Event") {
              case "issues" => entity(as[IssueEvent]) { issue =>
                issue.action match {
                  case "created" => wustReceiver.push(List(GraphChanges.empty))
                  case "edited" => scribe.info("Received Webhook: edited issue"); wustReceiver.push(List(GraphChanges.empty))
                  case "deleted" => wustReceiver.push(List(GraphChanges.empty))
                  case a => scribe.error(s"Received unknown IssueEvent action: $a")
                }
                complete(StatusCodes.Success)
              }
              case "issue_comment" => entity(as[IssueCommentEvent]) {comment =>
                comment.action match {
                  case "created" => scribe.info("Received Webhook: created Comment"); wustReceiver.push(List(GraphChanges.empty))
                  case "edited" => wustReceiver.push(List(GraphChanges.empty))
                  case "deleted" => wustReceiver.push(List(GraphChanges.empty))
                  case a => scribe.error(s"Received unknown IssueCommentEvent: $a")
                }
                complete(StatusCodes.Success)
              }
              case "ping" => scribe.info("Received ping"); complete(StatusCodes.Accepted)
              case e => scribe.error(s"Received unknown GitHub Event Header: $e"); complete(StatusCodes.Accepted)
            }
          }
        }
      }
    }

    Http().bindAndHandle(route, interface = config.host, port = config.port).onComplete {
      case Success(binding) => {
        val separator = "\n" + ("#" * 50)
        val readyMsg = s"\n##### GitHub App Server online at ${binding.localAddress} #####"
        scribe.info(s"$separator$readyMsg$separator")
      }
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

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(graphChanges: List[GraphChanges]): Future[Either[String, List[GraphChanges]]] = {
    scribe.info(s"pushing new graph change: $graphChanges")
    client.api.changeGraph(graphChanges).map{ success =>
      if(success) Right(graphChanges)
      else Left("Failed to create post")
    }
  }
}

case class Buffer[T](buffer: List[T])

object WustReceiver {
  type Result[T] = Either[String, T]

  val wustUser = UserId("wust-github")
  val mCallBuffer: mutable.Set[GithubCall] = mutable.Set.empty[GithubCall]

  object GraphTransition {
    def empty: GraphTransition = new GraphTransition(Graph.empty, Seq.empty[GraphChanges], Graph.empty)
  }
  case class GraphTransition(prevGraph: Graph, changes: Seq[GraphChanges], resGraph: Graph)

  def run(config: WustConfig, github: GithubClient)(implicit system: ActorSystem): Future[Result[WustReceiver]] = {
    implicit val materializer = ActorMaterializer()
    implicit val scheduler = Scheduler(system.dispatcher)

    val location = s"ws://${config.host}:${config.port}/ws"

    val handler = new WustIncidentHandler {
      override def onConnect(): Unit = println(s"GitHub App - Connected to websocket")
    }

    val client = AkkaWustClient(location, handler).sendWith(SendType.WhenConnected, 30 seconds)

    println("Running WustReceiver")

    val graphEvents: Observable[Seq[ApiEvent.GraphContent]] = handler.eventObservable
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
    val githubResult = githubApiCalls.foreach(_.foreach {
      case c: CreateIssue =>
        mCallBuffer += c
        github.createIssue(c)
      case c: EditIssue =>
        mCallBuffer += c
        github.editIssue(c)
      case c: DeleteIssue =>
        mCallBuffer += c
        github.deleteIssue(c)
      case c: CreateComment =>
        mCallBuffer += c
        github.createComment(c).foreach { comment =>
          val tag = Connection(c.postId, Label.parent, Constants.commentTagId)
          println(s"Sending add comment tag: $tag")
          valid(client.api.changeGraph(List(GraphChanges(addConnections = Set(tag)))), "Could not redirect comment to add tag")
        }
      case c: EditComment =>
        mCallBuffer += c
        github.editComment(c)
      case c: DeleteComment =>
        mCallBuffer += c
        github.deleteComment(c)

      case _ => println("Could not match to github api call")
    })

    import cats.implicits._
    valid(client.auth.register(config.user, config.password), "Cannot register")
    val res = for { // Assume that user is logged in
      _ <- valid(client.auth.login(config.user, config.password), "Cannot login")
//      changes = GraphChanges(addPosts = Set(Post(Constants.githubId, "wust-github", wustUser)))
//      graph <- valid(client.api.getGraph(Page.Root))
//      _ <- valid(client.api.changeGraph(List(changes)), "cannot change graph")
    } yield new WustReceiver(client)

    res.value

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
      graph.connectionsByLabel("describes")
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

object GithubClient {
  def apply(accessToken: Option[String])(implicit ec: ExecutionContext, system: ActorSystem): GithubClient = {

    import github4s.jvm.Implicits._
    val user = Github(accessToken).users.get("GRBurst")
    val userF = user.execFuture[HttpResponse[String]]()

    val res = userF.map {
      case Right(GHResult(user: GHUser, status, headers)) => user.login //.id
      case Left(e) => e.getMessage
    }

    new GithubClient(Github(accessToken))
  }
}

object App extends scala.App {
  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val system = ActorSystem("github")

  Config.load match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      import WebhookServer._
      val client = GithubClient(config.github.accessToken) // TODO: Real option
      WustReceiver.run(config.wust, client).foreach {
        case Right(receiver) => WebhookServer.run(config.github, receiver)
        case Left(err) => println(s"Cannot connect to Wust: $err")
      }
  }
}
