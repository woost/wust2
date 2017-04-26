package wust.backend

import wust.db
import wust.ids._
import wust.api._
import wust.backend.auth._
import wust.graph._
import wust.util.Pipe
import dbConversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ApiImpl(apiAuth: AuthenticatedAccess) extends Api {
  import Server.{emit, emitDynamic}
  import apiAuth._

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient))

  //TODO: return Future[Boolean]
  def addPost(
    msg: String,
    selection: GraphSelection,
    groupId: GroupId
  ): Future[Post] = withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (db.post(msg, groupId) ||> (_.foreach {
      case (post, ownership) =>
        NewPost(forClient(post)) |> emitDynamic
        NewOwnership(forClient(ownership)) |> emitDynamic

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(contain(_, post.id))
          case _ =>
        }
    })) map { case (post, _) => forClient(post) }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to update post
    db.post.update(post) ||> (_.foreach(if (_) UpdatedPost(forClient(post)) |> emitDynamic))
  }

  def deletePost(id: PostId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete post
    db.post.delete(id) ||> (_.foreach(if (_) DeletePost(id) |> emitDynamic))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connects] = withUserOrImplicit {
    db.connects(sourceId, targetId).map(forClient) ||> (_.foreach(NewConnection(_) |> emitDynamic))
  }

  def deleteConnection(id: ConnectsId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete connection
    db.connects.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emitDynamic))
  }

  def contain(parentId: PostId, childId: PostId): Future[Contains] = withUserOrImplicit {
    db.contains(parentId, childId).map(forClient) ||> (_.foreach(NewContainment(_) |> emitDynamic))
  }

  def deleteContainment(id: ContainsId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete containment
    db.contains.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emitDynamic))
  }

  //TODO: return Future[Boolean]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: GroupId): Future[(Post, Connects)] = withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (db.connects.newPost(msg, to, groupId) ||> (_.foreach {
      case (post, connects, ownership) =>
        NewPost(post) |> emitDynamic
        NewConnection(connects) |> emitDynamic
        NewOwnership(ownership) |> emitDynamic

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(contain(_, post.id))
          case _ =>
        }
    })).map { case (post, connects, _) => (forClient(post), forClient(connects)) }
  }

  def getUser(id: UserId): Future[Option[User]] = db.user.get(id).map(_.map(forClient))
  def addUserGroup(): Future[Group] = withUserOrImplicit { user =>
    val createdGroup = db.user.createUserGroupForUser(user.id)
    createdGroup.map {
      case (group, usergroupmember) =>
        emit(ChannelEvent(Channel.User(user.id), NewGroup(Group(group.id))))
        emit(ChannelEvent(Channel.User(user.id), NewMembership(Membership(group.id, user.id))))

        Group(group.id)
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUserOrImplicit { user =>
    //TODO this should be handled in the query, just return false in db.user.addMember
    //TODO NEVER use bare exception! we have an exception for apierrors: UserError(something)
    if (groupId == 1L) Future.failed(new Exception("adding members to public group is not allowed"))

    else {
      //TODO: check if user has access to group
      val createdMembership = db.user.addMember(groupId, userId)
      createdMembership.map { membership =>
        emit(ChannelEvent(Channel.User(groupId), NewMembership(Membership(membership.groupId, membership.userId.get))))

        true
      }
    }
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  def getUnion(userIdOpt: Option[UserId], parentIds: Set[PostId]): Future[Graph] = {
    //TODO: in stored procedure
    db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
      graph -- graph.postsById.keys.filterNot(transitiveChildren)
    }
  }

  def getGraph(selection: GraphSelection): Future[Graph] = withUserOpt {
    uOpt =>
      selection match {
        case GraphSelection.Root =>
          db.graph.getAllVisiblePosts(uOpt.map(_.id)).map(forClient(_).consistent) // TODO: consistent should not be necessary here
        case GraphSelection.Union(parentIds) =>
          getUnion(uOpt.map(_.id), parentIds).map(_.consistent) // TODO: consistent should not be necessary here
      }

  }
}
