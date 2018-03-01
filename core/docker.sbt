enablePlugins(DockerPlugin)

import scala.concurrent.duration._

dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre-alpine")
    runRaw("apk update && apk add curl")
    run("adduser", "user", "-D", "-u", "1000")
    user("user")
    copy(artifact, artifactPath)
    healthCheck(Seq("curl", "-f", "-X", "GET", "localhost:8080/health"), interval = Some(30 seconds), timeout = Some(10 seconds), retries = Some(2))
    entryPoint("java", "-jar", artifactPath)
  }
}

imageNames in docker := Defs.dockerVersionTags.value.map { v =>
  ImageName(namespace = Some("woost"), repository = "core", tag = Some(v))
}
