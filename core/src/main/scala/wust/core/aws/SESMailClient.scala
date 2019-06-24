package wust.core.aws

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model._
import monix.eval.Task
import wust.core.config.SESConfig
import wust.core.mail.{MailClient, MailMessage}

class SESMailClient(replyTo: String, config: SESConfig) extends MailClient {
  private val client = AmazonSimpleEmailServiceClientBuilder
    .standard
    .withRegion(config.region)
    .build()

  def sendMessage(from: String, mail: MailMessage): Task[Unit] = Task {
    val request = new SendEmailRequest()
      .withReplyToAddresses(replyTo)
      .withDestination(new Destination()
        .withToAddresses(mail.recipient.to: _*)
        .withCcAddresses(mail.recipient.cc: _*)
        .withBccAddresses(mail.recipient.bcc: _*)
      ).withMessage(new Message()
        .withBody(mail.bodyHtml.fold(new Body) { bodyHtml =>
          new Body().withHtml(new Content().withCharset("UTF-8").withData(bodyHtml))
        }.withText(new Content().withCharset("UTF-8").withData(mail.body))
        ).withSubject(new Content().withCharset("UTF-8").withData(mail.subject)))
        .withSource(s"${mail.fromPersonal} <$from>")

    client.sendEmail(request)
  }
}
