package wust.backend.mail

import monix.eval.Task
import wust.backend.config._

final case class MailRecipient(to: Seq[String], cc: Seq[String] = Seq.empty, bcc: Seq[String] = Seq.empty)
final case class MailMessage(recipient: MailRecipient, subject: String, fromPersonal: String, body: String, bodyHtml: Option[String] = None)

trait MailService {
  def sendMail(message: MailMessage): Task[Unit]
}

object LoggingMailService extends MailService {
  override def sendMail(message: MailMessage): Task[Unit] = Task {
    scribe.info(s"MailService not activated, just logging the mail:\n\t $message")
    ()
  }
}

class SendingMailService(fromAddress: String, client: MailClient) extends MailService {
  override def sendMail(message: MailMessage): Task[Unit] = {
    client.sendMessage(fromAddress, message)
      .doOnFinish {
        case None => Task(scribe.info(s"Successfully sent out email: $message"))
        case Some(err) => Task(scribe.error(s"Failed to send out email: $message", err))
      }
  }
}

object MailService {
  def empty: MailService = LoggingMailService
  def apply(fromAddress: String, client: MailClient): MailService = new SendingMailService(fromAddress, client)

  def apply(config: Option[EmailConfig]): MailService = config.fold[MailService](empty) { config =>
    apply(config.fromAddress, new JavaMailClient(config.smtp))
  }
}
