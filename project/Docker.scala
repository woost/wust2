import java.io.File

import com.typesafe.sbt.SbtGit.git
import sbt.{Def, Keys}
import sbtdocker.ImageName
import sbtdocker.mutable.Dockerfile

import scala.concurrent.duration._
import scala.util.Try

object Docker {
  def java(artifact: File, healthCheckUrl: Option[String] = None): Dockerfile = {
    val appFolder = "/app"
    val logsFolder = s"$appFolder/logs"

    new Dockerfile {
      from(Deps.docker.alpine)
      runRaw("apk add --no-cache curl openjdk11-jre-headless")
      run("adduser", "user", "-D", "-u", "1000")
      run("mkdir", "-p", appFolder)
      run("mkdir", "-p", logsFolder)
      run("chown", "-R", "user:user", appFolder)
      run("chown", "-R", "user:user", logsFolder)
      volume(logsFolder)
      user("user")

      healthCheckUrl.foreach { url =>
        healthCheck(
          Seq("curl", "-f", "-X", "GET", url),
          interval = Some(30 seconds),
          timeout = Some(10 seconds),
          retries = Some(2)
        )
      }

      workDir(appFolder)
      copy(artifact, appFolder)
      entryPoint("java", "-jar", artifact.getName)
    }
  }

  def imageNames(name: String, versionPostfix: String = ""): Def.Initialize[List[ImageName]] = Def.setting {
    val tags = sys.env.get("WUST_VERSION").getOrElse("latest") :: Nil

    tags.map { v =>
      val version = if (versionPostfix.isEmpty) v else s"$v-$versionPostfix"
      ImageName(namespace = Some("woost"), repository = name, tag = Some(version))
    }
  }
}
