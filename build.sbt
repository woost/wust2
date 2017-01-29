name := "wust"
version in ThisBuild := "0.1.0-SNAPSHOT"

//TODO: report bug that this project does not compile with 2.12.1
// scala.tools.asm.tree.analysis.AnalyzerException: While processing backend/Server$$anonfun$$nestedInanonfun$router$1$1.$anonfun$applyOrElse$3
scalaVersion in ThisBuild := "2.11.8" //TODO: 2.12 (quill is blocking)

lazy val commonSettings = Seq(
  resolvers ++= (
    ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") ::
    ("RoundEights" at "http://maven.spikemark.net/roundeights") ::
    Nil
  ),
  // watch managed library dependencies (only works with scala 2.11 currently)
  watchSources ++= (managedClasspath in Compile).map(_.files).value,
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Ywarn-unused" ::
    Nil,
  maxErrors := 5
)

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, backend, frameworkJS, frameworkJVM, frontend, graphJS, graphJVM, utilJS, utilJVM, nginxHttps, nginxHttp, dbMigration)
  .settings(
    publish := {},
    publishLocal := {},

    addCommandAlias("dev", "~; backend/re-start; workbench/compile"),
    addCommandAlias("devf", "~workbench/compile")
  )

val reactVersion = "15.4.2"
val akkaVersion = "2.4.16"
val d3v4FacadeVersion = "0.1.0-SNAPSHOT"

lazy val api = crossProject.crossType(CrossType.Pure)
  .dependsOn(graph)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      Nil
    )
  )
lazy val apiJS = api.js
lazy val apiJVM = api.jvm

lazy val graph = crossProject
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      "com.github.fdietze" %%% "vectory" % "0.1.0" ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      "com.github.fdietze" %%% "scala-js-d3v4-force" % d3v4FacadeVersion ::
      Nil
    )
  )
lazy val graphJS = graph.js
lazy val graphJVM = graph.jvm

lazy val util = crossProject
  .settings(commonSettings)
lazy val utilJS = util.js
lazy val utilJVM = util.jvm

lazy val framework = crossProject
  .dependsOn(util)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      "com.lihaoyi" %%% "autowire" % "0.2.6" ::
      "io.suzaku" %%% "boopickle" % "1.2.6" ::
      "com.outr" %%% "scribe" % "1.3.2" ::
      Nil
    )
  )
  .jvmSettings(
    libraryDependencies ++= (
      "com.typesafe.akka" %% "akka-http" % "10.0.3" ::
      "com.typesafe.akka" %% "akka-actor" % akkaVersion ::
      // "com.typesafe.akka" %% "akka-slf4j" % akkaVersion ::
      // "com.outr" %% "scribe-slf4j" % "1.3.2" :: //TODO
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      "org.scala-js" %%% "scalajs-dom" % "0.9.1" ::
      Nil
    )
  )

lazy val frameworkJS = framework.js
lazy val frameworkJVM = framework.jvm

lazy val frontend = project
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb)
  .dependsOn(frameworkJS, apiJS, utilJS)
  .settings(commonSettings)
  .settings(
    persistLauncher := true,
    persistLauncher in Test := false,

    libraryDependencies ++= (
      "io.suzaku" %%% "diode" % "1.1.1" ::
      "io.suzaku" %%% "diode-react" % "1.1.1" ::
      "com.github.japgolly.scalajs-react" %%% "core" % "0.11.3" ::
      "org.scala-js" %%% "scalajs-dom" % "0.9.1" ::
      "com.github.fdietze" %%% "vectory" % "0.1.0" ::
      "com.github.fdietze" %%% "scalajs-react-custom-component" % "0.1.0" ::

      "com.github.fdietze" %%% "scala-js-d3v4-selection" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-collection" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-dispatch" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-quadtree" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-timer" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-force" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-drag" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-interpolate" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-ease" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-transition" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-zoom" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-drag" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-polygon" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-shape" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-path" % d3v4FacadeVersion ::
      "com.github.fdietze" %%% "scala-js-d3v4-color" % d3v4FacadeVersion ::
      Nil
    ),

    jsDependencies ++= Seq(
      "org.webjars.bower" % "react" % reactVersion / "react-with-addons.js" minified "react-with-addons.min.js",
      "org.webjars.bower" % "react" % reactVersion / "react-dom.js" minified "react-dom.min.js" dependsOn "react-with-addons.js",
      "org.webjars.bower" % "react" % reactVersion / "react-dom-server.js" minified "react-dom-server.min.js" dependsOn "react-dom.js"
    )
  )

