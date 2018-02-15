enablePlugins(DockerPlugin)

dockerfile in docker := {
  val artifact: File = assembly.value
  val artifactPath = s"/app/${artifact.name}"

  new Dockerfile {
    from("openjdk:8-jre-alpine")
    run("adduser", "user", "-D", "-u", "1000")
    user("user")
    copy(artifact, artifactPath)
    entryPoint("java", "-jar", artifactPath)
  }
}

imageNames in docker :=
  ImageName(namespace = Some("woost"), repository = "github") ::
  ImageName(namespace = Some("woost"), repository = "github", tag = Some(version.value)) ::
  Nil
