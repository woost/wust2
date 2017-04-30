package wust.backend.mail

import wust.backend.config._
import scala.concurrent.Future

case class EmailRecipient(address: String)
case class EmailTemplate(subject: String, body: String)

trait MailService {
  def sendMail(recipients: Seq[EmailRecipient], template: EmailTemplate): Future[Boolean]
}

class LoggingMailService(address: Option[String]) extends MailService {
  override def sendMail(recipients: Seq[EmailRecipient], template: EmailTemplate): Future[Boolean] = {
    scribe.info(s"logging mail:\n\tfrom: $address\n\tto: $recipients\n\tmail: $template")
    Future.successful(true)
  }
}

class SmtpMailService(address: String, config: SmtpConfig) extends MailService {
  override def sendMail(recipients: Seq[EmailRecipient], template: EmailTemplate): Future[Boolean] = {
    scribe.info(s"sending mail through smtp ($config):\n\tfrom: $address\n\tto: $recipients\n\tmail: $template")
    Future.successful(true)
  }
}

trait MailServiceWrapper extends MailService {
  //TODO delegert
  protected def inner: MailService
  override def sendMail(recipients: Seq[EmailRecipient], template: EmailTemplate) = inner.sendMail(recipients, template)
}

object MailService extends MailServiceWrapper {
  protected lazy val inner: MailService = {
    Config.email.fromAddress.map { address =>
      Config.email.smtp.map { smtp =>
        val config = SmtpConfig(endpoint = smtp.endpoint, username = smtp.username, password = smtp.password)
        new SmtpMailService(address, config)
      }.getOrElse(new LoggingMailService(Some(address)))
    } getOrElse new LoggingMailService(None)
  }
}
