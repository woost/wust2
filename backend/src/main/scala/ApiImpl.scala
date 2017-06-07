package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.db.Db
import wust.graph._
import wust.framework.state._
import wust.ids._
import wust.util.{RandomUtil, RichFuture}

import scala.concurrent.{ ExecutionContext, Future }

class ApiImpl(holder: StateHolder[State, ApiEvent], dsl: GuardDsl, db: Db)(implicit ec: ExecutionContext) extends Api {
  import holder._, dsl._

  override def changeGraph(changes: GraphChanges): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO rights
    import changes.consistent._

    //TODO error handling
    val result = db.ctx.transaction { implicit ec =>
      for {
        true <- db.post.createPublic(addPosts)
        true <- db.connection(addConnections)
        true <- db.containment(addContainments)
        true <- db.ownership(addOwnerships)
        true <- db.post.update(updatePosts)
        true <- db.post.delete(delPosts)
        true <- db.connection.delete(delConnections)
        true <- db.containment.delete(delContainments)
        true <- db.ownership.delete(delOwnerships)
      } yield true
    }//.recoverValue(false)

    result.map(respondWithEventsIf(_, NewGraphChanges(changes.consistent)))
  }

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient)) //TODO: check if public or user has access
  def getUser(id: UserId): Future[Option[User]] = db.user.get(id).map(_.map(forClient))

  def addGroup(): Future[GroupId] = withUserOrImplicit { (_, user) =>
    for {
      //TODO: simplify db.createForUser return values
      Some((_, dbMembership, dbGroup)) <- db.group.createForUser(user.id)
    } yield {
      val group = forClient(dbGroup)
      respondWithEvents(group.id, NewMembership(dbMembership), NewGroup(group))
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUserOrImplicit { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        for {
          Some((_, dbMembership, group)) <- db.group.addMember(groupId, userId)
        } yield respondWithEvents(true, NewMembership(dbMembership), NewGroup(group))
      }(recover = respondWithEvents(false))
    }
  }

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = withUserOrImplicit { (_, _) =>
    db.ctx.transaction { implicit ec =>
      (
        for {
          Some(user) <- db.user.byName(userName)
          Some((_, dbMembership, group)) <- db.group.addMember(groupId, user.id)
        } yield respondWithEvents(true, NewMembership(dbMembership), NewGroup(group))
      ).recover { case _ => respondWithEvents(false) }
    }
  }

  def recreateGroupInviteToken(groupId: GroupId): Future[Option[String]] = withUserOrImplicit { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        setRandomGroupInviteToken(groupId)
      }(recover = None)
    }
  }

  def getGroupInviteToken(groupId: GroupId): Future[Option[String]] = withUserOrImplicit { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        db.group.getInviteToken(groupId).flatMap {
          case someToken @ Some(token) => Future.successful(someToken)
          case None                    => setRandomGroupInviteToken(groupId)
        }
      }(recover = None)
    }
  }

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withUserOrImplicit { (_, user) =>
    //TODO optimize into one request?
    db.ctx.transaction { implicit ec =>
      db.group.fromInvite(token).flatMap {
        case Some(group) =>
          db.group.addMember(group.id, user.id).map {
            case Some((_, dbMembership, dbGroup)) =>
              val group = forClient(dbGroup)
              respondWithEvents(Option(group.id), NewMembership(dbMembership), NewGroup(group))
            case None => respondWithEvents[Option[GroupId]](None)
          }
        case None => Future.successful(respondWithEvents[Option[GroupId]](None))
      }
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

  private def setRandomGroupInviteToken(groupId: GroupId)(implicit ec: ExecutionContext): Future[Option[String]] = {
    val randomToken = RandomUtil.alphanumeric()
    db.group.setInviteToken(groupId, randomToken).map(_ => Option(randomToken)).recover { case _ => None }
  }

  private def getUnion(userIdOpt: Option[UserId], rawParentIds: Set[PostId])(implicit ec: ExecutionContext): Future[Graph] = {
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

  //TODO: we should raise an apierror 'forbidden', instead of false/None/etc, when a user is not allowed
  private def isGroupMember[T](groupId: GroupId, userId: UserId)(code: => Future[T])(recover: T)(implicit ec: ExecutionContext): Future[T] = {
    (for {
      isMember <- db.group.isMember(groupId, userId) if isMember
      result <- code
    } yield result).recover { case _ => recover }
  }

  private def hasAccessToPost[T](postId: PostId, userId: UserId)(code: => Future[T])(recover: T)(implicit ec: ExecutionContext): Future[T] = {
    (for {
      hasAccess <- db.group.hasAccessToPost(userId, postId) if hasAccess
      result <- code
    } yield result).recover { case _ => recover }
  }
}
