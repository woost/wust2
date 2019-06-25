package wust.core.aws

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}
import com.amazonaws.services.simpleemail.model.{Body, Content, Destination, Message, SendEmailRequest}
import com.amazonaws.services.simpleemail.{AmazonSimpleEmailService, AmazonSimpleEmailServiceClientBuilder}
import wust.backend.config.{AwsConfig, SESConfig}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import monix.eval.Task
import wust.backend.mail.{MailClient, MailMessage}

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
