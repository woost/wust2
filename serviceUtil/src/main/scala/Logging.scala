package wust.serviceUtil

import java.net.InetAddress
import java.nio.file.Paths

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import monix.execution.Scheduler
import scribe.{writer, _}
import scribe.format._
import scribe.writer._
import scribe.writer.file.LogPath

object Logging {
  val shortThreadName = threadName.map(_.replaceFirst("server-akka.actor.default-dispatcher-", ""))
  val shortLevel = level.map(_.trim)
  val fileBaseName = FormatBlock.FileName.map(fileName => fileName.split('/').last)
  val simpleFormatter =
    formatter"${scribe.format.time} $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"
  val detailFormatter =
    formatter"${scribe.format.time} $shortLevel [$shortThreadName] $fileBaseName:${FormatBlock.LineNumber} - $message$newLine"

  def setup(hostname: String, id: String, logstashConfig: Option[LogstashConfig] = None): Unit = {
    val rootSetup = Logger.root
      .clearHandlers()
      .withHandler(formatter = simpleFormatter, minimumLevel = None, writer = ConsoleWriter)
      .withHandler(
        formatter = detailFormatter,
        minimumLevel = Some(Level.Info),
        writer = FileWriter().path(LogPath.daily(prefix = id, directory = Paths.get("logs")))
      )


    val configuredSetup = logstashConfig.fold(rootSetup) { logstashCfg =>
      import Scheduler.Implicits.global
      implicit val system = ActorSystem.create("logging")
      implicit val materializer = ActorMaterializer()

      val writer = new QueuedLogstashWriter(
        url = logstashCfg.url,
        service = id,
        additionalFields = Map(
          "type" -> "applog",
          "beat.hostname" -> hostname))

      writer.start()

      rootSetup
        .withHandler(
          formatter = simpleFormatter,
          minimumLevel = Some(Level.Info),
          writer = writer)
    }

    configuredSetup.replace()


  }

  case class LogstashConfig(url: String) {
    override def toString = s"LogstashConfig(${url.split("/").head})"
  }
}
