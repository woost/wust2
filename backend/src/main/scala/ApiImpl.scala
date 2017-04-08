package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.util.Pipe
import wust.api._
import wust.graph._
import auth.JWT

class ApiImpl(apiAuth: ApiAuthentication) extends Api {
  import Server.emit, apiAuth._

  private def ownerGroup(user: User, isPrivate: Boolean) =
    if (isPrivate) Db.user.group(user).map(Option.apply) else Future.successful(None)

  def getPost(id: PostId): Future[Option[Post]] = Db.post.get(id)

  def addPost(msg: String, isPrivate: Boolean): Future[Post] = withUserOrAnon { user =>
    ownerGroup(user, isPrivate).flatMap { group =>
      Db.post(msg, group) ||> (_.foreach(NewPost(_) |> emit))
    }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrAnon {
    Db.post.update(post) ||> (_.foreach(if (_) UpdatedPost(post) |> emit))
  }

  def deletePost(id: PostId): Future[Boolean] = withUserOrAnon {
    Db.post.delete(id) ||> (_.foreach(if (_) DeletePost(id) |> emit))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects] = withUserOrAnon {
    Db.connects(sourceId, targetId) ||> (_.foreach(NewConnection(_) |> emit))
  }

  def deleteConnection(id: ConnectsId): Future[Boolean] = withUserOrAnon {
    Db.connects.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emit))
  }

  def contain(parentId: PostId, childId: PostId): Future[Contains] = withUserOrAnon {
    Db.contains(parentId, childId) ||> (_.foreach(NewContainment(_) |> emit))
  }

  def deleteContainment(id: ContainsId): Future[Boolean] = withUserOrAnon {
    Db.contains.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emit))
  }

  //TODO allow choosing any usergroup
  def respond(to: PostId, msg: String, isPrivate: Boolean): Future[(Post, Connects)] = withUserOrAnon { user =>
    ownerGroup(user, isPrivate).flatMap { group =>
      Db.connects.newPost(msg, to, group) ||> (_.foreach {
        case (post, connects) =>
          NewPost(post) |> emit
          NewConnection(connects) |> emit
      })
    }
  }

  def getUser(id: Long): Future[Option[User]] = Db.user.get(id)

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  def getGraph(): Future[Graph] = withUserOpt { userOpt =>
    Db.graph.get(userOpt)
  }
}
