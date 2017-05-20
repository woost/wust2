package wust.backend.mail

import wust.backend.config._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

case class MailRecipient(to: Seq[String], cc: Seq[String] = Seq.empty, bcc: Seq[String] = Seq.empty)
case class MailMessage(subject: String, content: String)

trait MailService {
  def sendMail(recipient: MailRecipient, message: MailMessage)(implicit ec: ExecutionContext): Future[Boolean]
}

object LoggingMailService extends MailService {
  override def sendMail(recipient: MailRecipient, message: MailMessage)(implicit ec: ExecutionContext): Future[Boolean] = {
    scribe.info(s"logging mail:\n\tto: $recipient\n\tmail: $message")
    Future.successful(true)
  }
}

class SmtpMailService(emailConfig: EmailConfig) extends MailService {
  private val client = new JavaMailClient(emailConfig.smtp)

  override def sendMail(recipient: MailRecipient, message: MailMessage)(implicit ec: ExecutionContext): Future[Boolean] = Future {
    scribe.info(s"sending mail through smtp ($emailConfig):\n\tto: $recipient\n\tmail: $message")

    client.sendMessage(emailConfig.fromAddress, recipient, message) match {
      case Success(_) => true
      case Failure(t) =>
        scribe.error("failed to send mail")
        scribe.error(t)
        false
    }
  }
}

object MailService {
  val default = {
    Config.email.map { email =>
      new SmtpMailService(email)
    } getOrElse LoggingMailService
  }
}