lazy val assets = project
  .enablePlugins(SbtWeb)
  .settings(
    unmanagedResourceDirectories in Assets += baseDirectory.value / "public",
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline, gzip) //TODO zopfli?
  )

lazy val backend = project
  .enablePlugins(DockerPlugin)
  .settings(dockerBackend)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .dependsOn(frameworkJVM, apiJVM)
  .settings(
    libraryDependencies ++=
      "io.getquill" %% "quill-async-postgres" % "1.0.1" ::
      "com.roundeights" %% "hasher" % "1.2.0" ::
      "org.mindrot" % "jbcrypt" % "0.3m" :: //TODO version 0.4?
      "org.specs2" %% "specs2-core" % "3.8.7" % "it,test" ::
      Nil,
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

def dockerImageName(name: String, version: String) = ImageName(
  namespace = Some("woost"),
  repository = name,
  tag = Some(version)
)

val dockerBackend = Seq(
  dockerfile in docker := {
    val artifact: File = assembly.value
    val artifactTargetPath = s"/app/${artifact.name}"

    new Dockerfile {
      from("openjdk:8-jre-alpine")
      copy(artifact, artifactTargetPath)
      workDir("/app")
      entryPoint("java", "-jar", artifactTargetPath)
    }
  },
  imageNames in docker := Seq(
    dockerImageName("wust2", "latest"),
    dockerImageName("wust2", version.value)
  )
)

lazy val workbench = project.in(file("workbench"))
  .enablePlugins(WorkbenchPlugin, SbtWeb)
  .dependsOn(assets)
  .settings(
    compile in Compile := ((compile in Compile) dependsOn WebKeys.assets).value,
    //TODO: deprecation-warning: https://github.com/sbt/sbt/issues/1444
    //TODO: do not refresh if compilation failed
    refreshBrowsers <<= refreshBrowsers.triggeredBy(compile in Compile)
  )

//TODO watchSources <++= baseDirectory map { p => (p / "reverse-proxy.conf").get } //TODO
lazy val nginxHttps = project.in(file("nginx/https"))
  .enablePlugins(DockerPlugin)
  .settings(dockerNginx(None))

lazy val nginxHttp = project.in(file("nginx/http"))
  .enablePlugins(DockerPlugin)
  .settings(dockerNginx(Some("http")))

def dockerNginx(tagPostfix: Option[String]) = Seq(
  dockerfile in docker := {
    val assetFolder = (WebKeys.assets in assets).value

    new Dockerfile {
      from("nginx:1.11.8-alpine")
      copy(baseDirectory(_ / "nginx-template-config.sh").value, "/nginx-template-config.sh")
      copy(baseDirectory(_ / "reverse-proxy.conf").value, "/templates/default.conf.tpl")
      run("chmod", "+x", "/nginx-template-config.sh")
      copy(assetFolder, "/public")
      entryPoint("/nginx-template-config.sh")
    }
  },
  imageNames in docker := Seq(
    dockerImageName("wust2.nginx", tagPostfix.getOrElse("latest")),
    dockerImageName("wust2.nginx", tagPostfix.map(_ + "-" + version.value).getOrElse(version.value))
  )
)

lazy val dbMigration = project
  .enablePlugins(DockerPlugin)
  .settings(dockerDbMigration)

val dockerDbMigration = Seq(
  dockerfile in docker := {
    new Dockerfile {
      from("dhoer/flyway:4.0.3-alpine")
      copy(baseDirectory(_ / "sql").value, "/flyway/sql")
      copy(baseDirectory(_ / "flyway-await-postgres.sh").value, "/flyway-await-postgres.sh")
      run("chmod", "+x", "/flyway-await-postgres.sh")
      entryPoint("/flyway-await-postgres.sh")
    }
  },
  imageNames in docker := Seq(
    dockerImageName("wust2.db-migration", "latest"),
    dockerImageName("wust2.db-migration", version.value)
  )
)
