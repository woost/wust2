package backend

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import api._, graph._
import boopickle.Default._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.model._

import pharg._

import framework._

case class User(name: String)

object UnauthorizedException extends UserViewableException("unauthorized")
object WrongCredentials extends UserViewableException("wrong credentials")

object Model {
  val users = User("hans") ::
              User("admin") ::
              Nil

  var counter = 0

  var graph = DirectedGraphData[Id, Post, Connects](Set.empty, Set.empty, Map.empty, Map.empty)
  private var currentId: Id = 0
  def nextId() = {
    val r = currentId
    currentId += 1
    r
  }
}

class ApiImpl(userOpt: Option[User], emit: ApiEvent => Unit) extends Api {
  import Model._

  def withUser[T](f: User => T) = userOpt.map(f).getOrElse {
    throw UnauthorizedException
  }

  def whoami() = withUser(_.name)

  def change(delta: Int) = withUser { user =>
    counter += delta
    emit(NewCounterValue(user.name, counter))
    counter
  }

  def getPost(id: Id): Post = graph.vertexData(id)
  def getGraph(): Graph = graph
  def addPost(msg: String): (Id, Post) = {
    //uns fehlt die id im client
    val id = nextId()
    val post = new Post(msg)
    graph = graph.copy(
      vertices = graph.vertices + id,
      vertexData = graph.vertexData + (id -> post)
    )
    emit(NewPost(id, post))
    (id, post)
  }
  def connect(from: Id, to: Id): (Edge[Id], Connects) = {
    val connects = Connects("responds")
    val edge = Edge(from, to)
    graph = graph.copy(
      edges = graph.edges + edge,
      edgeData = graph.edgeData + (edge -> connects)
    )
    emit(NewConnects(edge, connects))
    (edge, connects)
  }
  def getComponent(id: Id): Graph = {
    graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  }
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
