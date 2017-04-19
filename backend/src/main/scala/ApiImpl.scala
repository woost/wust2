package wust.backend

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import wust.util.Pipe
import wust.api._
import wust.graph._
import auth._

class ApiImpl(apiAuth: AuthenticatedAccess) extends Api {
  import Server.emit, apiAuth._

  private def ownerGroup(user: User, isPrivate: Boolean) =
    if (isPrivate) Db.user.group(user).map(Option.apply) else Future.successful(None)

  def getPost(id: PostId): Future[Option[Post]] = Db.post.get(id)

  def addPost(msg: String, selection: GraphSelection, groupId: Long): Future[Post] = withUserOrImplicit { user =>
    //TODO: check if user is allowed to create post in group
    Db.post(msg, groupId) ||> (_.foreach { post =>
      NewPost(post) |> emit
      selection match {
        case GraphSelection.Union(parentIds) =>
          parentIds.foreach(contain(_, post.id))
        case _ =>
      }
    })
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to update post
    Db.post.update(post) ||> (_.foreach(if (_) UpdatedPost(post) |> emit))
  }

  def deletePost(id: PostId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete post
    Db.post.delete(id) ||> (_.foreach(if (_) DeletePost(id) |> emit))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects] = withUserOrImplicit {
    Db.connects(sourceId, targetId) ||> (_.foreach(NewConnection(_) |> emit))
  }

  def deleteConnection(id: ConnectsId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete connection
    Db.connects.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emit))
  }

  def contain(parentId: PostId, childId: PostId): Future[Contains] = withUserOrImplicit {
    Db.contains(parentId, childId) ||> (_.foreach(NewContainment(_) |> emit))
  }

  def deleteContainment(id: ContainsId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete containment
    Db.contains.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emit))
  }

  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: Long): Future[(Post, Connects)] = withUserOrImplicit { user =>
    //TODO: check if user is allowed to create post in group
    Db.connects.newPost(msg, to, groupId) ||> (_.foreach {
      case (post, connects) =>
        NewPost(post) |> emit
        NewConnection(connects) |> emit
        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(contain(_, post.id))
          case _ =>
        }
    })
  }

  def getUser(id: Long): Future[Option[User]] = Db.user.get(id)
  def getUserGroups(id: Long): Future[Seq[UserGroup]] = Db.user.allGroups(id)

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  def getGraph(selection: GraphSelection): Future[Graph] = withUserOpt { uOpt =>
    selection match {
      case GraphSelection.Root => Db.graph.getAllVisiblePosts(uOpt.map(_.id))
      case GraphSelection.Union(parentIds) => Db.graph.getUnion(uOpt.map(_.id), parentIds)
    }
  }
}
