package wust.backend

import wust.db.Db
import wust.ids._
import wust.api._
import wust.backend.auth._
import wust.backend.config.Config
import wust.graph._
import wust.util.{RandomUtil, Pipe}
import DbConversions._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import cats.data.OptionT // helpers for Future[Option[T]] - http://typelevel.org/cats/datatypes/optiont.html
import cats.implicits._

class ApiImpl(stateAccess: StateAccess) extends Api {
  import stateAccess._

  def getPost(id: PostId): Future[Option[Post]] = Db.post.get(id).map(_.map(forClient))

  //TODO: return Future[Boolean]
  def addPost(msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Post] = withUserOrImplicit { (_,_) =>
    //TODO: check if user is allowed to create post in group
    val newPost = Db.post(msg, groupIdOpt)

    //sideeffect create containment
    //TODO proper integration into request
    newPost.foreach { case (post, _) =>
      selection match {
        case GraphSelection.Union(parentIds) =>
          parentIds.foreach(createContainment(_, post.id))
        case _ =>
      }
    }

    newPost.map { case (post, ownershipOpt) =>
      val events = Seq(NewPost(post)) ++ ownershipOpt.map(NewOwnership(_)).toSeq
      RequestResponse(forClient(post), events :_*)
    }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit { (_,_) =>
    //TODO: check if user is allowed to update post
    Db.post.update(post).map(RequestResponse.eventsIf(_, UpdatedPost(forClient(post))))
  }

  def deletePost(id: PostId): Future[Boolean] = withUserOrImplicit { (_,_) =>
    //TODO: check if user is allowed to delete post
    Db.post.delete(id).map(RequestResponse.eventsIf(_, DeletePost(id)))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connection] = withUserOrImplicit { (_,_) =>
    val connection = Db.connection(sourceId, targetId)
    connection.map {
      case Some(connection) =>
        RequestResponse(forClient(connection), NewConnection(connection))
      //TODO: failure case
    }
  }

  def deleteConnection(id: ConnectionId): Future[Boolean] = withUserOrImplicit { (_,_) =>
    //TODO: check if user is allowed to delete connection
    Db.connection.delete(id).map(RequestResponse.eventsIf(_, DeleteConnection(id)))
  }

  def createContainment(parentId: PostId, childId: PostId): Future[Containment] = withUserOrImplicit { (_,_) =>
    val connection = Db.containment(parentId, childId)
    connection.map {
      case Some(connection) =>
        RequestResponse(forClient(connection), NewContainment(connection))
      //TODO: failure case
    }
  }

  def deleteContainment(id: ContainmentId): Future[Boolean] = withUserOrImplicit { (_,_) =>
    //TODO: check if user is allowed to delete containment
    Db.containment.delete(id).map(RequestResponse.eventsIf(_, DeleteContainment(id)))
  }

  //TODO: return Future[Boolean]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[(Post, Connection)] = withUserOrImplicit { (_,_) =>
    //TODO: check if user is allowe d to create post in group
    val newPost = Db.connection.newPost(msg, to, groupIdOpt)

    //sideeffect create containment
    //TODO proper integration into request
    newPost.foreach(_.foreach { case (post, _, _) =>
      selection match {
        case GraphSelection.Union(parentIds) =>
          parentIds.foreach(createContainment(_, post.id))
        case _ =>
      }
    })

    newPost.map {
      case Some((post, connection, ownershipOpt)) =>
        val events = Seq(NewPost(post), NewConnection(connection)) ++ ownershipOpt.map(NewOwnership(_)).toSeq
        RequestResponse[(Post, Connection)]((post, connection), events: _*)
      //TODO failure case
    }
  }

  def getUser(id: UserId): Future[Option[User]] = Db.user.get(id).map(_.map(forClient))
  def addGroup(): Future[Group] = withUserOrImplicit { (_, user) =>
    val createdGroup = Db.group.createForUser(user.id)
    createdGroup.map {
      case (group, membership) =>
        val clientGroup = forClient(group)
        val membership = Membership(user.id, group.id)
        RequestResponse(clientGroup, NewGroup(clientGroup), NewMembership(membership))
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUserOrImplicit { (_, user) =>
    //TODO: check if user has access to group
    val createdMembership = Db.group.addMember(groupId, userId)
    createdMembership.map { membership =>
      RequestResponse(true, NewMembership(membership))
    }
  }

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = {
    Db.user.byName(userName).flatMap {
      case Some(user) => addMember(groupId, user.id)
      case None => Future.successful(false)
    }
  }

  def createGroupInvite(groupId: GroupId): Future[Option[String]] = withUser { (_, user) =>
    //TODO: check if user has access to group
    val token = RandomUtil.alphanumeric()
    for (success <- Db.group.createInvite(groupId, token))
      yield if (success) Option(token) else None
  }

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withUser { (_, user) =>
    //TODO optimize into one request?
    Db.group.fromInvite(token).flatMap {
      case Some(group) =>
        val createdMembership = Db.group.addMember(group.id, user.id)
        createdMembership.map { membership =>
          RequestResponse(Option(group.id), NewGroup(group))
        }
      case None => Future.successful(RequestResponse[Option[GroupId]](None))
    }
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  private def getUnion(userIdOpt: Option[UserId], parentIds: Set[PostId]): Future[Graph] = {
    //TODO: in stored procedure
    Db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
      graph -- graph.postsById.keys.filterNot(transitiveChildren)
    }
  }

  def getGraph(selection: GraphSelection): Future[Graph] = { (state: State) =>
    val userIdOpt = state.user.map(_.id)
    val graph = selection match {
      case GraphSelection.Root =>
        Db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent) // TODO: consistent should not be necessary here
      case GraphSelection.Union(parentIds) =>
        getUnion(userIdOpt, parentIds).map(_.consistent) // TODO: consistent should not be necessary here
    }

    graph
  }
}
