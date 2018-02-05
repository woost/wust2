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


sealed trait GithubCall
case class CreateIssue(owner: String, repo: String, title: String, content: String) extends GithubCall
case class EditIssue(owner: String, repo: String, externalNumber: Option[Int], status: String, title: String, content: String) extends GithubCall
case class DeleteIssue(owner: String, repo: String, externalNumber: Option[Int], title: String, content: String) extends GithubCall
case class CreateComment(owner: String, repo: String, externalIssueNumber: Option[Int], content: String) extends GithubCall
case class EditComment(owner: String, repo: String, externalId: Option[Int], content: String) extends GithubCall
case class DeleteComment(owner: String, repo: String, externalId: Option[Int]) extends GithubCall

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

  object GraphTransition {
    def empty: GraphTransition = new GraphTransition(Graph.empty, Seq.empty[GraphChanges], Graph.empty)
  }
  case class GraphTransition(prevGraph: Graph, changes: Seq[GraphChanges], resGraph: Graph)

  def run(config: WustConfig, github: GithubClient): Future[Result[WustReceiver]] = {
    implicit val system = ActorSystem("wust")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

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

    val githubApiCalls = graphObs.map { graphTransition =>
      filterAndSendChanges(github, graphTransition)
    }

    { // TODO: Use same execution context
      import monix.execution.Scheduler.Implicits.global

        // nice sideeffect
        println("Calling side-effect in github app")
      githubApiCalls.foreach(s => s.foreach {
        case c: CreateIssue => github.createIssue(c)
        case c: EditIssue => github.editIssue(c)
        case c: DeleteIssue => github.deleteIssue(c)
        case c: CreateComment => {
           // TODO: push comment tag to wust backend. where to put that?
          val commGC: GraphChanges = GraphChanges.empty
          client.api.changeGraph(List(commGC))
          github.createComment(c)
    }
        case c: EditComment => github.editComment(c)
        case c: DeleteComment => github.deleteComment(c)
        case _ => println("Could not match to github api call")
      })
    }

    import cats.implicits._
    val res = for { // Assume that user is logged in
//      _ <- valid(client.auth.register(config.user, config.password), "Cannot register")
      _ <- valid(client.auth.login(config.user, config.password), "Cannot login")
      changes = GraphChanges(addPosts = Set(Post(Constants.githubId, "wust-github", wustUser)))
      graph <- valid(client.api.getGraph(Page.Root))
      _ <- valid(client.api.changeGraph(List(changes)), "cannot change graph")
    } yield new WustReceiver(client)

    res.value

  }

  private def filterAndSendChanges(github: GithubClient, graphTransition: GraphTransition): Seq[GithubCall] = {


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

    graphTransition.changes.flatMap{ gc: GraphChanges =>

      println("-" * 200)
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
        .map(pid => DeleteIssue(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalNumber = Try(pid.toString.toInt).toOption,
          title = prevGraph.postsById(pid).content,
          content = issuePostOfDesc(prevGraph, pid).map(_.content).getOrElse("")
        ))

      val deleteCommentsCall = commentsToDelete
        .map(pid => DeleteComment(owner = Constants.wustOwner, repo = Constants.wustRepo, externalId = Try(pid.toString.toInt).toOption))


      // Update
      val githubUpdatePosts = githubChanges.updatePosts
      val issuesToUpdate: Set[Post] = githubUpdatePosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.issueTagId))
      val commentsToUpdate: Set[Post] = githubUpdatePosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.commentTagId))

      val editIssuesCall = issuesToUpdate
        .map(p => EditIssue(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalNumber = Try(p.id.toString.toInt).toOption,
          status = "open",
          title = p.content,
          content = issuePostOfDesc(currGraph, p.id).map(_.content).getOrElse("")
        ))

      val editCommentsCall = commentsToUpdate
        .map(p => EditComment(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalId = Try(p.id.toString.toInt).toOption,
          content = p.content))


      // Add
      val githubAddPosts = githubChanges.addPosts
      val issuesToAdd: Set[Post] = githubAddPosts.filter(post => currGraph.inChildParentRelation(post.id, Constants.issueTagId))
      val commentsToAdd: Set[Post] = githubAddPosts.filter(post => {
        println(s"add comments parents: ${currGraph.parents(post.id)}")
        currGraph.inChildParentRelation(post.id, Constants.commentTagId) ||
        (!currGraph.inChildParentRelation(post.id, Constants.issueTagId) &&
          currGraph.inDescendantAncestorRelation(post.id, Constants.issueTagId)) // TODO: In this case: Push comment tag to backend!
      })

      val createIssuesCall = issuesToAdd
        .map(p => CreateIssue(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          title = p.content,
          content = issuePostOfDesc(currGraph, p.id).map(_.content).getOrElse("")
        ))

      val createCommentsCall = commentsToAdd
        .map(p => {
          val issueNumber = currGraph.getParents(p.id) // TODO: Not working
            .find(pid => currGraph.inChildParentRelation(pid, Constants.issueTagId))
            .flatMap(pid => Try(pid.toString.toInt).toOption)
          CreateComment(owner = Constants.wustOwner,
          repo = Constants.wustRepo,
          externalIssueNumber = issueNumber, //Get issue id here
          content = p.content)})

      val combinedFilters = (issuesToAdd ++ commentsToAdd ++ issuesToUpdate ++ commentsToUpdate ++ issuesToDelete ++ commentsToDelete).toSeq
      val combinedCalls = (createIssuesCall ++ createCommentsCall ++ editIssuesCall ++ editCommentsCall ++ deleteIssuesCall ++ deleteCommentsCall).toSeq

