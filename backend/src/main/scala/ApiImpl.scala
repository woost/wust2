package wust.backend

import wust.api._
import wust.backend.DbConversions._
import wust.db.Db
import wust.graph._
import wust.ids._
import wust.util.RandomUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ApiImpl(holder: StateHolder[State, ApiEvent], dsl: GuardDsl, db: Db)(implicit ec: ExecutionContext) extends Api {
  import dsl._
  import holder._

  override def changeGraph(changes: List[GraphChanges]): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO permissions
    val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
      changes.foldLeft(Future.successful(true)){ (previous, changes) =>
        import changes.consistent._

        previous.flatMap { success =>
          if (success) {
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
          } else Future.successful(false)
        }
      }
    }

    val compactChanges = changes.foldLeft(GraphChanges.empty)(_ merge _)
    result
      .recover {
        case NonFatal(t) =>
          scribe.error(s"unexpected error in apply graph change: $changes")
          scribe.error(t)
          false
      }
      .map(respondWithEventsIf(_, NewGraphChanges(compactChanges)))
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

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUserOrImplicit { (_, user) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        for {
          Some(user) <- db.user.get(userId)
          Some((_, dbMembership, group)) <- db.group.addMember(groupId, userId)
        } yield respondWithEvents(true, NewMembership(dbMembership), NewUser(user))
      }(recover = respondWithEvents(false))
    }
  }

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = withUserOrImplicit { (_, _) =>
    db.ctx.transaction { implicit ec =>
      (
        for {
          Some(user) <- db.user.byName(userName)
          Some((_, dbMembership, group)) <- db.group.addMember(groupId, user.id)
        } yield respondWithEvents(true, NewMembership(dbMembership), NewUser(user))
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
          case someToken @ Some(_) => Future.successful(someToken)
          case None                => setRandomGroupInviteToken(groupId)
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
              respondWithEvents(Option(group.id), NewMembership(dbMembership))
            case None => respondWithEvents[Option[GroupId]](None)
          }
        case None => Future.successful(respondWithEvents[Option[GroupId]](None))
      }
    }
  }

  def getGraph(selection: Page): Future[Graph] = { (state: State) =>
    val userIdOpt = state.user.map(_.id)
    val graph = selection match {
      case Page.Root =>
        db.graph.getAllVisiblePosts(userIdOpt).map(forClient(_).consistent) // TODO: consistent should not be necessary here
      case Page.Union(parentIds) =>
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
      val descendants = parentIds.flatMap(graph.descendants) ++ parentIds
      val descendantsWithDirectParents = descendants ++ parentIds.flatMap(graph.parents)
      graph removePosts graph.postIds.filterNot(descendantsWithDirectParents)
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
