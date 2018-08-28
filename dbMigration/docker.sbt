lazy val projectRef = ProjectRef(file("."), "core") // TODO need some projectref with settings, depending dbMigration does not work because cycle.

def dockerDbMigration(name: String): Seq[Setting[_]] = Seq(
  dockerfile in docker := {
    val postgresHost = "postgres"
    new Dockerfile {
      from(Deps.docker.flyway)
      run("adduser", "user", "-D", "-u", "1000")
      run("chown", "-R", "user:user", "/flyway")
      user("user")
      copy(baseDirectory(_ / "sql").value, "/flyway/sql")
      copy(
        baseDirectory(_ / "../flyway-await-postgres.sh").value,
        s"/flyway/flyway-await-postgres.sh"
      )
      entryPoint("/flyway/flyway-await-postgres.sh", postgresHost)
    }
  },
  imageNames in docker := Docker.imageNames("db-migration", versionPostfix = name).value
)

lazy val dbMigration = project
  .in(file("."))
  .aggregate(dbMigrationCore, dbMigrationGithub, dbMigrationSlack)
lazy val dbMigrationCore = project
  .in(file("core"))
  .enablePlugins(DockerPlugin)
  .settings(dockerDbMigration("core"))
lazy val dbMigrationGithub = project
  .in(file("github"))
  .enablePlugins(DockerPlugin)
  .settings(dockerDbMigration("github"))
lazy val dbMigrationSlack = project
  .in(file("slack"))
  .enablePlugins(DockerPlugin)
  .settings(dockerDbMigration("slack"))
