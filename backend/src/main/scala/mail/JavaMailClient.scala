package wust.backend.mail

import wust.backend.config.SmtpConfig

import javax.mail._
import javax.mail.internet._
import java.util.Date
import java.util.Properties
import scala.util.{Try, Success, Failure}
import scala.collection.JavaConversions._

class JavaMailClient(config: SmtpConfig)
{
  private val authenticator = new Authenticator {
    override protected def getPasswordAuthentication = new PasswordAuthentication(config.username, config.password)
  }

  private def createMessage(from: String, recipient: MailRecipient, mail: MailMessage): Try[Message] = Try {
    import mail._

    val properties = new Properties()
    properties.put("mail.smtp.host", config.endpoint)
    properties.put("mail.smtp.auth", "true");

    val session = Session.getDefaultInstance(properties, authenticator);
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
    message.setText(content)
    message
  }

  def sendMessage(from: String, recipient: MailRecipient, mail: MailMessage): Try[Unit] = {
    createMessage(from, recipient, mail).map(message => Try(Transport.send(message)))
  }
}
