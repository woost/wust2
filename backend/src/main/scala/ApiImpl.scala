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

  def addPost(msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Option[PostId]] = withUserOrImplicit { (_, user) =>
    def createStuff = {
      val newPost = db.post(msg, groupIdOpt)
      newPost.flatMap {
        case (post, _) =>
          selectionToContainments(selection, post.id).map { containments =>
            val events = Seq(NewPost(post)) ++ containments.map(NewContainment(_))
            respondWithEvents(Option(post.id), events: _*)
          }
      }
    }
    groupIdOpt match {
      case Some(groupId) =>
        isGroupMember(groupId, user.id) {
          createStuff
        }(recover = respondWithEvents(None))
      case None => // public group
        createStuff
    }
  }

  def addPostInContainment(msg: String, parentId: PostId, groupId: Option[GroupId]): Future[Option[PostId]] = withUserOrImplicit { (_, user) =>
    def createStuff = {
      db.containment.newPost(msg, parentId, groupId).map {
        case Some((post, containment, ownership)) =>
          val events = NewPost(post) :: NewContainment(containment) :: ownership.map(NewOwnership(_)).toList
          respondWithEvents(Option(post.id), events: _*)
        case None =>
          respondWithEvents(None: Option[PostId])
      }
    }

    groupId match {
      case Some(groupId) =>
        isGroupMember(groupId, user.id) {
          createStuff
        }(recover = respondWithEvents(None: Option[PostId]))
      case None => // public group
        createStuff
    }
  }

  def updatePost(post: Post): Future[Boolean] = withUserOrImplicit { (_, user) =>
    hasAccessToPost(post.id, user.id) {
      db.post.update(post).map(respondWithEventsIf(_, UpdatedPost(forClient(post))))
    }(recover = false)
  }

  def deletePost(postId: PostId, selection: GraphSelection): Future[Boolean] = withUserOrImplicit { (state, user) =>
    val toDelete = (Collapse.getHiddenPosts(state.graph removePosts selection.parentIds, Set(postId)) + postId).toSeq
    val deletedPostIds = Future.sequence(toDelete.map { postId =>
      hasAccessToPost(postId, user.id) {
        db.post.delete(postId).map{ case true => Some(postId); case false => None }
      }(recover = None)
    }).map(_.flatten)

    deletedPostIds.map { postIds =>
      respondWithEvents(postIds.nonEmpty, postIds.map(DeletePost(_)): _*)
    }
  }

  def connect(sourceId: PostId, targetId: PostId): Future[Option[Connection]] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    val connection = db.connection(sourceId, targetId)
    connection.map {
      case Some(connection) =>
        respondWithEvents[Option[Connection]](Option(forClient(connection)), NewConnection(connection))
      case None =>
        respondWithEvents[Option[Connection]](None)
    }
  }

  def deleteConnection(connection: Connection): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToBothPosts(user, postA, postB)
    db.connection.delete(connection).map(respondWithEventsIf(_, DeleteConnection(connection)))
  }

  def createSelection(postId: PostId, selection: GraphSelection): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    selectionToContainments(selection, postId).map { containments =>
      val events = containments.map(NewContainment(_))
      respondWithEvents(true, events: _*)
    }
  }

  def createContainment(parentId: PostId, childId: PostId): Future[Option[Containment]] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    val containment = db.containment(parentId, childId)
    containment.map {
      case Some(containment) =>
        respondWithEvents[Option[Containment]](Option(forClient(containment)), NewContainment(containment))
      case None =>
        respondWithEvents[Option[Containment]](None)
    }
  }

  def deleteContainment(containment: Containment): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to delete containment
    db.containment.delete(containment).map(respondWithEventsIf(_, DeleteContainment(containment)))
  }

  def respond(targetPostId: PostId, msg: String, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Option[(Post, Connection)]] = withUserOrImplicit { (_, user) =>
    def createStuff = {
      val newPost = db.connection.newPost(msg, targetPostId, groupIdOpt)
      newPost.flatMap {
        case Some((post, connection, ownershipOpt)) =>
          val selectionContainmentsFut = selectionToContainments(selection, post.id)
          val parentContainmentsFut = for {
            parentIds <- db.post.getParentIds(targetPostId)
            containments <- createContainments(parentIds, post.id)
          } yield containments

          for {
            selectionContainments <- selectionContainmentsFut
            parentContainments <- parentContainmentsFut
          } yield {
            val containmentEvents = (selectionContainments ++ parentContainments).map(NewContainment(_))
            val events = Seq(NewPost(post), NewConnection(connection)) ++ containmentEvents
            respondWithEvents[Option[(Post, Connection)]](Option((post, connection)), events: _*)
          }
        case None => Future.successful(respondWithEvents[Option[(Post, Connection)]](None))
      }
    }
    groupIdOpt match {
      case Some(groupId) =>
        isGroupMember(groupId, user.id) {
          createStuff
        }(recover = respondWithEvents[Option[(Post, Connection)]](None))
      case None => // public group
        createStuff
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

  private def createContainments(parentIds: Seq[PostId], postId: PostId): Future[Seq[Containment]] = {
    //TODO: bulk insert into database
    val containments = parentIds.map(db.containment(_, postId))

    //TODO: fail instead of flatten?
    Future.sequence(containments).map(_.flatten.map(forClient))
  }

  private def selectionToContainments(selection: GraphSelection, postId: PostId): Future[Seq[Containment]] = {
    selection match {
      case GraphSelection.Union(parentIds) => createContainments(parentIds.toSeq, postId)
      case _                               => Future.successful(Seq.empty)
    }
  }
}
