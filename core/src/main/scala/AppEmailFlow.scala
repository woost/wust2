package wust.backend

import monix.eval.Task
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.PublishSubject
import wust.backend.config.{EmailConfig, ServerConfig}
import wust.backend.mail.{MailMessage, MailRecipient, MailService}
import wust.api.{AuthUser, Authentication, UserDetail}
import wust.backend.auth.JWT
import wust.graph.Node
import wust.ids.{NodeId, UserId}
import wust.serviceUtil.MonixUtils

import scala.util.control.NonFatal
import wust.util.StringOps

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class AppEmailFlow(serverConfig: ServerConfig, jwt: JWT, mailService: MailService) {
  private val emailSubject = PublishSubject[MailMessage]

  private def generateRandomVerificationLink(userId: UserId, email: String): String = {
    val token = jwt.generateEmailActivationToken(userId, email)
    s"https://core.${serverConfig.host}/${Server.paths.emailVerify}?token=$token"
  }

  private def workspaceLink(nodeId: NodeId, token: Authentication.Token):String = {
    s"https://${serverConfig.host}/#page=${nodeId.toBase58}&invitation=$token"
  }

  private def userSettingsLink: String = {
    s"https://${serverConfig.host}/#view=usersettings"
  }

  private val signature =
    """
      |Your Woost Team
      |
      |Woost - c/o DigitalHUB Aachen e.V. - Jülicher Straße 72a - 52070 Aachen
    """.stripMargin

  private def verificationMailMessage(userId: UserId, email: String): MailMessage = {
    val secretLink = generateRandomVerificationLink(userId, email)
    val recipient = MailRecipient(to = email :: Nil)
    val subject = "Woost - Please verify your email address"
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
        |$signature
      """.stripMargin

    MailMessage(recipient, subject = subject, body = body, fromPersonal = "Woost")
  }

  private def feedbackMailMessage(userId: UserId, userName: String, msg: String): MailMessage = {
    //TODO: show name and email in message
    // pass User and Option[UserDetail] to this function
    val recipient = MailRecipient(to = "team@woost.space" :: Nil)
    val subject = s"Feedback on ${serverConfig.host}"
    val body =
      s"""
        |Feedback:
        |  UserId: ${userId.toCuidString}
        |  UserName: ${userName}
        |  Instance: ${serverConfig.host}
        |
        |$msg
      """.stripMargin

    MailMessage(recipient, subject = subject, body = body, fromPersonal = "Woost")
  }

  private def inviteEMailMessage(email:String, invitedJwt: Authentication.Token, inviterName:String, inviterEmail:String, node: Node.Content): MailMessage = {
    //TODO: email from field with username
    // we assume that the node we share is already public and just send a link
    // in reality we want something smarter, bind email adress to permission
    //TODO: description of what woost is
    val recipient = MailRecipient(to = email :: Nil)
    val subject = s"$inviterEmail invited you to '${StringOps.trimToMaxLength(node.str, 20)}'"
    val body =
      s"""
        | $inviterEmail has invited you to collaborate on a workspace in Woost.
        |
        | Click the following link to accept the invitation:
        |
        | ${workspaceLink(node.id, invitedJwt)}
        |
        | "${StringOps.trimToMaxLength(node.str, 200)}"
        |
        | $signature
      """.stripMargin

    MailMessage(recipient, subject = subject, body = body, fromPersonal = s"$inviterName via Woost")
  }

  def sendEmailVerification(userId: UserId, email: String)(implicit ec: ExecutionContext): Unit = {
    val message = verificationMailMessage(userId, email)
    emailSubject.onNext(message)
  }

  def sendEmailFeedback(userId: UserId, userName: String, msg: String)(implicit ec: ExecutionContext): Unit = {
    val message = feedbackMailMessage(userId, userName = userName, msg = msg)
    emailSubject.onNext(message)
  }

  def sendEmailInvitation(email: String, invitedJwt: Authentication.Token, inviterName:String, inviterEmail:String, node: Node.Content)(implicit ec: ExecutionContext): Unit = {
    val message = inviteEMailMessage(email = email, invitedJwt = invitedJwt, inviterName = inviterName, inviterEmail = inviterEmail, node = node)
    emailSubject.onNext(message)
  }

  def start()(implicit scheduler: Scheduler): Cancelable = emailSubject
      .mapEval { message =>
        // retry? MonixUtils.retryWithBackoff(mailService.sendMail(message), maxRetries = 3, initialDelay = 1.minute)
        mailService.sendMail(message)
          .onErrorRecover { case NonFatal(t) =>
            scribe.warn(s"Failed to send email message: $message", t)
            ()
          }
      }
      .subscribe(
        _ => Ack.Continue,
        err => scribe.error(s"Error while sending email, will not continue", err)
      )
}
