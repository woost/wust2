package wust.backend.mail

import wust.backend.config._
import scala.concurrent.Future
import scala.util.{Success, Failure}

import scala.concurrent.ExecutionContext.Implicits.global

case class MailRecipient(to: Seq[String], cc: Seq[String] = Seq.empty, bcc: Seq[String] = Seq.empty)
case class MailMessage(subject: String, content: String)

trait MailService {
  def sendMail(recipient: MailRecipient, message: MailMessage): Future[Boolean]
}

object LoggingMailService extends MailService {
  override def sendMail(recipient: MailRecipient, message: MailMessage): Future[Boolean] = {
    scribe.info(s"logging mail:\n\tto: $recipient\n\tmail: $message")
    Future.successful(true)
  }
}

class SmtpMailService(from: String, config: SmtpConfig) extends MailService {
  private val client = new JavaMailClient(config)

  override def sendMail(recipient: MailRecipient, message: MailMessage): Future[Boolean] = Future {
    scribe.info(s"sending mail through smtp ($config):\n\tfrom: $from\n\tto: $recipient\n\tmail: $message")

    client.sendMessage(from, recipient, message) match {
      case Success(_) => true
      case Failure(t) =>
        scribe.error("failed to send mail")
        scribe.error(t)
        false
    }
  }
}

trait MailServiceWrapper extends MailService {
  //TODO delegert
  protected def inner: MailService
  override def sendMail(recipient: MailRecipient, message: MailMessage) = inner.sendMail(recipient, message)
}

object MailService extends MailServiceWrapper {
  protected lazy val inner: MailService = {
    Config.email.fromAddress.flatMap { from =>
      Config.email.smtp.map(smtp => new SmtpMailService(from, smtp))
    } getOrElse LoggingMailService
  }
}
