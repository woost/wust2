package wust.core

import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.PublishSubject
import wust.api.{Authentication, ClientInfo}
import wust.core.auth.JWT
import wust.core.config.ServerConfig
import wust.core.mail.{MailMessage, MailRecipient, MailService}
import wust.graph.Node
import wust.ids.{NodeId, UserId}
import wust.util.StringOps

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

object AppEmailFlow {
  val teamEmailAddress = "team@woost.space" //TODO config...
}

class AppEmailFlow(serverConfig: ServerConfig, jwt: JWT, mailService: MailService) {
  import AppEmailFlow._

  private val emailSubject = PublishSubject[MailMessage]

  private def generateRandomVerificationLink(userId: UserId, email: String): String = {
    val token = jwt.generateEmailActivationToken(userId, email)
    s"https://core.${serverConfig.host}/${ServerPaths.emailVerify}?token=${token.string}"
  }

  private def workspaceLink(nodeId: NodeId):String = {
    s"https://${serverConfig.host}/#page=${nodeId.toBase58}"
  }

  private def workspaceLink(nodeId: NodeId, token: Authentication.Token):String = {
    s"https://${serverConfig.host}/#page=${nodeId.toBase58}&invitation=${token.string}"
  }

  private def userSettingsLink: String = {
    s"https://${serverConfig.host}/#view=usersettings"
  }

  private val farewell = "Your Woost Team"

  //TODO config
  private val signature = "Woost - c/o DigitalHUB Aachen e.V. - Jülicher Straße 72a - 52070 Aachen"

  private def verificationMailMessage(userId: UserId, email: String): MailMessage = {
    val recipient = MailRecipient(to = email :: Nil)
    val subject = "Woost - Please verify your email address"
    val secretLink = generateRandomVerificationLink(userId, email)

    val body =
      s"""
        |Hi there,
        |
        |please verify your email address by clicking this link:
        |${secretLink}
        |
        |This link will be valid for ${jwt.emailVerificationTokenLifeTimeSeconds / 60 / 60 } hours. If the link has expired, you can resend a new verification mail via ${userSettingsLink}.
        |
        |Thank you!
        |
        |$farewell
        |
        |$signature
      """.stripMargin

    val bodyHtml =
      s"""
        |<p>Hi there,</p>
        |
        |<p>please verify your email address by clicking this link: <a href='$secretLink'>Verify your email address</a></p>
        |
        |<p>This link will be valid for ${jwt.emailVerificationTokenLifeTimeSeconds / 60 / 60 } hours. If the link has expired, you can resend a new verification mail in your <a href='$userSettingsLink'>user settings</a>.</p>
        |
        |<p>Thank you!</p>
        |
        |<p>$farewell</p>
        |
        |<p>$signature</p>
      """.stripMargin

    MailMessage(recipient, subject = subject, fromPersonal = "Woost", body = body, bodyHtml = Some(bodyHtml))
  }

  private def feedbackMailMessage(userId: UserId, userName: String, userEmail: Option[String], clientInfo: ClientInfo, msg: String): MailMessage = {
    val recipient = MailRecipient(to = teamEmailAddress :: Nil)
    val subject = s"Feedback on ${serverConfig.host}"
    val body =
      s"""
        |Feedback:
        |  UserId: ${userId.toCuidString}
        |  UserName: ${userName}
        |  Email: ${userEmail.getOrElse("-")}
        |  UserAgent: ${clientInfo.userAgent}
        |  Instance: ${serverConfig.host}
        |
        |$msg
      """.stripMargin

    MailMessage(recipient, subject = subject, fromPersonal = "Woost", body = body)
  }

  private def inviteMailMessage(email:String, invitedJwt: Authentication.Token, inviterName:String, inviterEmail:String, node: Node.Content): MailMessage = {
    //TODO: description of what woost is
    val recipient = MailRecipient(to = email :: Nil)
    val subject = s"$inviterEmail invited you to '${StringOps.trimToMaxLength(node.str, 20)}'"
    val secretLink = workspaceLink(node.id, invitedJwt)

    val escapedContent = com.google.common.html.HtmlEscapers.htmlEscaper().escape(StringOps.trimToMaxLength(node.str, 200))

    val body =
      s"""
        |$inviterEmail has invited you to collaborate on a workspace in Woost.
        |
        |Click the following link to accept the invitation:
        |$secretLink
        |
        |"$escapedContent"
        |
        |$farewell
        |
        |$signature
      """.stripMargin

    val bodyHtml =
      s"""
        |<p>$inviterEmail has invited you to collaborate on a workspace in Woost.</p>
        |
        |<p>Click the following link to accept the invitation: <a href='$secretLink'>Accept Invitation</a></p>
        |
        |<blockquote>$escapedContent</blockquote>
        |
        |<p>$farewell</p>
        |
        |<p>$signature</p>
      """.stripMargin

    MailMessage(recipient, subject = subject, fromPersonal = s"$inviterName via Woost", body = body, bodyHtml = Some(bodyHtml))
  }

