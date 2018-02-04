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
import monix.reactive.Observable

import scala.util.Try
import scalaj.http.HttpResponse

object Constants {
  //TODO
  val githubId: PostId = "wust-github"
  val issueTagId: PostId = "github-issue"
  val commentTagId: PostId = "github-comment"

  val wustOwner = "woost"
  val wustRepo = "bug"
}

case class WustIssue( owner: String,
                      repo: String,
                      externalNumber: Option[Int] = None,
                      status: Option[String] = None,
                      title: Option[String] = None,
                      content: Option[String] = None,
                    )

case class WustComment( owner: String,
                        repo: String,
                        externalIssueNumber: Option[Int] = None,
                        externalId: Option[Int] = None,
                        content: Option[String] = None,
                      )

trait MessageReceiver {
  type Result[T] = Future[Either[String, T]]

//  def push(issue: WustIssue, author: UserId): Result[Post]
//  def push(issue: WustComment, author: UserId): Result[Post]
}

class WustReceiver(client: WustClient)(implicit ec: ExecutionContext) extends MessageReceiver {

//  def push(issue: WustIssue, author: UserId) = {
//    println(s"new issue: ${issue.content}")
//  }
//  def push(comment: WustComment, author: UserId) = {
//    println(s"new comment: ${comment.content}")
//  }
  //  def push(msg: ExchangeMessage, author: UserId) = {
  //    println(s"new message: ${msg.content}")
  //    val post = Post(PostId.fresh, msg.content, author)
  //    val connection = Connection(post.id, Label.parent, Constants.githubId)
  //
  //    val changes = List(GraphChanges(addPosts = Set(post), addConnections = Set(connection)))
  //    client.api.changeGraph(changes).map { success =>
  //      if (success) Right(post)
  //      else Left("Failed to create post")
  //    }
  //  }
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

    println("Running WustReceiver")
    val graphEvents = handler.eventObservable.map(_.collect { case ev: ApiEvent.GraphContent => ev }).collect { case list if list.nonEmpty => list }
    val graph: Observable[Graph] = graphEvents.scan(Graph.empty) { (prevGraph, events) =>
        println(s"Got events: $events")
        val _graph = events.foldLeft(prevGraph)(EventUpdate.applyEventOnGraph)
        val changes = events collect { case ApiEvent.NewGraphChanges(_changes) => _changes }

        // nice sideeffect
        println("Calling side-effect in github app")
        filterAndSendChanges(github, _graph, changes)

        _graph
    }

    val client = AkkaWustClient(location, handler).sendWith(SendType.WhenConnected, 30 seconds)

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


    def getAncestors(graphChanges: GraphChanges): Map[PostId, Iterable[PostId]] = {

      val addAncestors = graphChanges.addPosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((p1, p2) => {
        p1 + (p2.id -> graph.ancestors(p2.id))
      })

      val updateAncestors = graphChanges.updatePosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((p1, p2) => {
        p1 + (p2.id -> graph.ancestors(p2.id))
      })

      val delAncestors = graphChanges.delPosts.foldLeft(Map.empty[PostId, Iterable[PostId]])((m, pid) => {
        m + (pid -> graph.ancestors(pid))
      })

      addAncestors ++ updateAncestors ++ delAncestors
    }

    def issuePostOfDesc(pid: PostId): Option[Post] = {
      graph.connectionsByLabel("describes")
        .find(c => c.targetId == pid)
        .map(c => graph.postsById(c.targetId))
    }

//    val ancestors = changes.foldLeft(Map.empty[PostId, Iterable[PostId]])((m, gc) => m ++ getAncestors(gc))
//    val githubChanges = changes.map(gc => gc.copy(
//      addPosts = gc.addPosts.filter(p => ancestors(p.id).exists(_ == Constants.githubId)),
//      delPosts = gc.delPosts.filter(pid => ancestors(pid).exists(_ == Constants.githubId)),
//      updatePosts = gc.updatePosts.filter(p => ancestors(p.id).exists(_ == Constants.githubId))
//    ))