//      combinedCalls
      println(s"Github post ancestors: $ancestors")
      println(s"Github add posts: $githubAddPosts")
      println(s"Github edit posts: $githubUpdatePosts")
      println(s"Github delete posts: $githubDeletePosts")
      println(s"Created filters: $combinedFilters")
      println(s"Graph changes in call creation: $gc")
      println(s"Previous graph in call creation: $prevGraph")
      println(s"Graph in call creation: $currGraph")
      println(s"Created calls: $combinedCalls")
      println("-" * 200)
      Seq.empty[GithubCall]

    }: Seq[GithubCall]

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

    def createIssue(i: CreateIssue): Unit = client.issues.createIssue(i.owner, i.repo, i.title, i.content)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully created issue $result")
        case Left(e) => println(s"Could not create comment: ${e.getMessage}")
      }

    def editIssue(i: EditIssue): Unit = client.issues.editIssue(i.owner, i.repo, i.externalNumber.get, i.status, i.title, i.content)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully edited issue $result")
        case Left(e) => println(s"Could not create comment: ${e.getMessage}")
      }

    def deleteIssue(i: DeleteIssue): Unit = client.issues.editIssue(i.owner, i.repo, i.externalNumber.get, "closed", i.title, i.content)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully edited issue $result")
        case Left(e) => println(s"Could not create comment: ${e.getMessage}")
      }

    def createComment(c: CreateComment): Unit = client.issues.createComment(c.owner, c.repo, c.externalIssueNumber.get, c.content)
        .execFuture[HttpResponse[String]]()
        .map {
          case Right(GHResult(result, _, _)) => println(s"Successfully created comment $result")
          case Left(e) => println(s"Could not create comment: ${e.getMessage}")
        }

    def editComment(c: EditComment): Unit = client.issues.editComment(c.owner, c.repo, c.externalId.get, c.content)
      .execFuture[HttpResponse[String]]()
      .map {
        case Right(GHResult(result, _, _)) => println(s"Successfully edited comment $result")
        case Left(e) => println(s"Could not edit comment: ${e.getMessage}")
      }

    def deleteComment(c: DeleteComment): Unit = client.issues.deleteComment(c.owner, c.repo, c.externalId.get)
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
      val client = GithubClient(Some(config.github.accessToken)) // TODO: Real option
      WustReceiver.run(config.wust, client).foreach {
        case Right(receiver) => client.run(receiver)
        case Left(err) => println(s"Cannot connect to Wust: $err")
      }
  }
}
