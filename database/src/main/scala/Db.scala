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
class Db(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCodecs(ctx) {
  import Data._
  import ctx._

  // schema meta: we can define how a type corresponds to a db table
  private implicit val userSchema = schemaMeta[User]("node") // User type is stored in node table with same properties.
  // enforce check of json-type for extra safety. additional this makes sure that partial indices on user.data are used.
  private val queryUser = quote { query[User].filter(_.data.jsonType == lift(NodeData.User.tpe)) }

  //TODO should actually rollback transactions when batch action had partial error
  object node {
    // node ids are unique, so the methods can assume that at max 1 row was touched in each operation

    //TODO need to check rights before we can do this
    private val insert = quote { node: Node =>
      val q = query[Node].insert(node)
      // when adding a new node, we undelete it in case it was already there
      //TODO this approach hides conflicts on node ids!!
      //TODO what about title
      //TODO can undelete nodes that i do not own
      infix"$q ON CONFLICT(id) DO UPDATE SET deleted = ${lift(DeletedDate.NotDeleted.timestamp)}"
        .as[Insert[Node]]
    }

    def create(node: Node)(implicit ec: ExecutionContext): Future[Boolean] = create(List(node))
    def create(nodes: Iterable[Node])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
        .run(liftQuery(nodes).foreach(insert(_)))
        .map(_.forall(_ <= 1))
    }

    def get(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Option[Node]] = {
      ctx
        .run(query[Node].filter(_.id == lift(nodeId)).take(1))
        .map(_.headOption)
    }

    def get(nodeIds: Set[NodeId])(implicit ec: ExecutionContext): Future[List[Node]] = {
      //TODO
      //ctx.run(query[Node].filter(p => liftQuery(nodeIds) contains p.id))
      val q = quote {
        infix"""
        select node.* from unnest(${lift(nodeIds.toList)} :: varchar(36)[]) inputNodeId join node on node.id = inputNodeId
      """.as[Query[Node]]
      }

      ctx.run(q)
    }

    def update(node: Node)(implicit ec: ExecutionContext): Future[Boolean] = update(Set(node))
    def update(nodes: Iterable[Node])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
        .run(
          liftQuery(nodes.toList)
            .foreach(node => query[Node].filter(_.id == node.id).update(_.data -> node.data))
        )
        .map(_.forall(_ == 1))
    }

    //TODO delete should be part of update?
    def delete(nodeId: NodeId)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(nodeId))
    def delete(nodeId: NodeId, when: DeletedDate)(implicit ec: ExecutionContext): Future[Boolean] =
      delete(Set(nodeId), when)
    def delete(nodeIds: Iterable[NodeId], when: DeletedDate = DeletedDate.Deleted(EpochMilli.now))(
        implicit ec: ExecutionContext
    ): Future[Boolean] = {
      ctx
        .run(
          liftQuery(nodeIds.toList)
            .foreach(nodeId => query[Node].filter(_.id == nodeId).update(_.deleted -> lift(when)))
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

    def addMemberWithCurrentJoinLevel(nodeId: NodeId, userId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Option[AccessLevel]] =
      addMemberWithCurrentJoinLevel(nodeId :: Nil, userId).map(_.headOption.map {
        case (nodeId, level) => level
      })
    def addMemberWithCurrentJoinLevel(nodeIds: List[NodeId], userId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Seq[(NodeId, AccessLevel)]] = {
      val now = EpochMilli.now
      val insertMembership = quote {
        // val membershipConnectionsToBeCreated = for {
        //   user <- query[Node].filter(_.id == lift(userId))
        //   node = query[Node].filter(p => liftQuery(nodeIds).contains(p.id) && lift(now) < p.joinDate)
        //   connection <- node.map(p => Connection(user.id, ConnectionData.Member(p.joinLevel), p.id))
        // } yield connection
        //
        //
        val membershipConnectionsToBeCreated =
          infix"""SELECT x22.id, jsonb_build_object('type', 'Member', 'level', p.joinlevel), p.id FROM node as x22, node p WHERE x22.id = ${lift(
            userId
          )} AND p.id = ANY(${lift(nodeIds)}) AND ${lift(now)} < p.joindate"""
        // don't lower permission level

        infix"""
          insert into edge(sourceid, data, targetid)
          $membershipConnectionsToBeCreated
          ON CONFLICT(sourceid,(data->>'type'),targetid) DO UPDATE set data =
            CASE EXCLUDED.data->>'level'  WHEN 'read' THEN edge.data
                                          WHEN 'readwrite' THEN EXCLUDED.data
            END
        """.as[Insert[Edge]]
        //TODO: https://github.com/getquill/quill/issues/1093
        // returning nodeid
        // """.as[ActionReturning[Membership, NodeId]]
      }

      // val r = ctx.run(liftQuery(nodeIds).foreach(insertMembership(_)))
      ctx
        .run(insertMembership)
        //TODO: fix query with returning
        .map { _ =>
          nodeIds.map(p => (p, AccessLevel.Read))
        } //TODO: this is fake data
    }

    def addMemberEvenIfLocked(nodeId: NodeId, userId: UserId, accessLevel: AccessLevel)(
        implicit ec: ExecutionContext
    ): Future[Boolean] = addMemberEvenIfLocked(nodeId :: Nil, userId, accessLevel).map(_.nonEmpty)
    def addMemberEvenIfLocked(nodeIds: List[NodeId], userId: UserId, accessLevel: AccessLevel)(
        implicit ec: ExecutionContext
    ): Future[Seq[NodeId]] = {
      val insertMembership = quote { nodeId: NodeId =>
        infix"""
          insert into edge(sourceid, data, targetid) values
          (${lift(userId)}, jsonb_build_object('type', 'Member', 'level', ${lift(accessLevel)}::accesslevel), ${nodeId})
          ON CONFLICT(sourceid,(data->>'type'),targetid) DO UPDATE set data =
            CASE EXCLUDED.data->>'level' WHEN 'read' THEN edge.data
                                        WHEN 'readwrite' THEN EXCLUDED.data
            END
        """.as[Insert[Edge]].returning(_.targetId)
      }
      ctx.run(liftQuery(nodeIds).foreach(insertMembership(_)))
    }
    def setJoinDate(nodeId: NodeId, joinDate: JoinDate)(
        implicit ec: ExecutionContext
    ): Future[Boolean] = {
      ctx
        .run(query[Node].filter(_.id == lift(nodeId)).update(_.joinDate -> lift(joinDate)))
        .map(_ == 1)
    }
  }

  object notifications {
    private case class NotifiedUsersData(userId: UserId, nodeIds: List[NodeId])
    private def notifiedUsersFunction(nodeIds: List[NodeId]) = quote {
      infix"select * from notified_users(${lift(nodeIds)})".as[Query[NotifiedUsersData]]
    }

    def notifiedUsers(
        nodeIds: Set[NodeId]
    )(implicit ec: ExecutionContext): Future[Map[UserId, List[NodeId]]] = {
      ctx
        .run {
          notifiedUsersFunction(nodeIds.toList).map(d => d.userId -> d.nodeIds)
        }
        .map(_.toMap)
    }

    def subscribeWebPush(
        subscription: WebPushSubscription
    )(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
        .run(query[WebPushSubscription].insert(lift(subscription)).returning(_.id))
        .map(_ => true)
        .recoverValue(false)
    }

    def delete(
        subscriptions: Set[WebPushSubscription]
    )(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
        .run(
          liftQuery(subscriptions.toList)
            .foreach(s => query[WebPushSubscription].filter(_.id == s.id).delete)
        )
        .map(_.forall(_ == 1))
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
    private val insert = quote { c: Edge =>
      val q = query[Edge].insert(c)
      // if there is unique conflict, we update the data which might contain new values
      infix"$q ON CONFLICT(sourceid,(data->>'type'),targetid) DO UPDATE SET data = EXCLUDED.data"
        .as[Insert[Edge]]
    }

    def apply(edge: Edge)(implicit ec: ExecutionContext): Future[Boolean] = apply(List(edge))
    def apply(edges: Iterable[Edge])(implicit ec: ExecutionContext): Future[Boolean] = {
      ctx
        .run(liftQuery(edges.toList).foreach(insert(_)))
        .map(_.forall(_ <= 1))
        .recoverValue(false)
    }

    def delete(edge: Edge)(implicit ec: ExecutionContext): Future[Boolean] = delete(Set(edge))
    def delete(edges: Iterable[Edge])(implicit ec: ExecutionContext): Future[Boolean] = {
      val data = edges.map(c => (c.sourceId, c.data.tpe, c.targetId))
      ctx
        .run(liftQuery(data.toList).foreach {
          case (sourceId, tpe, targetId) =>
            query[Edge]
              .filter(
                c => c.sourceId == sourceId && c.data.jsonType == tpe && c.targetId == targetId
              )
              .delete
        })
        .map(_.forall(_ <= 1))
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
    def create(userId: UserId, name: String, digest: Array[Byte], channelNodeId: NodeId)(
        implicit ec: ExecutionContext
    ): Future[Option[User]] = {
      val channelNode = Node(
        channelNodeId,
        NodeData.Channels,
        DeletedDate.NotDeleted,
        JoinDate.Never,
        AccessLevel.ReadWrite
      )
      val userData =
        NodeData.User(name = name, isImplicit = false, revision = 0, channelNodeId = channelNode.id)
      val user =
        User(userId, userData, DeletedDate.NotDeleted, JoinDate.Never, AccessLevel.ReadWrite)
      val membership: EdgeData = EdgeData.Member(AccessLevel.Read)

      val q = quote {
        infix"""
        with insert_channelnode as (insert into node (id,data,deleted,joindate,joinlevel) values (${lift(
          channelNode.id
        )}, ${lift(channelNode.data)}, ${lift(channelNode.deleted)}, ${lift(channelNode.joinDate)}, ${lift(
          channelNode.joinLevel
        )})),
             insert_user as (insert into node (id,data,deleted,joindate,joinlevel) values(${lift(
          user.id
        )}, ${lift(user.data)}, ${lift(user.deleted)}, ${lift(user.joinDate)}, ${lift(
          user.joinLevel
        )})),
             ins_m_cp as (insert into edge (sourceid, data, targetid) values(${lift(userId)}, ${lift(
          membership
        )}, ${lift(channelNodeId)})),
             ins_m_up as (insert into edge (sourceid, data, targetid) values(${lift(userId)}, ${lift(
          membership
        )}, ${lift(userId)}))
                      insert into password(userid, digest) select id, ${lift(digest)}
      """.as[Insert[Node]]
      }

      ctx
        .run(q)
        .collect { case 1 => Option(user) }
        .recoverValue(None)
    }

    def createImplicitUser(userId: UserId, name: String, channelNodeId: NodeId)(
        implicit ec: ExecutionContext
    ): Future[Option[User]] = {
      val channelNode = Node(
        channelNodeId,
        NodeData.Channels,
        DeletedDate.NotDeleted,
        JoinDate.Never,
        AccessLevel.ReadWrite
      )
      val userData =
        NodeData.User(name = name, isImplicit = true, revision = 0, channelNodeId = channelNode.id)
      val user =
        User(userId, userData, DeletedDate.NotDeleted, JoinDate.Never, AccessLevel.ReadWrite)
      val membership: EdgeData = EdgeData.Member(AccessLevel.Read)

      val q = quote {
        infix"""
        with insert_channelnode as (insert into node (id,data,deleted,joindate,joinlevel) values (${lift(
          channelNode.id
        )}, ${lift(channelNode.data)}, ${lift(channelNode.deleted)}, ${lift(channelNode.joinDate)}, ${lift(
          channelNode.joinLevel
        )})),
             insert_user as (insert into node (id,data,deleted,joindate,joinlevel) values(${lift(
          user.id
        )}, ${lift(user.data)}, ${lift(user.deleted)}, ${lift(user.joinDate)}, ${lift(
          user.joinLevel
        )})),
             ins_m_cp as (insert into edge (sourceId, data, targetId) values(${lift(userId)}, ${lift(
          membership
        )}, ${lift(channelNodeId)}))
                      insert into edge (sourceId, data, targetId) values(${lift(userId)}, ${lift(
          membership
        )}, ${lift(userId)})
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

    def isMember(nodeId: NodeId, userId: UserId, minAccessLevel: AccessLevel)(
        implicit ec: ExecutionContext
    ): Future[Boolean] = {
      //TODO: move these mappings into AccessLevel
      val allowedLevels: List[String] = minAccessLevel match {
        case AccessLevel.Read      => AccessLevel.Read.str :: AccessLevel.ReadWrite.str :: Nil
        case AccessLevel.ReadWrite => AccessLevel.ReadWrite.str :: Nil
      }
      def find(allowedLevels: List[String]) = quote {
        (for {
          user <- query[Node].filter(_.id == lift(userId))
          connectionExists <- query[Edge].filter(
            c =>
              c.targetId == lift(nodeId) && c.sourceId == user.id && c.data.jsonType == lift(
                EdgeData.Member.tpe
              ) && lift(allowedLevels).contains(c.data ->> "level")
          )
        } yield connectionExists).nonEmpty
      }
      ctx.run(find(allowedLevels))
    }
  }

  object graph {
    private def graphPage(parents: Seq[NodeId], children: Seq[NodeId], requestingUserId: UserId) =
      quote {
        infix"select * from graph_page(${lift(parents)}, ${lift(children)}, ${lift(requestingUserId)})"
          .as[Query[GraphRow]]
      }
    private def graphPageWithOrphans(
        parents: Seq[NodeId],
        children: Seq[NodeId],
        requestingUserId: UserId
    ) = quote {
      infix"select * from graph_page_with_orphans(${lift(parents)}, ${lift(children)}, ${lift(requestingUserId)})"
        .as[Query[GraphRow]]
    }

    def getPage(parentIds: Seq[NodeId], childIds: Seq[NodeId], requestingUserId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx
        .run {
          graphPage(parentIds, childIds, requestingUserId)
        }
        .map(Graph.from)
    }

    def getPageWithOrphans(parentIds: Seq[NodeId], childIds: Seq[NodeId], requestingUserId: UserId)(
        implicit ec: ExecutionContext
    ): Future[Graph] = {
      //TODO: also get visible direct parents in stored procedure
      ctx
        .run {
          graphPageWithOrphans(parentIds, childIds, requestingUserId)
        }
        .map(Graph.from)
    }
  }
}