    changes.foreach { gc: GraphChanges =>

      val ancestors = getAncestors(gc)

      val githubChanges = gc.copy(
        addPosts = gc.addPosts.filter(p => ancestors(p.id).exists(_ == Constants.githubId)),
        delPosts = gc.delPosts.filter(pid => ancestors(pid).exists(_ == Constants.githubId)),
        updatePosts = gc.updatePosts.filter(p => ancestors(p.id).exists(_ == Constants.githubId))
      )

      // Delete
      val githubDeletePosts = githubChanges.delPosts
      val issuesToDelete: Set[PostId] = githubDeletePosts.filter(pid => graph.inChildParentRelation(pid, Constants.issueTagId))
      val commentsToDelete: Set[PostId] = githubDeletePosts.filter(pid => graph.inChildParentRelation(pid, Constants.commentTagId))

      issuesToDelete
        .map(pid => WustIssue(owner = Constants.wustOwner, repo = Constants.wustRepo, externalNumber = Try(pid.toString.toInt).toOption))
        .foreach(i => github.deleteIssue(i))
      commentsToDelete
        .map(pid => WustComment(owner = Constants.wustOwner, repo = Constants.wustRepo, externalId = Try(pid.toString.toInt).toOption))
        .foreach(c => github.deleteComment(c))


      // Update
      val githubUpdatePosts = githubChanges.updatePosts
      val issuesToUpdate: Set[Post] = githubUpdatePosts.filter(post => graph.inChildParentRelation(post.id, Constants.issueTagId))
      val commentsToUpdate: Set[Post] = githubUpdatePosts.filter(post => graph.inChildParentRelation(post.id, Constants.commentTagId))

      issuesToUpdate
        .map(p => WustIssue(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalNumber = Try(p.id.toString.toInt).toOption,
          status = Some("open"),
          title = Some(p.content),
          content = issuePostOfDesc(p.id).map(_.content)
        ))
        .foreach(i => github.editIssue(i))
      commentsToUpdate
        .map(p => WustComment(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalId = Try(p.id.toString.toInt).toOption,
          content = Some(p.content)))
        .foreach(c => github.editComment(c))


      // Add
      val githubAddPosts = githubChanges.addPosts
      val issuesToAdd: Set[Post] = githubAddPosts.filter(post => graph.inChildParentRelation(post.id, Constants.issueTagId))
      val commentsToAdd: Set[Post] = githubAddPosts.filter(post => graph.inChildParentRelation(post.id, Constants.commentTagId))

      issuesToAdd
        .map(p => WustIssue(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          title = Some(p.content),
          content = issuePostOfDesc(p.id).map(_.content)
        ))
        .foreach(i => github.createIssue(i))

      commentsToAdd
        .map(p => {
          val issueNumber = graph.getParents(p.id)
            .find(pid => graph.inChildParentRelation(pid, Constants.issueTagId))
            .flatMap(pid => Try(pid.toString.toInt).toOption)
          WustComment(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalIssueNumber = issueNumber, //Get issue id here
          content = Some(p.content))})
        .foreach(c => github.createComment(c))
    }

  }

  private def validRecover[T]: PartialFunction[Throwable, Either[String, T]] = { case NonFatal(t) => Left(s"Exception was thrown: $t") }
  private def valid(fut: Future[Boolean], errorMsg: String)(implicit ec: ExecutionContext) = EitherT(fut.map(Either.cond(_, (), errorMsg)).recover(validRecover))
  private def valid[T](fut: Future[T])(implicit ec: ExecutionContext) = EitherT(fut.map(Right(_) : Either[String, T]).recover(validRecover))
}

class GithubClient(client: Github)(implicit ec: ExecutionContext) {

//  def send(msg: ExchangeMessage): Unit = {

    import github4s.jvm.Implicits._

//    import cats.Eval
//    def createCatsComment: Eval[Option[Comment]] = client.issues.createComment(owner, repo, issueNumber, text)
//      .exec[Eval, HttpResponse[String]]()
//      .map {
//        case Right(GHResult(result, _, _)) => Some(result)
//        case Left(e) =>
//          println(s"Could not create comment: ${e.getMessage}")
//          None
//      }

    def createIssue(i: WustIssue): Unit = client.issues.createIssue(i.owner, i.repo, i.title.get, i.content.get)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully created issue $result")
        case Left(e) => println(s"Could not create comment: ${e.getMessage}")
      }

    def editIssue(i: WustIssue): Unit = client.issues.editIssue(i.owner, i.repo, i.externalNumber.get, i.status.get, i.title.get, i.content.get)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully edited issue $result")
        case Left(e) => println(s"Could not create comment: ${e.getMessage}")
      }

    def deleteIssue(i: WustIssue): Unit = client.issues.editIssue(i.owner, i.repo, i.externalNumber.get, "closed", i.title.get, i.content.get)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully edited issue $result")
        case Left(e) => println(s"Could not create comment: ${e.getMessage}")
      }

    def createComment(c: WustComment): Unit = client.issues.createComment(c.owner, c.repo, c.externalIssueNumber.get, c.content.get)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => println(s"Successfully created comment $result")
          case Left(e) => println(s"Could not create comment: ${e.getMessage}")
        }

    def editComment(c: WustComment): Unit = client.issues.editComment(c.owner, c.repo, c.externalId.get, c.content.get)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully edited comment $result")
        case Left(e) => println(s"Could not edit comment: ${e.getMessage}")
      }

    def deleteComment(c: WustComment): Unit = client.issues.deleteComment(c.owner, c.repo, c.externalId.get)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully deleted comment $result")
        case Left(e) => println(s"Could not delete comment: ${e.getMessage}")
      }

//    createComment

//    println("Finished send")

//  }

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
