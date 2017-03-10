package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.util.Pipe
import wust.api._
import wust.graph._
import auth.JWT

class ApiImpl(authentication: Option[Authentication]) extends Api {
  import Server.emit

  private def userOpt = authentication.filterNot(JWT.isExpired).map(_.user)

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

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects] = withUser {
    Db.connects(sourceId, targetId) ||> (_.foreach(NewConnection(_) |> emit))
  }

  def deleteConnection(id: ConnectsId): Future[Boolean] = withUser {
    Db.connects.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emit))
  }

  def contain(childId: PostId, parentId: PostId): Future[Contains] = withUser {
    Db.contains(childId, parentId) ||> (_.foreach(NewContainment(_) |> emit))
  }

  def deleteContainment(id: ContainsId): Future[Boolean] = withUser {
    Db.contains.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emit))
  }

  def respond(to: PostId, msg: String): Future[(Post, Connects)] = withUser {
    Db.connects.newPost(msg, to) ||> (_.foreach { case (post, connects) =>
      NewPost(post) |> emit
      NewConnection(connects) |> emit
    })
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  def getGraph(): Future[Graph] = Db.graph.get()
}
