enablePlugins(DockerPlugin)

import scala.concurrent.duration._

dockerfile in docker := {
  val artifact: File = assembly.value
  val appFolder = "/app"
  val logsFolder = s"$appFolder/logs"

  new Dockerfile {
    from("openjdk:8-jre-alpine")
    runRaw("apk update && apk add curl")
    run("adduser", "user", "-D", "-u", "1000")
    run("mkdir", "-p", appFolder)
    run("mkdir", "-p", logsFolder)
    run("chown", "-R", "user:user", appFolder)
    run("chown", "-R", "user:user", logsFolder)
    volume(logsFolder)
    user("user")

    healthCheck(Seq("curl", "-f", "-X", "GET", "localhost:8080/health"), interval = Some(30 seconds), timeout = Some(10 seconds), retries = Some(2))

    workDir(appFolder)
    copy(artifact, appFolder)
    entryPoint("java", "-jar", artifact.name)
  }
}

imageNames in docker := Defs.dockerVersionTags.value.map { v =>
  ImageName(namespace = Some("woost"), repository = "core", tag = Some(v))
}
