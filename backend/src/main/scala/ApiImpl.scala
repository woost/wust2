package wust.backend

import wust.db.Db
import wust.ids._
import wust.api._
import wust.backend.auth._
import wust.backend.config.Config
import wust.graph._
import wust.util.Pipe
import DbConversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.data.OptionT // helpers for Future[Option[T]] - http://typelevel.org/cats/datatypes/optiont.html
import cats.implicits._

object RandomUtil {
  import java.math.BigInteger
  import java.security.SecureRandom

  private val random = new SecureRandom()

  def alphanumeric(nrChars: Int = 24): String = {
    new BigInteger(nrChars * 5, random).toString(32)
  }
}

class ApiImpl(stateAccess: StateAccess) extends Api {
  import Server.{emit, emitDynamic}
  import stateAccess._

  def getPost(id: PostId): Future[Option[Post]] = Db.post.get(id).map(_.map(forClient))

  //TODO: return Future[Boolean]
  def addPost(
    msg: String,
    selection: GraphSelection,
    groupIdOpt: Option[GroupId]
  ): Future[Post] = withStateChange(_.withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (Db.post(msg, groupIdOpt) ||> (_.foreach {
      case (post, ownershipOpt) =>
        NewPost(forClient(post)) |> emitDynamic
        ownershipOpt.foreach { ownership =>
          NewOwnership(forClient(ownership)) |> emitDynamic
        }

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(createContainment(_, post.id))
          case _ =>
        }
    })) map { case (post, _) => forClient(post) }
  })

  def updatePost(post: Post): Future[Boolean] = withStateChange(_.withUserOrImplicit {
    //TODO: check if user is allowed to update post
    Db.post.update(post) ||> (_.foreach(if (_) UpdatedPost(forClient(post)) |> emitDynamic))
  })

  def deletePost(id: PostId): Future[Boolean] = withStateChange(_.withUserOrImplicit {
    //TODO: check if user is allowed to delete post
    Db.post.delete(id) ||> (_.foreach(if (_) DeletePost(id) |> emitDynamic))
  })

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connection] = withStateChange(_.withUserOrImplicit {
    (OptionT(Db.connection(sourceId, targetId)).map(forClient) ||> (_.map(NewConnection(_) |> emitDynamic))).value.map(_.get)
  })

  def deleteConnection(id: ConnectionId): Future[Boolean] = withStateChange(_.withUserOrImplicit {
    //TODO: check if user is allowed to delete connection
    Db.connection.delete(id) ||> (_.foreach(if (_) DeleteConnection(id) |> emitDynamic))
  })

  def createContainment(parentId: PostId, childId: PostId): Future[Containment] = withStateChange(_.withUserOrImplicit {
    (OptionT(Db.containment(parentId, childId)).map(forClient) ||> (_.map(NewContainment(_) |> emitDynamic))).value.map(_.get)
  })

  def deleteContainment(id: ContainmentId): Future[Boolean] = withStateChange(_.withUserOrImplicit {
    //TODO: check if user is allowed to delete containment
    Db.containment.delete(id) ||> (_.foreach(if (_) DeleteContainment(id) |> emitDynamic))
  })

  //TODO: return Future[Boolean]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[(Post, Connection)] = withStateChange(_.withUserOrImplicit {
    //TODO: check if user is allowed to create post in group
    (Db.connection.newPost(msg, to, groupIdOpt) ||> (_.foreach {
      case Some((post, connection, ownershipOpt)) =>
        NewPost(post) |> emitDynamic
        NewConnection(connection) |> emitDynamic
        ownershipOpt.foreach { ownership =>
          NewOwnership(ownership) |> emitDynamic
        }

        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(createContainment(_, post.id))
          case _ =>
        }
      case None =>
    })).map {
      case Some((post, connection, _)) => (forClient(post), forClient(connection))
    }
  })

  def getUser(id: UserId): Future[Option[User]] = Db.user.get(id).map(_.map(forClient))
  def addGroup(): Future[Group] = withStateChange(_.withUserOrImplicit { user =>
    val createdGroup = Db.group.createForUser(user.id)
    createdGroup.map {
      case (group, membership) =>
        val clientGroup = forClient(group)
        emit(ChannelEvent(Channel.User(user.id), NewGroup(clientGroup)))
        emit(ChannelEvent(Channel.User(user.id), NewMembership(Membership(user.id, group.id))))
        clientGroup
    }
  })

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withStateChange(_.withUserOrImplicit { user =>
    //TODO: check if user has access to group
    val createdMembership = Db.group.addMember(groupId, userId)
    createdMembership.map { membership =>
      emit(ChannelEvent(Channel.Group(groupId), NewMembership(membership)))

      true
    }
  })

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = {
    Db.user.byName(userName).flatMap {
      case Some(user) => addMember(groupId, user.id)
      case None => Future.successful(false)
    }
  }

  def createGroupInvite(groupId: GroupId): Future[Option[String]] = withState(_.withUser { user =>
    //TODO: check if user has access to group
    val token = RandomUtil.alphanumeric()
    for (success <- Db.group.createInvite(groupId, token))
      yield if (success) Option(token) else None
  })

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withState(_.withUser { user =>
    //TODO optimize into one request?
    Db.group.fromInvite(token).flatMap {
      case Some(group) =>
        val createdMembership = Db.group.addMember(group.id, user.id)
        createdMembership.map { membership =>
          emit(ChannelEvent(Channel.User(user.id), NewGroup(group)))
          Db.group.members(group.id).foreach { members =>
            //TODO: this is a hack to work without subscribing
            for ((user, _) <- members; (groupUser, groupMembership) <- members) {
              emit(ChannelEvent(Channel.User(user.id), NewUser(groupUser)))
              emit(ChannelEvent(Channel.User(user.id), NewMembership(groupMembership)))
            }
          }

          Option(group.id)
        }
      case None => Future.successful(None)
    }
  })

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  def getUnion(userIdOpt: Option[UserId], parentIds: Set[PostId]): Future[Graph] = {
    //TODO: in stored procedure
    Db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
      graph -- graph.postsById.keys.filterNot(transitiveChildren)
    }
  }

  def getGraph(selection: GraphSelection): Future[Graph] = withState(_.withUserOpt { uOpt =>
    val userIdOpt = uOpt.map(_.id)
    val graph = selection match {
      case GraphSelection.Root =>
        Db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent) // TODO: consistent should not be necessary here
      case GraphSelection.Union(parentIds) =>
        getUnion(userIdOpt, parentIds).map(_.consistent) // TODO: consistent should not be necessary here
    }

    graph
  })
}
