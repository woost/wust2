package wust.backend

import wust.api._
import wust.backend.auth._
import wust.graph._
import wust.util.Pipe

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApiImpl(apiAuth: AuthenticatedAccess) extends Api {
  import Server.{emit, emitDynamic}
  import apiAuth._

  def getPost(id: PostId): Future[Option[Post]] = Db.post.get(id)

  //TODO: return Future[Boolean]
  def addPost(msg: String,
              selection: GraphSelection,
              groupId: Long): Future[Post] = withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (Db.post(msg, groupId) ||> (_.foreach {
      case (post, ownership) =>
        NewPost(post) |> emitDynamic
        NewOwnership(ownership) |> emitDynamic

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(contain(_, post.id))
          case _ =>
        }
    })) map { case (post, _) => post }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to update post
    Db.post.update(post) ||> (_.foreach(if (_) UpdatedPost(post) |> emitDynamic))
  }

  def deletePost(id: PostId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete post
    Db.post.delete(id) ||> (_.foreach(if (_) DeletePost(id) |> emitDynamic))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects] = withUserOrImplicit {
    Db.connects(sourceId, targetId) ||> (_.foreach(NewConnection(_) |> emitDynamic))
  }

  def deleteConnection(id: ConnectsId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete connection
    Db.connects.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emitDynamic))
  }

  def contain(parentId: PostId, childId: PostId): Future[Contains] = withUserOrImplicit {
    Db.contains(parentId, childId) ||> (_.foreach(NewContainment(_) |> emitDynamic))
  }

  def deleteContainment(id: ContainsId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete containment
    Db.contains.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emitDynamic))
  }

  //TODO: return Future[Boolean]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: Long): Future[(Post, Connects)] = withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (Db.connects.newPost(msg, to, groupId) ||> (_.foreach {
      case (post, connects, ownership) =>
        NewPost(post) |> emitDynamic
        NewConnection(connects) |> emitDynamic
        NewOwnership(ownership) |> emitDynamic

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(contain(_, post.id))
          case _ =>
        }
    })).map { case (post, connects, _) => (post, connects) }
  }

  def getUser(id: Long): Future[Option[User]] = Db.user.get(id)
  def getUserGroups(id: Long): Future[Seq[UserGroup]] = Db.user.allGroups(id)
  def addUserGroup(): Future[UserGroup] = withUserOrImplicit { user =>
    val createdGroup = Db.user.createUserGroupForUser(user.id)
    createdGroup.foreach { _ =>
      Db.user.allGroups(user.id)
        .map(groups => ChannelEvent(Channel.User(user.id), ReplaceUserGroups(groups)))
        .foreach(emit)
    }
    createdGroup.map(group => UserGroup(group.id, Seq(user.toClientUser)))
  }
  def addMember(groupId: Long, userId: Long): Future[Boolean] = withUserOrImplicit { user =>
    //TODO this should be handled in the query, just return false in Db.user.addMember
    //TODO NEVER use bare exception! we have an exception for apierrors: UserError(something)
    if (groupId == 1L) Future.failed(new Exception("adding members to public group is not allowed"))
    else {
      //TODO: check if user has access to group
      val success = Db.user.addMember(groupId, userId).map(_ => true)
      success.filter(identity).foreach { _ =>
        Db.user.allGroups(user.id)
          .map(groups => ChannelEvent(Channel.User(user.id), ReplaceUserGroups(groups)))
          .foreach(emit)
      }
      success
    }
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }

  def getGraph(selection: GraphSelection): Future[Graph] = withUserOpt {
    uOpt =>
      selection match {
        case GraphSelection.Root => Db.graph.getAllVisiblePosts(uOpt.map(_.id))
        case GraphSelection.Union(parentIds) =>
          Db.graph.getUnion(uOpt.map(_.id), parentIds)
      }
  }
}
