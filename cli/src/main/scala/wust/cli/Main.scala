package wust.cli

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import caseapp._
import covenant.core.DefaultLogHandler
import sloth.{Client, ClientException}
import wust.api.{Api, AuthApi}

import scala.concurrent.Future

class WustHttpClient(client: Client[String, Future, ClientException]) {
  val api = client.wire[Api[Future]]
  val auth = client.wire[AuthApi[Future]]
}
object WustHttpClient {
  def apply(url: String, headers: => List[(String, String)])(implicit system: ActorSystem): WustHttpClient = {
    implicit val materializer = ActorMaterializer()
    import system.dispatcher

    new WustHttpClient(HttpClientWithHeaders[String](url, new DefaultLogHandler[Future](identity), headers))
  }
}

object Main extends CaseAndCommandApp[AppOptions, AppCommand] {
  def run(opts: AppOptions, cmd: Option[AppCommand], args: RemainingArgs): Unit = {

    setupLogging(opts.debug)

    cmd.getOrElse(AppCommand.Help) match {
      case cmd: AppCommand.Runnable => Commander(opts, cmd, args.remaining)
      case AppCommand.Help          => args.remaining.headOption match {
        case Some(cmd) if commandsMessages.messagesMap.isDefinedAt(cmd) => commandHelpAsked(cmd)
        case _                                                          => helpAsked()
      }
    }
  }

  private def setupLogging(debug: Boolean): Unit = {
    import scribe.format._
    import scribe.writer.ConsoleWriter
    import scribe.{Level, Logger}

    Logger.root
      .clearHandlers()
      .clearModifiers()
      .withHandler(formatter = formatter"$message$newLine", minimumLevel = if (debug) None else Some(Level.Error), writer = ConsoleWriter)
      .replace()
  }
}
