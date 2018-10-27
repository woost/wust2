package wust.db

import java.util.Date

import com.typesafe.config.Config
import io.getquill._
import io.circe.parser._
import io.circe.syntax._
import supertagged._
import wust.ids._
import wust.ids.serialize.Circe._
import wust.util._

import scala.concurrent.{ExecutionContext, Future}

object Db {
  def apply(config: Config) = {
    new Db(new PostgresAsyncContext(LowerCase, config))
  }
}

// all database operations
class Db(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCoreCodecs(ctx) {
  import Data._
  import ctx._

  // schema meta: we can define how a type corresponds to a db table
  private implicit val userSchema = schemaMeta[User]("node") // User type is stored in node table with same properties.
  // enforce check of json-type for extra safety. additional this makes sure that partial indices on user.data are used.
  private val queryUser = quote { query[User].filter(_.data.jsonType == lift(NodeData.User.tpe)) }

  //TODO should actually rollback transactions when batch action had partial error
  // ^does anybody know what this is about?
  object node {
    // node ids are unique, so the methods can assume that at max 1 row was touched in each operation
    def create(node: Node)(implicit ec: ExecutionContext): Future[Boolean] = create(List(node))
    def create(nodes: Iterable[Node])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
      // if there is an id conflict, we update the post.
      // this is fine, because we always check permissions before creating new nodes.
      // non-exisiting ids are automatically allowed.
      // important: the permission checks must run in the same transaction.
        .run(liftQuery(nodes).foreach {
          query[Node]
            .insert(_)
            .onConflictUpdate(_.id)(
              (node, excluded) => node.data -> excluded.data,
              (node, excluded) => node.accessLevel -> excluded.accessLevel
            )
        })
        .map(_.forall(_ <= 1))
    }

    private val canAccess = quote { (userId: UserId, nodeId: NodeId) =>
      infix"""
                can_access_node($userId, $nodeId)
           """.as[Boolean]
    }

    def get(userId: UserId, nodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[Node]] = {
      ctx
        .run(query[Node].filter( accessedNode =>
          accessedNode.id == lift(nodeId) &&
            canAccess(lift(userId), lift(nodeId))
        ).take(1))
        .map(_.headOption)
    }

    def get(nodeIds: Set[NodeId])(implicit ec: ExecutionContext): Future[List[Node]] = {
      //TODO
      //ctx.run(query[Node].filter(p => liftQuery(nodeIds) contains p.id))
      val q = quote {
        infix"""
        select node.* from unnest(${lift(nodeIds.toList)} :: uuid[]) inputNodeId join node on node.id = inputNodeId
      """.as[Query[Node]]
      }

      ctx.run(q)
    }

    def update(node: Node)(implicit ec: ExecutionContext): Future[Boolean] = update(Set(node))
    def update(nodes: Iterable[Node])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
        .run(
          liftQuery(nodes.toList)
            .foreach(
              node =>
                query[Node]
                  .filter(_.id == node.id)
                  .update(
                    _.data -> node.data,
                    _.accessLevel -> node.accessLevel
                  )
            )
        )
        .map(_.forall(_ == 1))
    }

    def getMembers(nodeId: NodeId)(implicit ec: ExecutionContext): Future[List[User]] = {
      ctx.run {
        for {
          membershipConnection <- query[MemberEdge].filter(
            c => c.targetId == lift(nodeId) && c.data.jsonType == lift(EdgeData.Member.tpe)
          )
          userNode <- queryUser.filter(_.id == membershipConnection.sourceId)
        } yield userNode
      }
    }

    def addMember(nodeId: NodeId, userId: UserId, accessLevel: AccessLevel)(
        implicit ec: ExecutionContext
    ): Future[Boolean] = addMember(nodeId :: Nil, userId, accessLevel).map(_.nonEmpty)
    def addMember(nodeIds: List[NodeId], userId: UserId, accessLevel: AccessLevel)(
        implicit ec: ExecutionContext
    ): Future[Seq[NodeId]] = {
      val insertMembership = quote { nodeId: NodeId =>
        infix"""
          insert into edge(sourceid, data, targetid) values
          (${lift(userId)}, jsonb_build_object('type', 'Member', 'level', ${lift(accessLevel)}::accesslevel), ${nodeId})
          ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE data->>'type' NOT IN('Author', 'Before') DO UPDATE set data = EXCLUDED.data
        """.as[Insert[Edge]].returning(_.targetId)
      }
      ctx.run(liftQuery(nodeIds).foreach(insertMembership(_)))
    }
  }

  object notifications {
    def notifiedNodesForUser(userId: UserId, nodeIds: Set[NodeId])(implicit ec: ExecutionContext): Future[List[NodeId]] = {
      ctx.run {
        infix"select nodeid from notified_nodes_for_user(${lift(userId)}, ${lift(nodeIds.toList)}::uuid[])".as[Query[NodeId]]
      }
    }

    def updateNodesForConnectedUser(userId: UserId, nodeIds: Set[NodeId])(implicit ec: ExecutionContext): Future[List[NodeId]] = {
      ctx.run(
        infix"select id from unnest(${lift(nodeIds.toList)}::uuid[]) id where can_access_node(${lift(userId)}, id)".as[Query[NodeId]]
      )
    }

    def subscribeWebPush(
        subscription: WebPushSubscription
    )(implicit ec: ExecutionContext): Future[Boolean] = {
      val q = quote {
        query[WebPushSubscription]
          .insert(lift(subscription))
          .onConflictUpdate(_.endpointUrl, _.p256dh, _.auth)(
            (s, excluded) => s.userId -> excluded.userId
          ).returning(_.id)
      }

      ctx.run(q)
        .map(_ => true)
        .recoverValue(false)
    }

    def cancelWebPush(endpointUrl: String, p256dh: String, auth: String)(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(
        query[WebPushSubscription]
          .filter(s => s.endpointUrl == lift(endpointUrl) && s.p256dh == lift(p256dh) && s.auth == lift(auth)).delete
      ).map(_ == 1)
    }

    def delete(subscription: WebPushSubscription)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(subscription))
    def delete(subscriptions: Set[WebPushSubscription])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(
        liftQuery(subscriptions.toList)
          .foreach(s => query[WebPushSubscription].filter(_.id == s.id).delete)
      ).map(_.forall(_ == 1))
    }

    def getSubscriptions(
        userIds: Set[UserId]
    )(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
      ctx.run {
        for {
          s <- query[WebPushSubscription].filter(
            sub => liftQuery(userIds.toList) contains sub.userId
          )
        } yield s
      }
    }
    def getAllSubscriptions()(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
      ctx.run(query[WebPushSubscription])
    }
    //
    // def getSubscriptions(nodeIds: Set[NodeId])(implicit ec: ExecutionContext): Future[List[WebPushSubscription]] = {
    //   ctx.run{
    //     for {
    //       // m <- query[Membership].filter(m => liftQuery(nodeIds.toList).contains(m.nodeId))
    //       s <- query[WebPushSubscription]//.filter(_.userId == m.userId)
    //     } yield s
    //   }
    // }
  }

  object edge {

    // Remember to use unique edge filter
//    private val uniqueEdgeFilter: String = "(data ->> 'type'::text) <> ALL (ARRAY['Author'::text, 'Before'::text])"

    private val upsert = quote { e: Edge =>
      val q = query[Edge].insert(e)
      // if there is unique conflict, we update the data which might contain new values
      infix"$q ON CONFLICT(sourceid,(data->>'type'),targetid) WHERE (data ->> 'type'::text) <> ALL (ARRAY['Author'::text, 'Before'::text]) DO UPDATE SET data = EXCLUDED.data"
        .as[Insert[Edge]]
    }

    private val insertBefore = quote { e: Edge =>
      val q = query[Edge].insert(e)
      infix"$q ON CONFLICT(sourceid,(data->>'type'),(data->>'parent'),targetid) WHERE data->>'type'='Before' DO NOTHING"
        .as[Insert[Edge]]
    }

    def create(edge: Edge)(implicit ec: ExecutionContext): Future[Boolean] = create(List(edge))
    def create(edges: Iterable[Edge])(implicit ec: ExecutionContext): Future[Boolean] = {
      val (beforeEdges, remainingEdges) = edges.partition(e => e.data.isInstanceOf[EdgeData.Before])

      ctx.transaction( implicit ec =>
        for {
          numBefore <- if(beforeEdges.nonEmpty) {
            ctx.run {
              liftQuery(beforeEdges.toList)
                .foreach(
                  insertBefore(_)
                )
            }
          } else Future.successful(Nil)
          numRemain <- if(remainingEdges.nonEmpty) {
            ctx.run {
              liftQuery(remainingEdges.toList).foreach(
                upsert(_)
              )
            }
          } else Future.successful(Nil)
        } yield numBefore.forall(_ <= 1) && numRemain.forall(_ <= 1)
      ).recoverValue(false)
    }


    def delete(edge: Edge)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(edge))
    def delete(edges: Iterable[Edge])(implicit ec: ExecutionContext): Future[Boolean] = {
      val beforeEdges = edges.collect{case e if e.data.isInstanceOf[EdgeData.Before] =>
        val beforeData = e.data.asInstanceOf[EdgeData.Before]
        (e.sourceId, beforeData.parent, e.targetId)
      }

      val remainingEdges = edges.collect { case e if !e.data.isInstanceOf[EdgeData.Before] =>
        (e.sourceId, e.targetId)
      }

      // tuple
      ctx.transaction( implicit ec =>
        for {
          numBefore <- if(beforeEdges.nonEmpty) {
            ctx.run {
              liftQuery(beforeEdges.toList)
                .foreach { case (sourceId, parent, targetId) =>
                  val q = query[Edge].filter(e => e.sourceId == sourceId && e.targetId == targetId && e.data.jsonParent == parent).delete
                  infix"$q AND data->>'type' = 'Before'".as[Delete[Edge]]
                }
            }
          } else Future.successful(Nil)
          numRemain <- if(remainingEdges.nonEmpty) {
            ctx.run {
              liftQuery(remainingEdges.toList).foreach { case (sourceId, targetId) =>
                val q = query[Edge].filter(e => e.sourceId == sourceId && e.targetId == targetId).delete
                infix"$q AND (data ->> 'type'::text) <> ALL (ARRAY['Author'::text, 'Before'::text])".as[Delete[Edge]]
              }
            }
          } else Future.successful(Nil)
        } yield numBefore.forall(_ <= 1) && numRemain.forall(_ <= 1)
      ).recoverValue(false)

    }

  }

  object user {

    def allMembershipConnections(userId: UserId): Quoted[Query[Edge]] = quote {
      for {
        user <- query[Node].filter(_.id == lift(userId))
        membershipConnection <- query[Edge].filter(
          c => c.sourceId == user.id && c.data.jsonType == lift(EdgeData.Member.tpe)
        )
      } yield membershipConnection
    }

    def allNodesQuery(userId: UserId): Quoted[Query[Node]] = quote {
      for {
        c <- allMembershipConnections(userId)
        p <- query[Node].join(p => p.id == c.targetId)
      } yield p
    }

    def getAllNodes(userId: UserId)(implicit ec: ExecutionContext): Future[List[Node]] = ctx.run {
      allNodesQuery(userId)
    }

    // TODO share code with createimplicit?
    def create(userId: UserId, name: String, digest: Array[Byte])(
        implicit ec: ExecutionContext
    ): Future[Option[User]] = {
      val userData = NodeData.User(name = name, isImplicit = false, revision = 0)
      val user = User(userId, userData, NodeAccess.Level(AccessLevel.Restricted))
      val membership: EdgeData = EdgeData.Member(AccessLevel.ReadWrite)

      val q = quote {
        infix"""
        with insert_user as (insert into node (id,data,accesslevel) values(${lift(user.id)}, ${lift(user.data)}, ${lift(user.accessLevel)})),
             insert_user_member as (insert into edge (sourceid, data, targetid) values(${lift(userId)}, ${lift(membership)}, ${lift(userId)}))
             insert into password(userid, digest) VALUES(${lift(userId)}, ${lift(digest)})
      """.as[Insert[Node]]
      }

      ctx
        .run(q)
        .collect { case 1 => Option(user) }
        .recoverValue(None)
    }

    def createImplicitUser(userId: UserId, name: String)(
        implicit ec: ExecutionContext
    ): Future[Option[User]] = {
      val userData = NodeData.User(name = name, isImplicit = true, revision = 0)
      val user = User(userId, userData, NodeAccess.Level(AccessLevel.Restricted))
      val membership: EdgeData = EdgeData.Member(AccessLevel.ReadWrite)

      val q = quote {
        infix"""
        with insert_user as (insert into node (id,data,accesslevel) values(${lift(user.id)}, ${lift(user.data)}, ${lift(user.accessLevel)}))
             insert into edge (sourceid, data, targetId) values(${lift(userId)}, ${lift(membership)}, ${lift(userId)})
     """.as[Insert[Node]]
      }
      ctx
        .run(q)
        .collect { case 1 => Option(user) }
        .recoverValue(None)
    }

    //TODO one query
    def activateImplicitUser(id: UserId, name: String, passwordDigest: Array[Byte])(
        implicit ec: ExecutionContext
    ): Future[Option[User]] = {
      ctx
        .transaction { implicit ec =>
          ctx
            .run(queryUser.filter(u => u.id == lift(id) && u.data ->> "isImplicit" == "true")) //TODO: type safe
            .flatMap(
              _.headOption
                .map { user =>
                  val userData = user.data
                  val updatedUser = user.copy(
                    data = userData
                      .copy(name = name, isImplicit = false, revision = userData.revision + 1)
                  )
                  for {
                    _ <- ctx.run(queryUser.filter(_.id == lift(id)).update(lift(updatedUser)))
                    _ <- ctx.run(query[Password].insert(lift(Password(id, passwordDigest))))
                  } yield Option(updatedUser)
                }
                .getOrElse(Future.successful(None))
            )
        }
        .recoverValue(None)
    }
    //TODO: one query.
    // def activateImplicitUser(id: UserId, name: String, digest: Array[Byte])(implicit ec: ExecutionContext): Future[Option[User]] = {
    //    val user = newRealUser(name)
    //    val q = quote { s"""
    //      with existingUser as (
    //        UPDATE "user" SET isimplicit = true, revision = revision + 1 WHERE id = ${lift(id)} and isimplicit = true RETURNING revision
    //      )
    //      INSERT INTO password(id, digest) values(${lift(id)}, ${lift(digest)}) RETURNING existingUser.revision;
    //    """}

    //    ctx.executeActionReturning(q, identity, _(0).asInstanceOf[Int], "revision")
    //      .map(rev => Option(user.copy(revision = rev)))
    //      // .recoverValue(None)
    // }

    def mergeImplicitUser(implicitId: UserId, userId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Boolean] = {
      if (implicitId == userId) Future.successful(false)
      else
        ctx.transaction { implicit ec =>
          get(implicitId).flatMap { user =>
            val isAllowed: Boolean = user.fold(false)(_.data.asInstanceOf[NodeData.User].isImplicit)
            if (isAllowed) {
              val q = quote {
                infix"""select mergeFirstUserIntoSecond(${lift(implicitId)}, ${lift(userId)})"""
                  .as[Delete[User]]
              }
              ctx.run(q).map(_ == 1)
            } else Future.successful(false)
          }
        }
    }

    def get(id: UserId)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx
        .run(
          queryUser
            .filter(p => p.id == lift(id) && p.data.jsonType == lift(NodeData.User.tpe))
            .take(1)
        )
        .map(_.headOption)
    }

    def getUserAndDigest(
        name: String
    )(implicit ec: ExecutionContext): Future[Option[(User, Array[Byte])]] = {
      ctx
        .run {
          queryUser
            .filter(_.data ->> "name" == lift(name))
            .join(query[Password])
            .on((u, p) => u.id == p.userId)
            .map { case (u, p) => (u, p.digest) }
            .take(1)
        }
        .map(_.headOption)
    }

    //TODO: we should update the revision of the user here, too. something
    //changed and we could thereby invalidate existing tokens
    def changePassword(userId: UserId, digest: Array[Byte])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx.run(
        query[Password].filter(_.userId == lift(userId)).update(_.digest -> lift(digest))
      ).map(_ => true).recoverValue(false)
    }

    def byName(name: String)(implicit ec: ExecutionContext): Future[Option[User]] = {
      ctx
        .run {
          queryUser
            .filter(u => u.data ->> "name" == lift(name) && u.data ->> "isImplicit" == "false")
            .take(1)
        }
        .map(_.headOption)
    }

    def checkIfEqualUserExists(user: SimpleUser)(implicit ec: ExecutionContext): Future[Boolean] = {
      import user.data._
      ctx
        .run {
          queryUser
            .filter(
              u =>
                u.id == lift(user.id) && u.data ->> "revision" == lift(revision.toString) && u.data ->> "isImplicit" == lift(
                  isImplicit.toString
                ) && u.data ->> "name" == lift(name)
            )
            .take(1)
        }
        .map(_.nonEmpty)
    }

    def canAccessNode(userId: UserId, nodeId: NodeId)(
        implicit ec: ExecutionContext
    ): Future[Boolean] = ctx.run {
      canAccessNodeQuery(lift(userId), lift(nodeId))
    }

    def inaccessibleNodes(userId: UserId, nodeIds: List[NodeId])(
        implicit ec: ExecutionContext
    ): Future[Seq[NodeId]] = ctx.run {
      inaccessibleNodesQuery(lift(userId), lift(nodeIds.toList))
    }

    private val canAccessNodeQuery = quote { (userId: UserId, nodeId: NodeId) =>
      // TODO why not as[Query[Boolean]] like other functions?
      infix"select * from can_access_node($userId, $nodeId)".as[Boolean]
    }

    private val inaccessibleNodesQuery = quote { (userId: UserId, nodeIds: List[NodeId]) =>
      infix"select * from inaccessible_nodes($userId, $nodeIds)".as[Seq[NodeId]]
    }
  }

  object graph {
    private val graphPage = quote {
      (parents: Seq[NodeId], children: Seq[NodeId], requestingUserId: UserId) =>
        infix"select * from graph_page($parents, $children, $requestingUserId)"
          .as[Query[GraphRow]]
    }
    private val graphPageWithOrphans = quote {
      (parents: Seq[NodeId], children: Seq[NodeId], requestingUserId: UserId) =>
        infix"select * from graph_page_with_orphans($parents, $children, $requestingUserId)"
          .as[Query[GraphRow]]
    }

    def getPage(parentIds: Seq[NodeId], childIds: Seq[NodeId], requestingUserId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx.run {
        graphPage(lift(parentIds), lift(childIds), lift(requestingUserId))
      }.map(Graph.from)
    }

    def getPageWithOrphans(parentIds: Seq[NodeId], childIds: Seq[NodeId], requestingUserId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx.run {
        graphPageWithOrphans(lift(parentIds), lift(childIds), lift(requestingUserId))
      }.map(Graph.from)
    }
  }
}
