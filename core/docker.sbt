enablePlugins(DockerPlugin)

import sbtdocker.Instructions.Raw

dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre-alpine")
    runRaw("apk update && apk add curl")
    run("adduser", "user", "-D", "-u", "1000")
    user("user")
    copy(artifact, artifactPath)
    addInstruction(Raw("healthcheck", "--interval=30s --timeout=10s --retries=2 CMD curl -f -X GET localhost:8080/health"))
    entryPoint("java", "-jar", artifactPath)
  }
}

imageNames in docker :=
  ImageName(namespace = Some("woost"), repository = "core") ::
  ImageName(namespace = Some("woost"), repository = "core", tag = Some(version.value)) ::
  Nil
