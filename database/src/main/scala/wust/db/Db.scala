package wust.db

import com.typesafe.config.Config
import io.getquill._
import io.getquill.context.async.TransactionalExecutionContext
import wust.ids._

import scala.concurrent.{ExecutionContext, Future}

object Db {
  def apply(config: Config): Db = {
    new Db(new PostgresAsyncContext(LowerCase, config))
  }
}

// we use this type instead of Unit, because Future[Unit] is dangerous, because this typechecks: Future[Future[Something]] : Future[Unit]
object SuccessResult

// all database operations
class Db(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCoreCodecs(ctx) {
  import Data._
  import ctx._

  // schema meta: we can define how a type corresponds to a db table
  private implicit val userSchema = schemaMeta[User]("node") // User type is stored in node table with same properties.
  private implicit val nodeRawSchema = schemaMeta[NodeRaw]("node") // NodeRaw is just a raw representation of node

  // enforce check of json-type for extra safety. additional this makes sure that partial indices on user.data are used.
  private val queryUser = quote { query[User].filter(_.data.jsonType == lift(NodeData.User.tpe)) }

  // ! Require implicit transactional execution context so people just use it within a transaction,
  // ! only then can checkUnexpected failure be rolled back
  def checkUnexpected[U](cond: Boolean, success: U, errorMsg: => String)(implicit _not_used: TransactionalExecutionContext): Future[U] = {
    if (cond) Future.successful(success) else Future.failed(new Exception(errorMsg))
  }
  def checkUnexpected(cond: Boolean, errorMsg: => String)(implicit _not_used: TransactionalExecutionContext): Future[SuccessResult.type] = {
    checkUnexpected(cond, SuccessResult, errorMsg)
  }

  object node {
    // node ids are unique, so the methods can assume that at max 1 row was touched in each operation
    def create(node: Node)(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = create(List(node))
    def create(nodes: Seq[Node])(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      if (nodes.isEmpty) return Future.successful(SuccessResult)

      // if there is an id conflict, we update the post.
      // this is fine, because we always check permissions before creating new nodes.
      // non-exisiting ids are automatically allowed.
      // important: the permission checks must run in the same transaction.
      ctx.run(liftQuery(nodes).foreach {
        query[Node]
          .insert(_)
          .onConflictUpdate(_.id)(
            (node, excluded) => node.data -> excluded.data,
            (node, excluded) => node.role -> excluded.role,
            (node, excluded) => node.accessLevel -> excluded.accessLevel,
            (node, excluded) => node.views -> excluded.views
          )
      }).flatMap(touched => checkUnexpected(touched.forall(_ == 1), s"Unexpected number of node inserts: ${touched.sum} / ${nodes.size} = ${nodes.zip(touched)}"))
    }

    private val canAccess = quote { (nodeId: NodeId, userId: UserId) =>
      infix"""node_can_access($nodeId, $userId)""".as[Boolean]
    }

    def resolveMentionedNodesWithAccess(mentionedNodeIds: scala.collection.Seq[NodeId], canAccessNodeId: NodeId)(implicit ec: ExecutionContext): Future[Seq[User]] = {
      val q = quote {
        infix"""
          (
            select * from node
            where node.id = ANY(${lift(mentionedNodeIds)} :: uuid[]) and node.data->>'type' = 'User' and can_access_node_via_url(node.id, ${lift(canAccessNodeId)})
          ) UNION (
            select node.* from node as initial
            join edge on edge.sourceid = initial.id and edge.data->>'type' = 'Member'
            join node on edge.targetid = node.id and node.data->>'type' = 'User' and can_access_node_via_url(node.id, ${lift(canAccessNodeId)})
            where initial.id = ANY(${lift(mentionedNodeIds)} :: uuid[]) and initial.data->>'type' <> 'User'
          )
        """.as[Query[User]]
      }

      ctx.run(q)
    }

    def get(userId: UserId, nodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[Node]] = {
      ctx.run {
        query[NodeRaw].filter(accessedNode =>
          accessedNode.id == lift(nodeId) && canAccess(lift(nodeId), lift(userId))
        ).take(1)
      }.map(_.headOption.map(_.toNode))
    }

    def get(nodeIds: scala.collection.Seq[NodeId])(implicit ec: ExecutionContext): Future[List[Node]] = {
      //TODO
      //ctx.run(query[Node].filter(p => liftQuery(nodeIds) contains p.id))
      val q = quote {
        infix"""
          select node.* from unnest(${lift(nodeIds)} :: uuid[]) inputNodeId join node on node.id = inputNodeId
        """.as[Query[NodeRaw]]
      }

      ctx.run(q).map(_.map(_.toNode))
    }

    def getAccessibleWorkspaces(userId: UserId, nodeId: NodeId)(implicit ec: ExecutionContext): Future[List[NodeId]] = {
      val workspaceRoles = List(NodeRole.Task, NodeRole.Project, NodeRole.Note, NodeRole.Message, NodeRole.Neutral)
      ctx.run(
        for {
          parentId <- query[Edge]
            .filter(e => e.data.jsonType == lift(EdgeData.Child.tpe) && e.targetId == lift(nodeId) && canAccess(e.sourceId, lift(userId)))
            .map(_.sourceId)
          node <- query[Node].filter(node => node.id == parentId && liftQuery(workspaceRoles).contains(node.role))
        } yield node.id
      )
    }

    def getFileNodes(keys: scala.collection.Seq[String])(implicit ec: ExecutionContext): Future[Seq[(NodeId, NodeData.File)]] = {
      ctx.run {
        query[NodeRaw].filter(node =>
          node.data.jsonType == lift(NodeData.File.tpe) && liftQuery(keys).contains(node.data->>"key")
        )
      }.map(_.map(n => n.id -> n.data.asInstanceOf[NodeData.File]))
    }

    def getMembers(nodeId: NodeId)(implicit ec: ExecutionContext): Future[List[User]] = {
      ctx.run {
        for {
          membershipConnection <- query[MemberEdge].filter(c =>
            //TODO call-site inline to have constant string instead of param for member.tpe
            c.sourceId == lift(nodeId) && c.data.jsonType == lift(EdgeData.Member.tpe)
          )
          userNode <- queryUser.filter(_.id == membershipConnection.targetId)
        } yield userNode
      }
    }

    def addMemberIfCanAccessViaUrlAndNotMember(nodeId: NodeId, userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      val insertMembership = quote { (nodeId: NodeId, userId: UserId) =>
        infix"""
          insert into edge(sourceid, data, targetid)
          select ${nodeId}, jsonb_build_object('type', 'Member', 'level', 'readwrite'::accesslevel), ${userId}
          where can_access_node_via_url($userId, $nodeId)
          ON CONFLICT DO NOTHING
        """.as[Insert[Edge]]
      }
      ctx.run(insertMembership(lift(nodeId), lift(userId))).map(_ == 1)
    }

    def addMember(nodeId: NodeId, userId: UserId, accessLevel: AccessLevel)(implicit ec: ExecutionContext): Future[Boolean] = addMember(nodeId :: Nil, userId, accessLevel).map(_.nonEmpty)
    def addMember(nodeIds: Seq[NodeId], userId: UserId, accessLevel: AccessLevel)(implicit ec: ExecutionContext): Future[Seq[NodeId]] = {
      val insertMembership = quote { nodeId: NodeId =>
        infix"""
          insert into edge(sourceid, data, targetid) values
          (${nodeId}, jsonb_build_object('type', 'Member', 'level', ${lift(accessLevel)}::accesslevel), ${lift(userId)})
          ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE set data = EXCLUDED.data
        """.as[Insert[Edge]].returning(_.sourceId)
      }
      ctx.run(liftQuery(nodeIds).foreach(insertMembership(_))).map { x =>
        //FIXME doesn't this always return nodeIds?
        assert(x.size == nodeIds.size)
        x
      }
    }

    def removeMember(nodeId: NodeId, userId: UserId)(implicit ec: TransactionalExecutionContext): Future[Boolean] = {
      ctx.run(query[Edge].filter(e => e.sourceId == lift(nodeId) && e.targetId == lift(userId) && e.data.jsonType == lift(EdgeData.Member.tpe)).delete).flatMap { touched =>
        checkUnexpected(touched <= 1, success = touched == 1, s"Unexpected number of edge deletes: $touched <= 1")
      }
    }
  }


  object notifications {
    def notifiedUsersByNodes(nodesOfInterest: scala.collection.Seq[NodeId])(implicit ec: ExecutionContext): Future[List[NotifiedUsersRow]] = {
      ctx.run(
        infix"select * from notified_users_by_nodeid(${lift(nodesOfInterest)})"
          .as[Query[NotifiedUsersRow]]
      )
    }

    def updateNodesForConnectedUser(userId: UserId, nodeIds: scala.collection.Seq[NodeId])(implicit ec: ExecutionContext): Future[List[NodeId]] = {
      ctx.run(
        infix"select id from unnest(${lift(nodeIds)}::uuid[]) id where node_can_access(id, ${lift(userId)})".as[Query[NodeId]]
      )
    }

    def subscribeWebPush(subscription: WebPushSubscription)(implicit ec: ExecutionContext): Future[SuccessResult.type] = {
      val q = quote {
        query[WebPushSubscription]
          .insert(lift(subscription))
          .onConflictUpdate(_.endpointUrl, _.p256dh, _.auth)(
            (s, excluded) => s.userId -> excluded.userId
          ).returning(_.id)
      }

      ctx.run(q).map(_ => SuccessResult)
      //.flatMap(numberInserts => checkUnexpected(numberInserts == 1, s"Unexpected number of webpush subscription inserts of user '${subscription.userId.toUuid}': $numberInserts == 1"))
    }

    def cancelWebPush(endpointUrl: String, p256dh: String, auth: String)(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      ctx.run(
        query[WebPushSubscription]
          .filter(s => s.endpointUrl == lift(endpointUrl) && s.p256dh == lift(p256dh) && s.auth == lift(auth)).delete
      ).flatMap(numberDeletes => checkUnexpected(numberDeletes <= 1, s"Unexpected number of webpush subscription deletes: $numberDeletes <= 1"))
    }

    def delete(subscription: WebPushSubscription)(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = delete(List(subscription))
    def delete(subscriptions: Seq[WebPushSubscription])(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      ctx.run(
        liftQuery(subscriptions)
          .foreach(s => query[WebPushSubscription].filter(_.id == s.id).delete)
      ).flatMap(touched => checkUnexpected(touched.forall(_ <= 1), s"Unexpected number of webpush subscription deletes: ${touched.sum} <= ${subscriptions.size} - ${subscriptions.zip(touched)}"))
    }

    def getSubscription(userId: UserId)(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = getSubscriptions(userId :: Nil)
    def getSubscriptions(userIds: Seq[UserId])(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
      ctx.run {
        query[WebPushSubscription].filter(sub =>
          liftQuery(userIds) contains sub.userId
        )
      }
    }
    def getAllSubscriptions()(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
      ctx.run(query[WebPushSubscription])
    }
  }

  object edge {

    // Remember to use unique edge filter
    private val upsert = quote { e: Edge =>
      val q = query[Edge].insert(e)
      // if there is unique conflict, we update the data which might contain new values
      infix"$q ON CONFLICT(sourceid,(data->>'type'),coalesce(data->>'key', ''),targetid) WHERE data->>'type' <> 'Author' DO UPDATE SET data = EXCLUDED.data"
        .as[Insert[Edge]]
    }

    def create(edge: Edge)(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = create(List(edge))
    def create(edges: Seq[Edge])(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      if (edges.isEmpty) return Future.successful(SuccessResult)

      for {
        touched <- if(edges.nonEmpty) {
          ctx.run {
            liftQuery(edges).foreach(upsert(_))
          }
        } else Future.successful(Nil)
        // Ignored insert (on conflict do nothing) do not count as touched
        r <- checkUnexpected(touched.forall(_ <= 1), s"Unexpected number of edge inserts: ${touched.sum} / ${edges.size} - ${edges.zip(touched)}")
      } yield r
    }


    def delete(edge: Edge)(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = delete(List(edge))
    def delete(edges: Seq[Edge])(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      if (edges.isEmpty) return Future.successful(SuccessResult)

      val dbEdges: Seq[(NodeId, EdgeData.Type, String, NodeId)] = edges.map {
        case Data.Edge(sourceId, data: EdgeData.LabeledProperty, targetId) => (sourceId, data.tpe, data.key, targetId)
        case Data.Edge(sourceId, data, targetId) => (sourceId, data.tpe, null, targetId)
      }

      for {
        touched <- if(dbEdges.nonEmpty) {
          ctx.run {
            liftQuery(dbEdges).foreach { case (sourceId, tpe, key, targetId) =>
              query[Edge].filter(e => e.sourceId == sourceId && e.targetId == targetId && e.data.jsonType == tpe && infix"coalesce(${e.data}->>'key' = $key, true)".as[Boolean]).delete
            }
          }
        } else Future.successful(Nil)
        r <- checkUnexpected(touched.forall(_ <= 1), s"Unexpected number of edge deletes: ${touched.sum} <= ${edges.size} - ${edges.zip(touched)}")
      } yield r
    }
  }

  object user {

    def allMembershipConnections(userId: UserId): Quoted[Query[Edge]] = quote {
      for {
        user <- query[NodeRaw].filter(_.id == lift(userId))
        membershipConnection <- query[Edge].filter(c =>
          c.targetId == user.id && c.data.jsonType == lift(EdgeData.Member.tpe)
        )
      } yield membershipConnection
    }

    def allNodesQuery(userId: UserId): Quoted[Query[NodeRaw]] = quote {
      for {
        c <- allMembershipConnections(userId)
        p <- query[NodeRaw].join(p => p.id == c.sourceId)
      } yield p
    }

    def getAllNodes(userId: UserId)(implicit ec: ExecutionContext): Future[List[Node]] = ctx.run {
      allNodesQuery(userId)
    }.map(_.map(_.toNode))

    // TODO share code with createimplicit?
    def create(userId: UserId, name: String, email: String, passwordDigest: Array[Byte])(implicit ec: TransactionalExecutionContext): Future[User] = {
      val userData = NodeData.User(name = name, isImplicit = false, revision = 0)
      val user = User(userId, userData, NodeAccess.Level(AccessLevel.Restricted))
      val membership: EdgeData = EdgeData.Member(AccessLevel.ReadWrite)

      val q = quote {
        infix"""
        with insert_user as (insert into node (id,data,accesslevel) values(${lift(user.id)}, ${lift(user.data)}, ${lift(user.accessLevel)})),
             insert_user_member as (insert into edge (sourceid, data, targetid) values(${lift(userId)}, ${lift(membership)}, ${lift(userId)})),
             insert_user_detail as (insert into userdetail (userid, email, verified) values(${lift(userId)}, ${lift(email)}, ${lift(false)}))
             insert into password(userid, digest) VALUES(${lift(userId)}, ${lift(passwordDigest)})
      """.as[Insert[Node]]
      }

      ctx.run(q)
        .flatMap { numberInserts =>
          checkUnexpected(numberInserts == 1, user, s"Unexpected number of user inserts ${userId.toUuid}: $numberInserts / 1")
        }
    }

    def createImplicitUser(userId: UserId, name: String)(implicit ec: TransactionalExecutionContext): Future[User] = {
      val userData = NodeData.User(name = name, isImplicit = true, revision = 0)
      val user = User(userId, userData, NodeAccess.Level(AccessLevel.Restricted))
      val membership: EdgeData = EdgeData.Member(AccessLevel.ReadWrite)

      val q = quote {
        infix"""
        with insert_user as (insert into node (id,data,accesslevel) values(${lift(user.id)}, ${lift(user.data)}, ${lift(user.accessLevel)}))
             insert into edge (sourceid, data, targetId) values(${lift(userId)}, ${lift(membership)}, ${lift(userId)})
       """.as[Insert[Node]]
      }

      ctx.run(q)
        .flatMap { numberInserts =>
          checkUnexpected(numberInserts == 1, user, s"Unexpected number of user inserts ${userId.toUuid}: $numberInserts / 1")
        }
    }

    //TODO one query
    def activateImplicitUser(userId: UserId, name: String, email: String, passwordDigest: Array[Byte])(implicit ec: TransactionalExecutionContext ): Future[Option[User]] = {
      ctx.run(queryUser.filter(u => u.id == lift(userId) && u.data ->> "isImplicit" == "true")) //TODO: type safe
        .flatMap(_.headOption.fold(Future.successful(Option.empty[User])) { user =>
          val userData = user.data
          val updatedUser = user.copy(
            data = userData.copy(name = name, isImplicit = false, revision = userData.revision + 1)
          )
          for {
            numberUserInserts <- ctx.run(queryUser.filter(_.id == lift(userId)).update(lift(updatedUser)))
            numberPWInserts <- ctx.run(query[Password].insert(lift(Password(userId, passwordDigest))))
            numberUserDetailInserts <- ctx.run(query[UserDetail].insert(lift(UserDetail(userId, Some(email), verified = false))))
            u <- checkUnexpected(numberPWInserts == 1 && numberUserInserts == 1 && numberUserDetailInserts == 1, Option(updatedUser), s"Unexpected number of user/pw inserts ${userId.toUuid}: $numberUserInserts / 1, $numberPWInserts / 1, $numberUserDetailInserts / 1")
          } yield u
        })
    }

    def mergeImplicitUser(implicitId: UserId, userId: UserId)(implicit ec: TransactionalExecutionContext ): Future[Boolean] = {
      if (implicitId == userId) Future.successful(true)
      else get(implicitId).flatMap { user =>
        val isAllowed: Boolean = user.fold(false)(_.data.isImplicit)
        if (isAllowed) {
          val q = quote {
            infix"""select mergeFirstUserIntoSecond(${lift(implicitId)}, ${lift(userId)})"""
              .as[Delete[User]]
          }
          ctx.run(q).flatMap { numberInserts =>
            checkUnexpected(numberInserts == 1, true, s"Unexpected number of mergeUser inserts: $numberInserts / 1")
          }
        } else Future.successful(false)
      }
    }

    def get(id: UserId)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.run(
        queryUser
          .filter(p => p.id == lift(id))
          .take(1)
      ).map(_.headOption)
    }

    def verifyEmailAddress(userId: UserId, email: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      val q = quote {
        query[UserDetail]
          .filter(detail => detail.userId == lift(userId) && detail.email.exists(_ == lift(email)) && !detail.verified)
          .update(_.verified -> lift(true))
      }

      ctx.run(q).map(_ == 1)
    }
    def existsEmail(email: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      val q = quote {
        query[UserDetail]
          .filter(_.email.exists(_ == lift(email)))
          .take(1)
      }

      ctx.run(q).map(_.nonEmpty).recover { case _ => false }
    }

    //TODO: we should bump the revision of the user to invalidate tokens after email changes.
    def updateUserEmail(userId: UserId, email: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      val q = quote {
        query[UserDetail]
          .filter(_.userId == lift(userId))
          .update(_.email -> Some(lift(email)), _.verified -> lift(false))
      }

      ctx.run(q).map(_ == 1).recover { case _ => false }
    }

    def getUserDetail(id: UserId)(implicit ec: ExecutionContext): Future[Option[UserDetail]] = {
      ctx.run(
        query[UserDetail]
          .filter(p => p.userId == lift(id))
          .take(1)
      ).map(_.headOption)
    }

    def getUserByMail(email: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.run(
        (for{
         userDetail <- query[UserDetail].filter(p => p.email.contains(lift(email)))
         user <- queryUser.filter(_.id == userDetail.userId)
        } yield user).take(1)
      ).map(_.headOption)
    }

    def getUserByVerifiedMail(email: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.run(
        (for{
         userDetail <- query[UserDetail].filter(p => p.email.contains(lift(email)) && p.verified)
         user <- queryUser.filter(_.id == userDetail.userId)
        } yield user).take(1)
      ).map(_.headOption)
    }

    def getUserAndDigestByEmail(email: String)(implicit ec: ExecutionContext): Future[Option[(User, Array[Byte])]] = {
      ctx.run {
        (for{
         userDetail <- query[UserDetail].filter(p => p.email.contains(lift(email)))
         user <- queryUser.filter(_.id == userDetail.userId)
         password <- query[Password].filter(p => user.id == p.userId).map(_.digest)
        } yield (user, password)).take(1)
      }.map(_.headOption)
    }

    def changePassword(userId: UserId, digest: Array[Byte])(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      for {
        SuccessResult <- ctx.run(infix"update node set data = data || json_build_object('revision', (data->>'revision')::int + 1)::jsonb where data->>'type' = 'User' and id = ${lift(userId)}".as[Update[User]])
          .flatMap(userUpdated => checkUnexpected(userUpdated == 1, s"Unexpected number of user updates for user ${userId.toUuid}: $userUpdated / 1"))
        SuccessResult <- ctx.run(query[Password].filter(_.userId == lift(userId)).update(_.digest -> lift(digest)))
          .flatMap(passwordUpdated => checkUnexpected(passwordUpdated == 1, s"Unexpected number of password updates for user ${userId.toUuid}: $passwordUpdated / 1"))
      } yield SuccessResult
    }

    def byName(name: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx.run {
        queryUser
          .filter(u => u.data ->> "name" == lift(name) && u.data ->> "isImplicit" == "false")
          .take(1)
      }.map(_.headOption)
    }

    def checkIfUserAlreadyExists(userId: UserId)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run {
        queryUser
          .filter(u => u.id == lift(userId))
          .take(1)
      }.map(_.nonEmpty)
    }

    // this method is in integral part of our authentication checks. We have tokens which include a user.
    // this method tries to find a user in the db that matches the given user from the token exactly.
    // Same id (obviously), same revision (important! revision is bumped on pw changes), same implicit-state. (username may have changed).
    def checkIfEqualUserExists(user: SimpleUser)(implicit ec: ExecutionContext): Future[Option[User]] = {
      import user.data._
      ctx.run {
        queryUser
          .filter(u => u.id == lift(user.id) && u.data ->> "revision" == lift(revision.toString) && u.data ->> "isImplicit" == lift(isImplicit.toString)) // we do not check the user-name, as it can be changed by the user during a session.
          .take(1)
      }.map(_.headOption)
    }

    def canAccessNode(userId: UserId, nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = ctx.run {
      canAccessNodeQuery(lift(nodeId), lift(userId))
    }

    def canAccessNodeViaUrl(userId: UserId, nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = ctx.run {
      canAccessNodeViaUrlQuery(lift(userId), lift(nodeId))
    }

    def inaccessibleNodes(userId: UserId, nodeIds: scala.collection.Seq[NodeId])(implicit ec: ExecutionContext): Future[Seq[NodeId]] = ctx.run {
      inaccessibleNodesQuery(lift(userId), lift(nodeIds))
    }

    private val canAccessNodeQuery = quote { (nodeId: NodeId, userId: UserId) =>
      // TODO why not as[Query[Boolean]] like other functions?
      infix"select node_can_access($nodeId, $userId)".as[Boolean]
    }

    private val canAccessNodeViaUrlQuery = quote { (userId: UserId, nodeId: NodeId) =>
      infix"select can_access_node_via_url($userId, $nodeId)".as[Boolean]
    }

    private val inaccessibleNodesQuery = quote { (userId: UserId, nodeIds: scala.collection.Seq[NodeId]) =>
      infix"select * from inaccessible_nodes($userId, $nodeIds)".as[Query[NodeId]]
    }
  }

  object graph {
    private val graphPage = quote { (parents: Seq[NodeId], requestingUserId: UserId) =>
      infix"select * from graph_page($parents, $requestingUserId)"
        .as[Query[GraphRow]]
    }

    def getPage(parentIds: Seq[NodeId], requestingUserId: UserId)(implicit ec: ExecutionContext): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx.run {
        graphPage(lift(parentIds), lift(requestingUserId))
      }.map(Graph.from)
    }
  }

  object feature {
    def getUsedFeaturesForUser(userId: UserId)(implicit ec:ExecutionContext): Future[List[UsedFeature]] = {
      ctx.run {
        query[UsedFeature]
          .filter(_.userId == lift(userId))
      }
    }

    def useFeatureForFirstTime(usedFeature: UsedFeature)(implicit ec:ExecutionContext): Future[Boolean] = {
      ctx.run {
        query[UsedFeature]
          .insert(lift(usedFeature))
          .onConflictIgnore
      }.map(_ == 1)
    }
  }

  object oAuthClients {
    def create(client: OAuthClient)(implicit ec:ExecutionContext): Future[Boolean] = {
      ctx.run(
        query[OAuthClient]
          .insert(lift(client))
          .onConflictUpdate(_.userId, _.service)(
            (node, excluded) => node.accessToken -> excluded.accessToken
          )
      ).map(_ == 1)
    }

    def get(userId: UserId, service: OAuthClientService)(implicit ec:ExecutionContext): Future[List[OAuthClient]] = get(userId :: Nil, service)
    def get(userIds: Seq[UserId], service: OAuthClientService)(implicit ec:ExecutionContext): Future[List[OAuthClient]] = {
      ctx.run(
        query[OAuthClient]
          .filter(client => liftQuery(userIds).contains(client.userId) && client.service == lift(service))
      )
    }

    def getAll(userId: UserId)(implicit ec:ExecutionContext): Future[Seq[OAuthClientService]] = {
      ctx.run(
        query[OAuthClient]
          .filter(client => client.userId == lift(userId))
          .map(_.service)
      )
    }

    def delete(clients: Seq[OAuthClient])(implicit ec: TransactionalExecutionContext): Future[SuccessResult.type] = {
      ctx.run(
        liftQuery(clients)
          .foreach(s => query[OAuthClient].filter(client => client.userId == s.userId && client.service == s.service && client.accessToken == s.accessToken).delete)
      ).flatMap(touched => checkUnexpected(touched.forall(_ <= 1), s"Unexpected number of oauth client deletes: ${ touched.sum } <= ${ clients.size } - ${ clients.zip(touched) }"))
    }

    def delete(userId: UserId, service: OAuthClientService)(implicit ec:ExecutionContext): Future[Boolean] = {
      ctx.run(
        query[OAuthClient]
          .filter(client => client.userId == lift(userId) && client.service == lift(service))
          .delete
      ).map(_ == 1)
    }
  }
}
