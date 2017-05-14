enablePlugins(DockerPlugin)

dockerfile in docker := {
  new Dockerfile {
    from("dhoer/flyway:4.0.3-alpine")
    run("adduser", "user", "-D", "-u", "1000")
    run("chown", "-R", "user:user", "/flyway")
    user("user")
    copy(baseDirectory(_ / "sql").value, "/flyway/sql")
    copy(baseDirectory(_ / "flyway-await-postgres.sh").value, s"/flyway/flyway-await-postgres.sh")
    entryPoint("/flyway/flyway-await-postgres.sh")
  }
}

imageNames in docker :=
  ImageName(namespace = Some("woost"), repository = "wust2.db-migration") ::
  ImageName(namespace = Some("woost"), repository = "wust2.db-migration", tag = Some(version.value)) ::
  Nil