  private def mentionMailMessage(email:String, mentionedIn: Seq[NodeId], authorName:String, authorEmail:String, node: Node.Content): MailMessage = {
    //TODO: description of what woost is
    val recipient = MailRecipient(to = email :: Nil)
    val subject = s"$authorName mentioned you in '${StringOps.trimToMaxLength(node.str, 20)}'"

    val escapedContent = com.google.common.html.HtmlEscapers.htmlEscaper().escape(StringOps.trimToMaxLength(node.str, 200))

    val linkNodeIds = if (mentionedIn.isEmpty) Seq(node.id) else mentionedIn

    val body =
      s"""
        |$authorName has mentioned you in a message in Woost:
        |
        |"$escapedContent"
        |
        |Click the following link to view the message:
        |${linkNodeIds.map(id => workspaceLink(id)).mkString(", ")}
        |
        |
        |$farewell
        |
        |$signature
      """.stripMargin

    val bodyHtml =
      s"""
        |<p>$authorName has mentioned you in a message in Woost:</p>
        |
        |<blockquote>$escapedContent</blockquote>
        |
        |<p>Click the following link to view the message: ${linkNodeIds.map(id => s"<a href='${workspaceLink(id)}'>View Message</a>").mkString(", ")}</p>
        |
        |
        |<p>$farewell</p>
        |
        |<p>$signature</p>
      """.stripMargin

    MailMessage(recipient, subject = subject, fromPersonal = s"$authorName via Woost", body = body, bodyHtml = Some(bodyHtml))
  }

  def sendEmailVerification(userId: UserId, email: String)(implicit ec: ExecutionContext): Unit = {
    val message = verificationMailMessage(userId, email)
    emailSubject.onNext(message)
  }

  def sendEmailFeedback(userId: UserId, userName: String, userEmail: Option[String], clientInfo: ClientInfo, msg: String)(implicit ec: ExecutionContext): Unit = {
    val message = feedbackMailMessage(userId, userName = userName, userEmail = userEmail, clientInfo, msg = msg)
    emailSubject.onNext(message)
  }

  def sendEmailInvitation(email: String, invitedJwt: Authentication.Token, inviterName:String, inviterEmail:String, node: Node.Content)(implicit ec: ExecutionContext): Unit = {
    val message = inviteMailMessage(email = email, invitedJwt = invitedJwt, inviterName = inviterName, inviterEmail = inviterEmail, node = node)
    emailSubject.onNext(message)
  }

  def sendMentionNotification(email: String, authorName:String, authorEmail:String, mentionedIn: Seq[NodeId], node: Node.Content)(implicit ec: ExecutionContext): Unit = {
    val message = mentionMailMessage(email = email, mentionedIn = mentionedIn, authorName = authorName, authorEmail = authorEmail, node = node)
    emailSubject.onNext(message)
  }

  def start()(implicit scheduler: Scheduler): Cancelable =
    emailSubject.mapEval { message =>
      // retry? MonixUtils.retryWithBackoff(mailService.sendMail(message), maxRetries = 3, initialDelay = 1.minute)
      mailService
        .sendMail(message)
        .map {
          case MailService.Success =>
            scribe.warn(s"Successfully sent out email: $message")
            ()
          case MailService.Blocked(reason) =>
            scribe.warn(s"Sending email was blocked, because '$reason': $message")
            ()
        }
        .onErrorRecover { case NonFatal(t) =>
          scribe.warn(s"Failed to send email message, will not retry: $message", t)
          ()
        }
    }.subscribe(
      _ => Ack.Continue,
      err => scribe.error(s"Error while sending email, will not continue", err)
    )
}
