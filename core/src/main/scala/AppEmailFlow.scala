package wust.backend

import monix.eval.Task
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.PublishSubject
import wust.backend.config.{EmailConfig, ServerConfig}
import wust.backend.mail.{MailMessage, MailRecipient, MailService}
import wust.api.UserDetail
import wust.backend.auth.JWT
import wust.ids.UserId
import wust.serviceUtil.MonixUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class AppEmailFlow(serverConfig: ServerConfig, jwt: JWT, mailService: MailService) {
  private val emailSubject = PublishSubject[MailMessage]

  private def generateRandomVerificationLink(userId: UserId, email: String): String = {
    val token = jwt.generateEmailActivationToken(userId, email)
    s"https://core.${serverConfig.host}/${Server.paths.emailVerify}?token=$token"
  }

  private def mailMessage(userId: UserId, email: String): MailMessage = {
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
        |Thank you!
        |
        |The Woost Team
        |
        |Woost - Kackertstr. 7 - 52072 Aachen - Germany
      """.stripMargin

    MailMessage(recipient, subject = subject, body = body)
  }

  def sendEmailVerification(userId: UserId, email: String)(implicit ec: ExecutionContext): Unit = {
    val message = mailMessage(userId, email)
    emailSubject.onNext(message)
  }

  def start()(implicit scheduler: Scheduler): Cancelable = emailSubject
      .mapEval { message =>
        // retry? MonixUtils.retryWithBackoff(mailService.sendMail(message), maxRetries = 3, initialDelay = 1.minute)
        mailService.sendMail(message)
      }
      .subscribe(
        _ => Ack.Continue,
        err => scribe.error(s"Error while sending email, will not continue", err)
      )
}
