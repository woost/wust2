package wust.cli

import java.io.File
import java.nio.file.Paths

import com.typesafe.config.ConfigRenderOptions
import pureconfig.error.ConfigReaderFailures

import scala.util.Try

case class Config(username: String, password: String, url: String)

object Config {
   import pureconfig._
   import wust.util.Config._

  private val configPath = {
    val homeDir = System.getProperty("user.home")
    Paths.get(homeDir, ".config", "woost") // TODO: proper filename for windows
  }

  private def assureConfigPathExists(): Unit = {
    new File(configPath.toString).mkdirs()
  }

  private def configFile(profile: Option[String]) = {
    val fileName = profile match {
      case None => "default.conf"
      case Some(profile) => s"$profile.conf"
    }
    Paths.get(configPath.toString, fileName)
  }


  def load(profile: Option[String]): Either[ConfigReaderFailures, Config] = {
    loadConfigFromFiles[Config](configFile(profile) :: Nil, failOnReadError = true)
  }
  def store(config: Config, profile: Option[String]): Either[String, Unit] = {
     assureConfigPathExists()
     Try {
       saveConfigAsPropertyFile(config, configFile(profile), overrideOutputPath = true, options = ConfigRenderOptions.concise.setFormatted(true))
     }.toEither.left.map(_.getMessage)
  }
}
