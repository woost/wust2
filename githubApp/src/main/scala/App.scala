package wust.github

import wust.sdk._
import wust.api._
import wust.ids._
import wust.graph._
import mycelium.client.{IncidentHandler, SendType}
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.EitherT
import cats.implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import github4s.Github
import github4s.Github._
import github4s.GithubResponses.GHResult
import github4s.free.domain.{Comment, Issue, User => GHUser}
import github4s.jvm.Implicits._

import scalaj.http.HttpResponse

object Constants {
  //TODO
  val githubId: PostId = "wust-github"
  val issueTagId: PostId = "github-issue"
  val commentTagId: PostId = "github-comment"
}

case class ExchangeMessage(content: String)

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

  def push(msg: ExchangeMessage, author: UserId): Result[Post]
}

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

  def push(msg: ExchangeMessage, author: UserId) = {
    println(s"new message: ${msg.content}")
    val post = Post(PostId.fresh, msg.content, author)
    val connection = Connection(post.id, Label.parent, Constants.githubId)

    val changes = List(GraphChanges(addPosts = Set(post), addConnections = Set(connection)))
    client.api.changeGraph(changes).map { success =>
      if (success) Right(post)
      else Left("Failed to create post")
    }
  }
}

object WustReceiver {
  type Result[T] = Either[String, T]

  val wustUser = UserId("wust-github")

  def run(config: WustConfig, github: GithubClient): Future[Result[WustReceiver]] = {
    implicit val system = ActorSystem("wust")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    //    val client = AkkaWustClient(location, handler).sendWith(SendType.NowOrFail, 30 seconds)

    val location = s"ws://${config.host}:${config.port}/ws"

    val handler = new WustIncidentHandler {
      override def onConnect(): Unit = println(s"GitHub App - Connected to websocket")
    }

    val graphEvents = handler.eventObservable.map(_.collect { case ev: ApiEvent.GraphContent => ev }).collect { case list if list.nonEmpty => list }
    val graph = graphEvents.scan(Graph.empty) { (prevGraph, events) =>
        println(s"Got events: $events")
        val graph = events.foldLeft(prevGraph)(EventUpdate.applyEventOnGraph)
        val changes = events collect { case ApiEvent.NewGraphChanges(_changes) => _changes }

        // nice sideeffect
        filterAndSendChanges(github, graph, changes)

        graph
    }

    val client = AkkaWustClient(location, handler).sendWith(SendType.WhenConnected, 30 seconds)

//    def getGraph: Future[Graph] = valid(client.api.getGraph(Page.Root)).value.map{
//      case Right(graph) => graph
//      case Left(_) => Graph.empty
//    }

    import cats.implicits._
    val res = for {
      _ <- valid(client.auth.register(config.user, config.password), "Cannot register")
      _ <- valid(client.auth.login(config.user, config.password), "Cannot login")
      changes = GraphChanges(addPosts = Set(Post(Constants.githubId, "wust-github", wustUser)))
      graph <- valid(client.api.getGraph(Page.Root))
      _ <- valid(client.api.changeGraph(List(changes)), "cannot change graph")
    } yield new WustReceiver(client)

    res.value

  }

  private def filterAndSendChanges(github: GithubClient, graph: Graph, changes: Seq[GraphChanges]): Unit = {
      changes.foreach { gc =>

        val ancestors: Map[PostId, Iterable[PostId]] = gc.addPosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((p1, p2) => {
          p1 + (p2.id -> graph.ancestors(p2.id))
        })

        val githubPosts = gc.addPosts.filter({ post =>
          val ancestors = graph.ancestors(post.id)
          ancestors.exists(_ == Constants.githubId)
        })

        val issues: Set[Post] = githubPosts.filter(post => {
          val postAncestors = ancestors(post.id)
          postAncestors.exists(_ == Constants.issueTagId)
        })

        val comments: Set[Post] = githubPosts.filter(post => {
          val postAncestors = ancestors(post.id)
          postAncestors.exists(_ == Constants.commentTagId)
        })

        issues.map(p => ExchangeMessage(p.content)).foreach {
          msg => github.send(msg)
        }

        comments.map(p => ExchangeMessage(p.content)).foreach {
          msg => github.send(msg)
        }
      }

  }

