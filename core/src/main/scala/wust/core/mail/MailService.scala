package wust.core.mail

import monix.eval.Task
import wust.core.config.{EmailConfig, EmailSettings}

final case class MailRecipient(to: Seq[String], cc: Seq[String] = Seq.empty, bcc: Seq[String] = Seq.empty) {
  def exists(f: String => Boolean) = to.exists(f) || cc.exists(f) || bcc.exists(f)
}
final case class MailMessage(recipient: MailRecipient, subject: String, fromPersonal: String, body: String, bodyHtml: Option[String] = None, replyTo: Option[String] = None)

trait MailService {
  def sendMail(message: MailMessage): Task[MailService.Result]
}

object LoggingMailService extends MailService {
  override def sendMail(message: MailMessage): Task[MailService.Result] = Task {
    scribe.info(s"MailService not activated, just logging the mail:\n\t $message")
    MailService.Success
  }
}

class SendingMailService(settings: EmailSettings, client: MailClient) extends MailService {
  private def isRecipientBlocked(recipient: MailRecipient): Boolean = {
    recipient.exists(address => settings.blockedEmailDomainsList.exists(domain => address.endsWith("@" + domain)))
  }

  override def sendMail(message: MailMessage): Task[MailService.Result] = {
    if (isRecipientBlocked(message.recipient)) Task.pure(MailService.Blocked(s"Recipient address is blocked by us: ${message.recipient}"))
    else client.sendMessage(settings.fromAddress, message).map(_ => MailService.Success)
  }
}

object MailService {
  sealed trait Result
  case object Success extends Result
  case class Blocked(reason: String) extends Result

  def empty: MailService = LoggingMailService
  def apply(settings: EmailSettings, client: MailClient): MailService = new SendingMailService(settings, client)

  def apply(config: Option[EmailConfig]): MailService = config.fold[MailService](empty) { config =>
    apply(config.settings, new JavaMailClient(config.smtp))
  }
}

