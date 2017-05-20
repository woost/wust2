package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.db.Db
import wust.graph._
import wust.framework.state._
import wust.ids._
import wust.util.RandomUtil

import scala.concurrent.{ExecutionContext, Future}

class ApiImpl(holder: StateHolder[State, ApiEvent], dsl: GuardDsl, db: Db)(implicit ec: ExecutionContext) extends Api {
  import holder._, dsl._

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient))

  //TODO: return Future[Boolean]
  def addPost(msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Post] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to create post in group
    val newPost = db.post(msg, groupIdOpt)

    //sideeffect create containment
    //TODO proper integration into request
    newPost.foreach {
      case (post, _) =>
        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(createContainment(_, post.id))
          case _ =>
        }
    }

    newPost.map {
      case (post, _) =>
        respondWithEvents(forClient(post), NewPost(post))
    }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to update post
    db.post.update(post).map(respondWithEventsIf(_, UpdatedPost(forClient(post))))
  }

  def deletePost(id: PostId): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to delete post
    db.post.delete(id).map(respondWithEventsIf(_, DeletePost(id)))
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connection] = withUserOrImplicit { (_, _) =>
    val connection = db.connection(sourceId, targetId)
    connection.map {
      case Some(connection) =>
        respondWithEvents(forClient(connection), NewConnection(connection))
      //TODO: failure case
    }
  }

  def deleteConnection(id: ConnectionId): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to delete connection
    db.connection.delete(id).map(respondWithEventsIf(_, DeleteConnection(id)))
  }

  def createContainment(parentId: PostId, childId: PostId): Future[Containment] = withUserOrImplicit { (_, _) =>
    val connection = db.containment(parentId, childId)
    connection.map {
      case Some(connection) =>
        respondWithEvents(forClient(connection), NewContainment(connection))
      //TODO: failure case
    }
  }

  def deleteContainment(id: ContainmentId): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to delete containment
    db.containment.delete(id).map(respondWithEventsIf(_, DeleteContainment(id)))
  }

  //TODO: return Future[Boolean]
  def respond(to: PostId, msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[(Post, Connection)] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowe d to create post in group
    val newPost = db.connection.newPost(msg, to, groupIdOpt)

    //sideeffect create containment
    //TODO proper integration into request
    newPost.foreach(_.foreach {
      case (post, _, _) =>
        selection match {
          case GraphSelection.Union(parentIds) =>
            parentIds.foreach(createContainment(_, post.id))
          case _ =>
        }
    })

    newPost.map {
      case Some((post, connection, ownershipOpt)) =>
        val events = Seq(NewPost(post), NewConnection(connection))
        respondWithEvents[(Post, Connection)]((post, connection), events: _*)
      //TODO failure case
    }
  }

  def getUser(id: UserId): Future[Option[User]] = db.user.get(id).map(_.map(forClient))
  def addGroup(): Future[GroupId] = withUser { (_, user) =>
    for {
      //TODO: simplify db.createForUser return values
      Some((_, dbMembership, dbGroup)) <- db.group.createForUser(user.id)
    } yield {
      val group = forClient(dbGroup)
      respondWithEvents(group.id, NewMembership(dbMembership))
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUser { (_, _) =>
    //TODO: check if user has access to group
    (
      for {
        Some((_, dbMembership, _)) <- db.group.addMember(groupId, userId)
      } yield {
        respondWithEvents(true, NewMembership(dbMembership))
      }).recover { case _ => respondWithEvents(false) }
  }

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = withUser { (_, _) =>
    (
      for {
        Some(user) <- db.user.byName(userName)
        Some((_, dbMembership, _)) <- db.group.addMember(groupId, user.id)
      } yield respondWithEvents(true, NewMembership(dbMembership))).recover { case _ => respondWithEvents(false) }
  }

  def createGroupInvite(groupId: GroupId): Future[Option[String]] = withUser { (_, user) =>
    //TODO: check if user has access to group
    val token = RandomUtil.alphanumeric()
    for (success <- db.group.createInvite(groupId, token))
      yield if (success) Option(token) else None
  }

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withUser { (_, user) =>
    //TODO optimize into one request?
    db.group.fromInvite(token).flatMap {
      case Some(group) =>
        val createdMembership = db.group.addMember(group.id, user.id)
        createdMembership.map {
          case Some((_, dbMembership, dbGroup)) =>
            val group = forClient(dbGroup)
            respondWithEvents(Option(group.id), NewMembership(dbMembership))
        }
      case None => Future.successful(respondWithEvents[Option[GroupId]](None))
    }
  }

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  private def getUnion(userIdOpt: Option[UserId], parentIds: Set[PostId]): Future[Graph] = {
    //TODO: in stored procedure
    db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
      graph -- graph.postIds.filterNot(transitiveChildren)
    }
  }

  def getGraph(selection: GraphSelection): Future[Graph] = { (state: State) =>
    val userIdOpt = state.user.map(_.id)
    val graph = selection match {
      case GraphSelection.Root =>
        db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent) // TODO: consistent should not be necessary here
      case GraphSelection.Union(parentIds) =>
        getUnion(userIdOpt, parentIds).map(_.consistent) // TODO: consistent should not be necessary here
    }

    graph
  }
}
