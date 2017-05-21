package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.db.Db
import wust.graph._
import wust.framework.state._
import wust.ids._
import wust.util.RandomUtil

import scala.concurrent.{ ExecutionContext, Future }

class ApiImpl(holder: StateHolder[State, ApiEvent], dsl: GuardDsl, db: Db)(implicit ec: ExecutionContext) extends Api {
  import holder._, dsl._

  private def isGroupMember[T](groupId: GroupId, userId: UserId)(code: => Future[T])(recover: T): Future[T] = {
    (for {
      isMember <- db.group.isMember(groupId, userId) if isMember
      result <- code
    } yield result).recover { case _ => recover }
  }

  private def hasAccessToPost[T](postId: PostId, userId: UserId)(code: => Future[T])(recover: T): Future[T] = {
    (for {
      hasAccess <- db.group.hasAccessToPost(userId, postId) if hasAccess
      result <- code
    } yield result).recover { case _ => recover }
  }

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient)) //TODO: check if public or user has access

  //TODO: return Future[Boolean]
  def addPost(msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Post] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to create post in group
    val newPost = db.post(msg, groupIdOpt)

    newPost.flatMap {
      case (post, _) =>
        selectionToContainments(selection, post.id).map { containEvents =>
          val events = Seq(NewPost(post)) ++ containEvents
          respondWithEvents(forClient(post), events: _*)
        }
    }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit { (_, user) =>
    hasAccessToPost(post.id, user.id) {
      db.post.update(post).map(respondWithEventsIf(_, UpdatedPost(forClient(post))))
    }(recover = false)
  }

  def deletePost(postId: PostId): Future[Boolean] = withUserOrImplicit { (_, user) =>
    hasAccessToPost(postId, user.id) {
      db.post.delete(postId).map(respondWithEventsIf(_, DeletePost(postId)))
    }(recover = false)
  }

  def connect(sourceId: PostId, targetId: ConnectableId): Future[Connection] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    val connection = db.connection(sourceId, targetId)
    connection.map {
      case Some(connection) =>
        respondWithEvents(forClient(connection), NewConnection(connection))
      //TODO: failure case
    }
  }

  def deleteConnection(id: ConnectionId): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToBothPosts(user, postA, postB)
    db.connection.delete(id).map(respondWithEventsIf(_, DeleteConnection(id)))
  }

  def createContainment(parentId: PostId, childId: PostId): Future[Containment] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
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
    //TODO: check if user is allowed to create post in group
    val newPost = db.connection.newPost(msg, to, groupIdOpt)

    newPost.flatMap {
      case Some((post, connection, ownershipOpt)) =>
        selectionToContainments(selection, post.id).map { containEvents =>
          val events = Seq(NewPost(post), NewConnection(connection)) ++ containEvents
          respondWithEvents[(Post, Connection)]((post, connection), events: _*)
        }
      //TODO failure case
    }
  }

  def getUser(id: UserId): Future[Option[User]] = db.user.get(id).map(_.map(forClient))
  def addGroup(): Future[GroupId] = withUserOrImplicit { (_, user) =>
    for {
      //TODO: simplify db.createForUser return values
      Some((_, dbMembership, dbGroup)) <- db.group.createForUser(user.id)
    } yield {
      val group = forClient(dbGroup)
      respondWithEvents(group.id, NewMembership(dbMembership))
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] =
    withUserOrImplicit { (_, user) =>
      isGroupMember(groupId, user.id) {
        for {
          Some((_, dbMembership, _)) <- db.group.addMember(groupId, userId)
        } yield {
          respondWithEvents(true, NewMembership(dbMembership))
        }
      }(recover = respondWithEvents(false))
    }

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = withUserOrImplicit { (_, _) =>
    (
      for {
        Some(user) <- db.user.byName(userName)
        Some((_, dbMembership, _)) <- db.group.addMember(groupId, user.id)
      } yield respondWithEvents(true, NewMembership(dbMembership))
    ).recover { case _ => respondWithEvents(false) }
  }

  private def setRandomGroupInviteToken(groupId: GroupId): Future[Option[String]] = {
    val randomToken = RandomUtil.alphanumeric()
    db.group.setInviteToken(groupId, randomToken).map(_ => Option(randomToken)).recover { case _ => None }
  }

  def recreateGroupInviteToken(groupId: GroupId): Future[Option[String]] =
    withUserOrImplicit { (_, user) =>
      isGroupMember(groupId, user.id) {
        setRandomGroupInviteToken(groupId)
      }(recover = None)
    }

  def getGroupInviteToken(groupId: GroupId): Future[Option[String]] =
    withUserOrImplicit { (_, user) =>
      isGroupMember(groupId, user.id) {
        db.group.getInviteToken(groupId).flatMap {
          case someToken @ Some(token) => Future.successful(someToken)
          case None                    => setRandomGroupInviteToken(groupId)
        }
      }(recover = None)
    }

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withUserOrImplicit { (_, user) =>
    //TODO optimize into one request?
    db.group.fromInvite(token).flatMap {
      case Some(group) =>
        db.group.addMember(group.id, user.id).map {
          case Some((_, dbMembership, dbGroup)) =>
            val group = forClient(dbGroup)
            respondWithEvents(Option(group.id), NewMembership(dbMembership))
        }
      case None => Future.successful(respondWithEvents[Option[GroupId]](None))
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

  // def getComponent(id: Id): Graph = {
  //   graph.inducedSubGraphData(graph.depthFirstSearch(id, graph.neighbours).toSet)
  // }
  private def getUnion(userIdOpt: Option[UserId], parentIds: Set[PostId]): Future[Graph] = {
    //TODO: in stored procedure
    // we also include the direct parents of the parentIds to be able no navigate upwards
    db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
      val transitiveChildrenWithDirectParents = transitiveChildren ++ parentIds.flatMap(graph.parents)
      graph -- graph.postIds.filterNot(transitiveChildrenWithDirectParents)
    }
  }

  private def selectionToContainments(selection: GraphSelection, postId: PostId): Future[Seq[ApiEvent]] = {
    val containments = selection match {
      case GraphSelection.Union(parentIds) => parentIds.map(db.containment(_, postId)).toSeq
      case _                               => Seq.empty
    }

    Future.sequence(containments).map(_.flatten).map { containments =>
      containments.map(NewContainment(_))
    }
  }
}
