package backend

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import api._, graph._
import boopickle.Default._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._

import framework._

case class User(name: String)

object UnauthorizedException extends UserViewableException("unauthorized")
object WrongCredentials extends UserViewableException("wrong credentials")

object Model {
  val users = User("hans") ::
    User("admin") ::
    Nil

  // TODO: the next id will come from the database
  private var currentId: AtomId = 0
  def nextId() = {
    val r = currentId
    currentId += 1
    r
  }
  val post1 = Post(nextId(), "Hallo")
  val post2 = Post(nextId(), "Ballo")
  val responds1 = RespondsTo(nextId(), post2.id, post1.id)
  val post3 = Post(nextId(), "Penos")
  val responds2 = RespondsTo(nextId(), post3.id, responds1.id)
  val post4 = Post(nextId(), "Wost")
  val responds3 = RespondsTo(nextId(), post4.id, responds2.id)
  var graph = Graph(Map(post1.id -> post1, post2.id -> post2, post3.id -> post3, post4.id -> post4), Map(responds1.id -> responds1, responds2.id -> responds2, responds3.id -> responds3))
  // var graph = Graph(Map(post1.id -> post1, post2.id -> post2), Map(responds1.id -> responds1))
}

class ApiImpl(userOpt: Option[User], emit: ApiEvent => Unit) extends Api {
  import Model._

  def withUser[T](f: User => T) = userOpt.map(f).getOrElse {
    throw UnauthorizedException
  }

  def whoami() = withUser(_.name)

  def getPost(id: AtomId): Post = graph.posts(id)
  def getGraph(): Graph = graph
  def addPost(msg: String): Post = {
    //uns fehlt die id im client
    val post = new Post(nextId(), msg)
    graph = graph.copy(
      posts = graph.posts + (post.id -> post)
    )
    emit(NewPost(post))
    post
  }
  def connect(fromId: AtomId, toId: AtomId): RespondsTo = {
    val existing = graph.respondsTos.values.find(r => r.in == fromId && r.out == toId)
    val edge = existing.getOrElse(RespondsTo(nextId(), fromId, toId))
    graph = graph.copy(
      respondsTos = graph.respondsTos + (edge.id -> edge)
    )
    emit(NewRespondsTo(edge))
    edge
  }
  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
}

object TypePicklers {
  implicit val channelPickler = implicitly[Pickler[Channel]]
  implicit val eventPickler = implicitly[Pickler[ApiEvent]]
  implicit val authPickler = implicitly[Pickler[Authorize]]
}
import TypePicklers._

object Server extends WebsocketServer[Channel, ApiEvent, Authorize, User] with App {
  // val router = wire.route[Api](user => new ApiImpl(user, emit))
  def router = user => wire.route[Api](new ApiImpl(user, emit))

  def authorize(auth: Authorize): Future[User] = auth match {
    case PasswordAuth(name, pw) =>
      Model.users.find(u => u.name == name)
        .map(Future.successful)
        .getOrElse(Future.failed(WrongCredentials))
  }

  def emit(event: ApiEvent): Unit = emit(Channel.fromEvent(event), event)

  val route = pathSingleSlash {
    getFromResource("index-dev.html")
  } ~ pathPrefix("assets") {
    //TODO from resource
    getFromDirectory("../frontend/target/scala-2.11/")
  }

  run("localhost", 8080) foreach { binding =>
    println(s"Server online at ${binding.localAddress}")
  }
}
