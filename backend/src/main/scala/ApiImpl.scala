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

  override def changeGraph(changes: List[GraphChanges]): Future[Boolean] = withUserOrImplicit { (_, user, wasCreated) =>
    //TODO permissions

    val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
      changes.foldLeft(Future.successful(true)){ (previousSuccess, changes) =>
        import changes.consistent._

        // val postsWithUser: Set[Post] = enrichPostWithUser(addPosts);
        val postsWithUser: Set[Post] =
          addPosts.map { post =>
            // if(!wasCreated) assert(post.author == user.id, s"(Post author id) ${post.author} != ${user.id} (user id)")
            post.copy(author = user.id)
          };

        previousSuccess.flatMap { success =>
          if (success) {
            for {
              true <- db.post.createPublic(postsWithUser)
              true <- db.connection(addConnections)
              true <- db.ownership(addOwnerships)
              true <- db.post.update(updatePosts)
              true <- db.post.delete(delPosts)
              true <- db.connection.delete(delConnections)
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
      .map(respondWithEventsIfToAllButMe(_, NewGraphChanges(compactChanges)))
  }

  def getPost(id: PostId): Future[Option[Post]] = db.post.get(id).map(_.map(forClient)) //TODO: check if public or user has access
  def getUser(id: UserId): Future[Option[User]] = db.user.get(id).map(_.map(forClient))

  def addGroup(): Future[GroupId] = withUserOrImplicit { (_, user, _) =>
    for {
      //TODO: simplify db.createForUser return values
      Some((_, dbMembership, dbGroup)) <- db.group.createForUser(user.id)
    } yield {
      val group = forClient(dbGroup)
      respondWithEventsToAllButMe(group.id, NewMembership(dbMembership))
    }
  }

  def addMember(groupId: GroupId, userId: UserId): Future[Boolean] = withUserOrImplicit { (_, user, _) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        for {
          Some(user) <- db.user.get(userId)
          Some((_, dbMembership, group)) <- db.group.addMember(groupId, userId)
        } yield respondWithEventsToAllButMe(true, NewMembership(dbMembership), NewUser(user))
      }(recover = respondWithEventsToAllButMe(false))
    }
  }

  def addMemberByName(groupId: GroupId, userName: String): Future[Boolean] = withUserOrImplicit { (_, _, _) =>
    db.ctx.transaction { implicit ec =>
      (
        for {
          Some(user) <- db.user.byName(userName)
          Some((_, dbMembership, group)) <- db.group.addMember(groupId, user.id)
        } yield respondWithEventsToAllButMe(true, NewMembership(dbMembership), NewUser(user))
      ).recover { case _ => respondWithEventsToAllButMe(false) }
    }
  }

  def recreateGroupInviteToken(groupId: GroupId): Future[Option[String]] = withUserOrImplicit { (_, user, _) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        setRandomGroupInviteToken(groupId)
      }(recover = None)
    }
  }

  def getGroupInviteToken(groupId: GroupId): Future[Option[String]] = withUserOrImplicit { (_, user, _) =>
    db.ctx.transaction { implicit ec =>
      isGroupMember(groupId, user.id) {
        db.group.getInviteToken(groupId).flatMap {
          case someToken @ Some(_) => Future.successful(someToken)
          case None                => setRandomGroupInviteToken(groupId)
        }
      }(recover = None)
    }
  }

  def acceptGroupInvite(token: String): Future[Option[GroupId]] = withUserOrImplicit { (_, user, _) =>
    //TODO optimize into one request?
    db.ctx.transaction { implicit ec =>
      db.group.fromInvite(token).flatMap {
        case Some(group) =>
          db.group.addMember(group.id, user.id).map {
            case Some((_, dbMembership, dbGroup)) =>
              val group = forClient(dbGroup)
              respondWithEventsToAllButMe(Option(group.id), NewMembership(dbMembership))
            case None => respondWithEventsToAllButMe[Option[GroupId]](None)
          }
        case None => Future.successful(respondWithEventsToAllButMe[Option[GroupId]](None))
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

  def importGithubUrl(url: String): Future[Boolean] = withUserOrImplicit { (_, user, _) =>

    object GitHubImporter {
      import github4s.Github
      import github4s.Github._
      import github4s.jvm.Implicits._
      import scalaj.http.HttpResponse
      import github4s.free.domain.{Comment, Issue, SearchIssuesResult, User => GHUser}

      val accessToken = sys.env.get("GITHUB4S_ACCESS_TOKEN")

      def getIssues(orga: String = "GRBurst", repo: String = "purple-gnome-keyring", issueNumber: Option[Int] = None): Future[(Set[Post], Set[Connection])] = {
        
        val listIssues = Github(accessToken).issues.listIssues(orga, repo)

        // val sets: Future[(Set[Post], Set[Connection])] = listIssues.execFuture[HttpResponse[String]]().map {
        val f_response: Future[github4s.GithubResponses.GHResponse[List[github4s.free.domain.Issue]]] = listIssues.execFuture[HttpResponse[String]]()

        val res:Future[(Set[Post], Set[Connection])] = f_response.map { response => 
          val zippedTuples: Set[(Set[Post], Set[Connection])] = response.map {
            right => {
              val tuples: Set[(Set[Post], Set[Connection])] = right.result.map { issue =>
                issueNumber match {
                  case Some(inum) if inum != issue.number => {
                    (Set.empty[Post], Set.empty[Connection])
              }
                  case _ => {
            val userId = UserId(issue.user match {
              case None => 1
              case Some(githubUser: GHUser) => githubUser.id
            }) //TODO: create this user
            val tempUserId = user.id
            val title = Post(PostId(issue.id.toString), issue.title, tempUserId)
                    val body = Post(PostId(issue.number.toString), issue.body, tempUserId)
                    val conn = Connection(body.id, Label.parent, title.id)
                    val cont = Connection(title.id, "title", body.id)
                    val comments = issue.comments_url
                    println(s"Comments url = $comments")
                    val pair: (Set[Post], Set[Connection]) = (Set[Post](title, body), Set[Connection](conn, cont))
                    // println(s"pair: $pair")
                    pair
        }
                }
              }.toSet
              // println(s"tuples: $tuples")
              tuples
            }
          }.getOrElse(Set((Set.empty[Post], Set.empty[Connection])))

          val (posts, conns) = zippedTuples.unzip
          // println(s"posts: ${posts.flatten}")
          // println(s"conns: ${conns.flatten}")
          (posts.flatten, conns.flatten)

      }
        res
    }
    }

    // TODO: Reuse graph changes instead
    // val postsOfUrl = Set(Post(PostId(scala.util.Random.nextInt.toString), url, UserId(1)))
    // changeGraph(List(GraphChanges(addPosts = postsOfUrl)))

    val _url = url.stripLineEnd.stripMargin.trim.stripPrefix("https://").stripPrefix("http://").stripPrefix("github.com/").stripSuffix("/")
    val numEndPattern = "issues/[0-9]+$".r
    val issueNumGiven = numEndPattern.findFirstIn(_url).getOrElse("/").split("/")
    val issueNum = if (issueNumGiven.isEmpty) None else Some(issueNumGiven(1).toInt)
    val urlData = _url.stripSuffix("/").stripSuffix((if(issueNum.isDefined) issueNum.get.toString else "")).stripSuffix("/").stripSuffix("/issues").split("/")

    println(s"urlData: orga = ${urlData(0)}, repo = ${urlData(1)}, issue number = ${issueNum.getOrElse(-1)}")

    assert(urlData.size == 2, "Could not extract url")
    val postsOfUrl = GitHubImporter.getIssues(urlData(0), urlData(1), issueNum)

    // val postsOfUrl = Set(Post(PostId(scala.util.Random.nextInt.toString), url, user.id))
    val result: Future[Boolean] = postsOfUrl.flatMap { case (posts, connections) =>
      db.ctx.transaction { implicit ec =>
        for {
          true <- db.post.createPublic(posts)
          true <- db.connection(connections)
        } yield true
      }
    }
    // val postsOfUrl = Set(Post(PostId(scala.util.Random.nextInt.toString), url, user.id))
    // val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
    //   for {
    //     true <- db.post.createPublic(postsOfUrl)
    //   } yield true

    // }

    result.recover {
      case NonFatal(t) =>
        scribe.error(s"unexpected error in import")
        scribe.error(t)
        false
    }//.map(respondWithEventsIfToAllButMe(_, NewGraphChanges(GraphChanges(addPosts = postsOfUrl)))) //<-- not working for import

  }

  def importGitterUrl(url: String): Future[Boolean] = withUserOrImplicit { (_, user, _) =>

    // object GitHubImporter {

    // }

    // TODO: Reuse graph changes instead
    val postsOfUrl = Set(Post(PostId(scala.util.Random.nextInt.toString), url, user.id))
    val result: Future[Boolean] = db.ctx.transaction { implicit ec =>
      for {
        true <- db.post.createPublic(postsOfUrl)
      } yield true

    }
    result.recover {
      case NonFatal(t) =>
        scribe.error(s"unexpected error in import")
        scribe.error(t)
        false
    } //.map(respondWithEventsIfToAllButMe(_,  NewGraphChanges(GraphChanges(addPosts = postsOfUrl))))
  }
    
}
