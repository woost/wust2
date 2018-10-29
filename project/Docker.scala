import java.io.File

import com.typesafe.sbt.SbtGit.git
import sbt.{Def, Keys}
import sbtassembly.AssemblyKeys
import sbtdocker.ImageName
import sbtdocker.mutable.Dockerfile
import scala.util.Try

import scala.concurrent.duration._

object Docker {
  def java(artifact: File, healthCheckUrl: Option[String] = None): Dockerfile = {
    val appFolder = "/app"
    val logsFolder = s"$appFolder/logs"

    new Dockerfile {
      from(Deps.docker.openjdk8)
      runRaw("apk update && apk add curl")
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
    val rawBranch = 
      sys.env.get("OVERRIDE_BRANCH") orElse
      sys.env.get("CIRCLE_BRANCH") orElse
      Try(git.gitCurrentBranch.value).filter(_.nonEmpty).toOption getOrElse
      "dirty"

    val branch = if (rawBranch == "master") "latest" else rawBranch
    val tags = branch :: Keys.version.value :: Nil

    tags.map { v =>
      val version = if (versionPostfix.isEmpty) v else s"$v-$versionPostfix"
      ImageName(namespace = Some("docker.woost.space/woost"), repository = name: String, tag = Some(version))
    }
  }
}
