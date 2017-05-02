package wust.backend

import wust.db
import wust.ids._
import wust.api._
import wust.backend.auth._
import wust.backend.config.Config
import wust.graph._
import wust.util.Pipe
import dbConversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object RandomUtil {
  import java.math.BigInteger
  import java.security.SecureRandom

  private val random = new SecureRandom()

  def alphanumeric(nrChars: Int = 24): String = {
    new BigInteger(nrChars * 5, random).toString(32)
  }
}

class ApiImpl(apiAuth: AuthenticatedAccess) extends Api {
  import Config.usergroup.{ publicId => publicGroupId }
  import Server.{ emit, emitDynamic }
  import apiAuth._

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient))

  //TODO: return Future[Boolean]
  def addPost(
    msg: String,
    selection: GraphSelection,
    groupId: Option[GroupId]): Future[Post] = withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (db.post(msg, groupId.getOrElse(publicGroupId)) ||> (_.foreach {
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

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connection] = withUserOrImplicit {
    db.connection(sourceId, targetId).map(forClient) ||> (_.foreach(NewConnection(_) |> emitDynamic))
  }

  def deleteConnection(id: ConnectionId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete connection
    db.connection.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emitDynamic))
  }

  def contain(parentId: PostId, childId: PostId): Future[Containment] = withUserOrImplicit {
    db.containment(parentId, childId).map(forClient) ||> (_.foreach(NewContainment(_) |> emitDynamic))
  }

  def deleteContainment(id: ContainmentId): Future[Boolean] = withUserOrImplicit {
    //TODO: check if user is allowed to delete containment
    db.containment.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emitDynamic))
  }

  //TODO: return Future[Boolean]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupId: Option[GroupId]): Future[(Post, Connection)] = withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (db.connection.newPost(msg, to, groupId.getOrElse(publicGroupId)) ||> (_.foreach {
      case (post, connection, ownership) =>
        NewPost(post) |> emitDynamic
        NewConnection(connection) |> emitDynamic
        NewOwnership(ownership) |> emitDynamic

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(contain(_, post.id))
          case _ =>
        }
    })).map { case (post, connection, _) => (forClient(post), forClient(connection)) }
  }

  def getUser(id: UserId): Future[Option[User]] = db.user.get(id).map(_.map(forClient))
  def addGroup(): Future[Group] = withUserOrImplicit { user =>
    val createdGroup = db.user.createGroupForUser(user.id)
    createdGroup.map {
      case (group, membership) =>
        val clientGroup = forClient(group)
        emit(ChannelEvent(Channel.User(user.id), NewGroup(clientGroup)))
        emit(ChannelEvent(Channel.User(user.id), NewMembership(Membership(user.id, group.id))))
        clientGroup
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUserOrImplicit { user =>
    //TODO this should be handled in the query, just return false in db.user.addMember
    //TODO NEVER use bare exception! we have an exception for apierrors: UserError(something)
    if (groupId == publicGroupId) Future.failed(new Exception("adding members to public group is not allowed"))

    else {
      //TODO: check if user has access to group
      val createdMembership = db.user.addMember(groupId, userId)
      createdMembership.map { membership =>
        emit(ChannelEvent(Channel.Group(groupId), NewMembership(Membership(membership.userId.get, membership.groupId))))

        true
      }
    }
  }

  def createGroupInvite(groupId: GroupId): Future[Option[String]] = withUser { user =>
    //TODO this should be handled in the query, just return false in db.user.addMember
    //TODO NEVER use bare exception! we have an exception for apierrors: UserError(something)
    if (groupId == publicGroupId) Future.failed(new Exception("adding members to public group is not allowed"))
    else {
      //TODO: check if user has access to group
      val token = RandomUtil.alphanumeric()
      for (success <- db.user.createGroupInvite(groupId, token))
        yield if (success) Option(token) else None
    }
  }

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withUser { user =>
    //TODO optimize into one request?
    db.user.userGroupFromInvite(token).flatMap {
      case Some(group) =>
        val createdMembership = db.user.addMember(group.id, user.id)
        createdMembership.map { membership =>
          emit(ChannelEvent(Channel.User(user.id), NewGroup(group)))
          db.group.members(group.id).foreach { members =>
            println(group.id)
            //TODO: this is a hack to work without subscribing
            for ((user, _) <- members; (groupUser, groupMembership) <- members) {
              emit(ChannelEvent(Channel.User(user.id), NewUser(groupUser)))
              emit(ChannelEvent(Channel.User(user.id), NewMembership(Membership(groupMembership.userId.get, groupMembership.groupId))))
            }
          }

          Option(group.id)
        }
      case None => Future.successful(None)
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

  def getGraph(selection: GraphSelection): Future[Graph] = withUserOpt { uOpt =>
    val userIdOpt = uOpt.map(_.id)
    val graph = selection match {
      case GraphSelection.Root =>
        db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent) // TODO: consistent should not be necessary here
      case GraphSelection.Union(parentIds) =>
        getUnion(userIdOpt, parentIds).map(_.consistent) // TODO: consistent should not be necessary here
    }

    graph.map(_.withoutGroup(publicGroupId))
  }
}