  private def validRecover[T]: PartialFunction[Throwable, Either[String, T]] = { case NonFatal(t) => Left(s"Exception was thrown: $t") }
  private def valid(fut: Future[Boolean], errorMsg: String)(implicit ec: ExecutionContext) = EitherT(fut.map(Either.cond(_, (), errorMsg)).recover(validRecover))
  private def valid[T](fut: Future[T])(implicit ec: ExecutionContext) = EitherT(fut.map(Right(_) : Either[String, T]).recover(validRecover))
}

class GithubClient(client: Github)(implicit ec: ExecutionContext) {

  def send(msg: ExchangeMessage): Unit = {

    import github4s.jvm.Implicits._
    val owner = "woost"
    val repo = "bug"
    val issueTitle = "wust issue"
    val issueNumber = 52
    val issueState = "open"
    val commentId = 1
    val text = msg.content

    import cats.Eval
    def createCatsComment: Eval[Option[Comment]] = client.issues.createComment(owner, repo, issueNumber, text)
      .exec[Eval, HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => Some(result)
        case Left(e) =>
          println(s"Could not create comment: ${e.getMessage}")
          None
      }

    def createIssue: Future[Option[Issue]] = client.issues.createIssue(owner, repo, issueTitle, text)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => Some(result)
        case Left(e) =>
          println(s"Could not create comment: ${e.getMessage}")
          None
      }

    def editIssue: Future[Option[Issue]] = client.issues.editIssue(owner, repo, issueNumber, issueState, issueTitle, text)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => Some(result)
        case Left(e) =>
          println(s"Could not create comment: ${e.getMessage}")
          None
      }

    def createComment: Future[Option[Comment]] = client.issues.createComment(owner, repo, issueNumber, text)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => Some(result)
          case Left(e) =>
            println(s"Could not create comment: ${e.getMessage}")
            None
        }

    def editComment: Future[Option[Comment]] = client.issues.editComment(owner, repo, commentId, text)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => Some(result)
        case Left(e) =>
          println(s"Could not edit comment: ${e.getMessage}")
          None
      }

    def deleteComment(): Unit = client.issues.deleteComment(owner, repo, commentId)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println("Successfully deleted comment")
        case Left(e) => println(s"Could not delete comment: ${e.getMessage}")
      }

    createComment

    println("Finished send")

  }

  def run(receiver: MessageReceiver): Unit = {
//    val roomMessagesChannel: RoomMessagesChannel = new RoomMessagesChannel(roomId) {
//      override def onMessage(channel: String, e: MessageEvent): Unit = {
//        println(s"Got message from '${e.message.fromUser}' in channel '${channel}': ${e.message.text}")
//
//        val message = ExchangeMessage(e.message.text)
//        receiver.push(message, WustReceiver.wustUser) foreach {
//          case Left(error) => println(s"Failed to sync with wust: $error")
//          case Right(post) => println(s"Created post: $post")
//        }
//      }
//    }

//    streamClient.connect(new ConnectionListener {
//      override def onConnected(): Unit = {
//        streamClient.subscribe(roomMessagesChannel)
//      }
//    })
  }
}

object GithubClient {
  def apply(accessToken: Option[String])(implicit ec: ExecutionContext): GithubClient = {
    implicit val system = ActorSystem("github")

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

  Config.load match {
    case Left(err) => println(s"Cannot load config: $err")
    case Right(config) =>
      val client = GithubClient(Some(config.accessToken)) // TODO: Real option
      WustReceiver.run(config.wust, client).foreach {
        case Right(receiver) => client.run(receiver)
        case Left(err) => println(s"Cannot connect to Wust: $err")
      }
  }
}
