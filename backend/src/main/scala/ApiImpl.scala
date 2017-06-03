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
        true <- destruct(delPosts.map(db.post.delete(_)))
        true <- destruct(delConnections.map(db.connection.delete(_)))
        true <- destruct(delContainments.map(db.containment.delete(_)))
        true <- destruct(delOwnerships.map(db.ownership.delete(_)))
      } yield true
    }

    val events =
      addPosts.map(NewPost(_)) ::
      addConnections.map(NewConnection(_)) ::
      addContainments.map(NewContainment(_)) ::
      addOwnerships.map(NewOwnership(_)) ::
      updatePosts.map(UpdatedPost(_)) ::
      delPosts.map(DeletePost(_)) ::
      delConnections.map(DeleteConnection(_)) ::
      delContainments.map(DeleteContainment(_)) ::
      delOwnerships.map(DeleteOwnership(_)) ::
      Nil

    result.map(respondWithEvents(_, events.map(_.toList).flatten: _*))
  }

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient)) //TODO: check if public or user has access

  def addPost(post: Post, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Boolean] = withUserOrImplicit { (_, user) =>
    assert(post.id != null)
    def createStuff = {
      val success = groupIdOpt match {
        case Some(groupId) => db.post.createOwned(post, groupId)
        case None => db.post.createPublic(post)
      }
      success.flatMap {
        case true =>
          createSelectionContainments(selection, post.id).map { containments =>
            val events = Seq(NewPost(post)) ++ containments.map(NewContainment(_))
            respondWithEvents(true, events: _*)
          }
        case false => Future.successful(respondWithEvents(false))
      }
    }

    groupIdOpt match {
      case Some(groupId) =>
        isGroupMember(groupId, user.id) {
          createStuff
        }(recover = respondWithEvents(false))
      case None => // public group
        createStuff
    }
  }

  def addPostInContainment(post: Post, parentId: PostId, groupIdOpt: Option[GroupId]): Future[Boolean] = withUserOrImplicit { (_, user) =>
    def createStuff = {
      db.containment.newPost(post, parentId, groupIdOpt).map {
        case true =>
          val ownership = groupIdOpt.map(Ownership(post.id, _))
          val events = NewPost(post) :: NewContainment(Containment(parentId, post.id)) :: ownership.map(NewOwnership(_)).toList
          respondWithEvents(true, events: _*)
        case false => respondWithEvents(false)
      }
    }

    groupIdOpt match {
      case Some(groupId) =>
        isGroupMember(groupId, user.id) {
          createStuff
        }(recover = respondWithEvents(false))
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

  def connect(sourceId: PostId, targetId: PostId): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    val connection = Connection(sourceId, targetId)
    db.connection(connection).map(respondWithEventsIf(_, NewConnection(connection)))
  }

  def deleteConnection(connection: Connection): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToBothPosts(user, postA, postB)
    db.connection.delete(connection).map(respondWithEventsIf(_, DeleteConnection(connection)))
  }

  def createSelection(postId: PostId, selection: GraphSelection): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    createSelectionContainments(selection, postId).map { containments =>
      val events = containments.map(NewContainment(_))
      respondWithEvents(true, events: _*)
    }
  }

  def createContainment(parentId: PostId, childId: PostId): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: hasAccessToOnePost(user, postA, postB)
    val containment = Containment(parentId, childId)
    db.containment(containment).map(respondWithEventsIf(_, NewContainment(containment)))
  }

  def deleteContainment(containment: Containment): Future[Boolean] = withUserOrImplicit { (_, _) =>
    //TODO: check if user is allowed to delete containment
    db.containment.delete(containment).map(respondWithEventsIf(_, DeleteContainment(containment)))
  }

  def respond(targetPostId: PostId, post: Post, selection: GraphSelection, groupIdOpt: Option[GroupId]): Future[Boolean] = withUserOrImplicit { (_, user) =>
    def createStuff = {
      val newPost = db.connection.newPost(post, targetPostId, groupIdOpt)
      newPost.flatMap {
        case true =>
          val connection = Connection(post.id, targetPostId)
          val ownership = groupIdOpt.map(Ownership(post.id, _))
          val selectionContainmentsFut = createSelectionContainments(selection, post.id)
          val parentContainmentsFut = for {
            parentIds <- db.post.getParentIds(targetPostId)
            containments <- createContainments(parentIds, post.id)
          } yield containments

          for {
            selectionContainments <- selectionContainmentsFut
            parentContainments <- parentContainmentsFut
          } yield {
            val containmentEvents = (selectionContainments ++ parentContainments).map(NewContainment(_))
            val events = Seq(NewPost(post), NewConnection(connection)) ++ ownership.map(NewOwnership(_)) ++ containmentEvents
            respondWithEvents(true, events: _*)
          }
        case false => Future.successful(respondWithEvents(false))
      }
    }
    groupIdOpt match {
      case Some(groupId) =>
        isGroupMember(groupId, user.id) {
          createStuff
        }(recover = respondWithEvents(false))
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
    val containments = parentIds.map(Containment(_, postId))
    createContainments(containments).map(_ => containments) //TODO error
  }

  // TODO one transaction and fail
  private def createContainments(containments: Seq[Containment]): Future[Boolean] = {
    //TODO: bulk insert into database
    val created = containments.map(db.containment(_))
    Future.sequence(created).map(_.forall(identity))
  }

  private def createSelectionContainments(selection: GraphSelection, postId: PostId): Future[Seq[Containment]] = {
    val containments = GraphSelection.toContainments(selection, postId)
    createContainments(containments).map(_ => containments) //TODO error
  }
}
