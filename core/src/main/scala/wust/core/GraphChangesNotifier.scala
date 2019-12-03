package wust.core

import wust.api.{AuthUser, UserDetail}
import wust.db.Db
import wust.graph._
import wust.ids.{NodeId, UserId, EmailAddress}
import wust.util.collection._
import DbConversions._

import scala.collection.breakOut
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class GraphChangesNotifier(db: Db, emailFlow: AppEmailFlow) {
  //TODO: make this more efficient with less queries
  //TODO: notify user that some mentions could not be reached?

  case class MentionsWithNode(mentions: Seq[Edge.Mention], node: Node.Content)

  def notify(author: AuthUser.Persisted, changes: GraphChanges)(implicit ec: ExecutionContext): Unit = {
    val mentions: Seq[Edge.Mention] = changes.addEdges.collect { case e: Edge.Mention => e }
    if (mentions.isEmpty) return

    scribe.info(s"Notifying mentions: $mentions")
    db.user.getUserDetail(author.id).onComplete {
      case Success(Some(userDetail)) if userDetail.verified => userDetail.email match {
        case Some(authorEmail) =>
          val mentionsBySourceId = mentions.groupBy(_.nodeId)
          db.node.get(mentionsBySourceId.keys.toSeq).onComplete {
            case Success(nodes) =>
              val nodeMap: Map[NodeId, Node.Content] = nodes.map(forClient).collect { case n: Node.Content => n.id -> n }(breakOut)
              val groupedMentions = mentionsBySourceId.flatMap { case (nodeId, mentions) => nodeMap.get(nodeId).map(MentionsWithNode(mentions, _)) }(breakOut)
              notifyMentions(author, authorEmail, groupedMentions)
            case Failure(t) => scribe.error("Failed to query nodes with mention, will not send email.", t)
          }
        case None => scribe.info("Author user has has no email defined, will not send email.")
      }
      case Success(_) => scribe.info("Author user has no verified user details defined, will not send email.")
      case Failure(t) => scribe.error("Failed to query user detail of author, will not send email.", t)
    }
  }

  private def notifyMentions(author: AuthUser, authorEmail: EmailAddress, groupedMentions: Seq[MentionsWithNode])(implicit ec:ExecutionContext): Unit = {
    groupedMentions.foreach { groupedMention =>
      val mentionedIds: List[NodeId] = groupedMention.mentions.map(_.mentionedId)(breakOut)
      val mentionsAuthorDirectly = mentionedIds.contains(author.id)
      db.node.resolveMentionedNodesWithAccess(mentionedIds, canAccessNodeId = groupedMention.node.id).onComplete {
        case Success(targetUsers) =>
          scribe.info(s"Resolved mentionedNodeId '$mentionedIds' to users: $targetUsers")
          targetUsers.distinct.foreach { user =>
            if (mentionsAuthorDirectly || author.id != user.id) db.user.getUserDetail(user.id).onComplete {
              case Success(Some(userDetail)) if userDetail.verified => userDetail.email match {
                case Some(email) => db.node.getAccessibleWorkspaces(author.id, groupedMention.node.id).onComplete {
                  case Success(parentIds) => emailFlow.sendMentionNotification(email = email, authorName = author.name, authorEmail = authorEmail, mentionedIn = parentIds, node = groupedMention.node)
                  case Failure(t) => scribe.error("Failed to query accessible parent of node mention, will not send email.", t)
                }

                case None => scribe.info("Mentioned user has no email address defined, will not send email.")
              }
              case Success(_) => scribe.info("Mentioned user has no verified user details defined, will not send email.")
              case Failure(t) => scribe.error("Failed to query user detail of mentioned user, will not send email.", t)
            }
          }
        case Failure(t) =>
          scribe.error("Failed to query mentioned nodes, will not send email.", t)
      }
    }
  }
}
