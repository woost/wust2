package backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import api._, graph._

case class User(id: Long, name: String)
case class Password(id: Long, digest: Array[Byte])

class AuthApiImpl extends AuthApi {
  def register(name: String, password: String): Future[Boolean] = {
    Db.user(name, password).map(_.isDefined)
  }
}

class ApiImpl(userOpt: Option[User], emit: ApiEvent => Unit) extends Api {
  import util.Pipe

  private def withUser[T](f: User => Future[T]): Future[T] = userOpt.map(f).getOrElse {
    Future.failed(UserError(Unauthorized))
  }

  private def withUser[T](f: => Future[T]): Future[T] = withUser(_ => f)

  def getPost(id: PostId): Future[Option[Post]] = Db.post.get(id)

  def addPost(msg: String): Future[Post] = withUser {
    Db.post(msg) ||> (_.foreach(NewPost(_) |> emit))
  }

  def updatePost(post: Post): Future[Boolean] = withUser {
    Db.post.update(post) ||> (_.foreach(if (_) UpdatedPost(post) |> emit))
  }

  def deletePost(id: PostId): Future[Boolean] = withUser {
    Db.post.delete(id) ||> (_.foreach(if (_) DeletePost(id) |> emit))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Option[Connects]] = withUser {
    Db.connects(sourceId, targetId) ||> (_.foreach(_.foreach(NewConnection(_) |> emit)))
  }

  def deleteConnection(id: ConnectsId): Future[Boolean] = withUser {
    Db.connects.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emit))
  }

  def contain(childId: PostId, parentId: PostId): Future[Option[Contains]] = withUser {
    Db.contains(childId, parentId) ||> (_.foreach(_.foreach(NewContainment(_) |> emit)))
  }

  def deleteContainment(id: ContainsId): Future[Boolean] = withUser {
    Db.contains.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emit))
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  def respond(to: PostId, msg: String): Future[Option[(Post, Connects)]] = withUser {
    //TODO do in one request, does currently not handle errors, then no get
    for {
      post <- addPost(msg)
      edge <- connect(post.id, to)
    } yield edge.map((post, _))
  }

  def getGraph(): Future[Graph] = Db.graph.get()
}
