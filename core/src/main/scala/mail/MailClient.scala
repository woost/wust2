package wust.backend.mail

import java.util.{Date, Properties}

import javax.mail._
import javax.mail.internet._
import monix.eval.Task
import wust.backend.config.SmtpConfig

trait MailClient {
  def sendMessage(from: String, mail: MailMessage): Task[Unit]
}

class JavaMailClient(config: SmtpConfig) extends MailClient {
  private val authenticator = new Authenticator {
    override protected def getPasswordAuthentication =
      new PasswordAuthentication(config.username, config.password)
  }

  private def createMessage(from: String, mail: MailMessage): Task[Message] = Task {
    import mail._

    val properties = new Properties()
    val (host, port) = {
      val arr = config.endpoint.split(":")
      if (arr.size > 1) (arr(0), Some(arr(1))) else (config.endpoint, None)
    }
    properties.put("mail.smtp.host", host)
    port.foreach(properties.put("mail.smtp.port", _))

    properties.put("mail.smtp.ssl.enable", "true")
    properties.put("mail.smtp.auth", "true")

    val session = Session.getDefaultInstance(properties, authenticator)
    // session.setDebug(true)

    val message = new MimeMessage(session)

    message.setFrom(new InternetAddress(from))

    val to: Array[Address] = recipient.to.map(addr => new InternetAddress(addr)).toArray
    message.setRecipients(Message.RecipientType.TO, to)
    val cc: Array[Address] = recipient.cc.map(addr => new InternetAddress(addr)).toArray
    message.setRecipients(Message.RecipientType.CC, cc)
    val bcc: Array[Address] = recipient.bcc.map(addr => new InternetAddress(addr)).toArray
    message.setRecipients(Message.RecipientType.BCC, bcc)

    message.setSentDate(new Date())
    message.setSubject(subject)
    message.setText(body)
    message
  }

  def sendMessage(from: String, mail: MailMessage): Task[Unit] = {
    createMessage(from, mail).flatMap(message => Task(Transport.send(message)))
  }
}
