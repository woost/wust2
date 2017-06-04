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

  //TODO: we should raise an apierror 'forbidden', instead of false/None/etc, when a user is not allowed
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

  override def changeGraph(changes: GraphChanges): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO bulk inserts
    //TODO rights
    import changes.consistent._

    def destruct(s: Set[Future[Boolean]]): Future[Boolean] = Future.sequence(s).map(_.forall(identity))

    //TODO error handling
    val result = db.ctx.transaction { _ =>
      for {
        true <- destruct(addPosts.map(db.post.createPublic(_)))
        true <- destruct(addConnections.map(db.connection(_)))
        true <- destruct(addContainments.map(db.containment(_)))
        true <- destruct(addOwnerships.map(db.ownership(_)))
        true <- destruct(updatePosts.map(db.post.update(_)))
        _ <- destruct(delPosts.map(db.post.delete(_)))
        _ <- destruct(delConnections.map(db.connection.delete(_)))
        _ <- destruct(delContainments.map(db.containment.delete(_)))
        _ <- destruct(delOwnerships.map(db.ownership.delete(_)))
      } yield true
    }

    result.map(respondWithEvents(_, NewGraphChanges(changes.consistent)))
  }

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient)) //TODO: check if public or user has access

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
          case None => respondWithEvents[Option[GroupId]](None)
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
  private def getUnion(userIdOpt: Option[UserId], rawParentIds: Set[PostId]): Future[Graph] = {
    //TODO: in stored procedure
    // we also include the direct parents of the parentIds to be able no navigate upwards
    db.graph.getAllVisiblePosts(userIdOpt).map { dbGraph =>
      val graph = forClient(dbGraph)
      val parentIds = rawParentIds filter graph.postsById.isDefinedAt
      val transitiveChildren = parentIds.flatMap(graph.transitiveChildren) ++ parentIds
      val transitiveChildrenWithDirectParents = transitiveChildren ++ parentIds.flatMap(graph.parents)
      graph removePosts graph.postIds.filterNot(transitiveChildrenWithDirectParents)
    }
  }
}
