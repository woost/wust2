name := "wust"

enablePlugins(GitVersioning)
git.useGitDescribe := true
git.baseVersion := "0.1.0"
git.uncommittedSignifier := None // TODO: appends SNAPSHOT to version, but is always(!) active.

//TODO: report bug that this project does not compile with 2.12.1
// scala.tools.asm.tree.analysis.AnalyzerException: While processing backend/Server$$anonfun$$nestedInanonfun$router$1$1.$anonfun$applyOrElse$3
scalaVersion in ThisBuild := "2.11.8" //TODO: migrate to 2.12 when this PR is merged: https://github.com/getquill/quill/pull/617

lazy val commonSettings = Seq(
  resolvers ++= (
    ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") ::
    ("RoundEights" at "http://maven.spikemark.net/roundeights") ::
    Nil
  ),

  // do not run tests in assembly command
  test in assembly := {},

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
    Nil
)

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, backend, frameworkJS, frameworkJVM, frontend, graphJS, graphJVM, utilJS, utilJVM, systemTest, nginxHttps, nginxHttp, dbMigration)
  .settings(
    publish := {},
    publishLocal := {},

    addCommandAlias("clean", "; root/clean; assets/clean; workbench/clean"),

    addCommandAlias("devwatch", "~; backend/re-start; workbench/assets"),
    addCommandAlias("dev", "; project root; devwatch"),
    addCommandAlias("devfwatch", "~workbench/assets"),
    addCommandAlias("devf", "; project root; backend/re-start; devfwatch"),

    addCommandAlias("testJS", "; utilJS/test; graphJS/test; frameworkJS/test; apiJS/test; frontend/test"),
    addCommandAlias("testJVM", "; utilJVM/test; graphJVM/test; frameworkJVM/test; apiJVM/test; backend/test"),

    watchSources ++= (watchSources in workbench).value
  )

val akkaVersion = "2.4.17"
val akkaHttpVersion = "10.0.4"
val specs2Version = "3.8.8"
val scalaTestVersion = "3.0.1"

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
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ::
      Nil
    )
  )
  .dependsOn(util)
lazy val graphJS = graph.js
lazy val graphJVM = graph.jvm

lazy val util = crossProject
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ::
      Nil
    )
  )
lazy val utilJS = util.js
lazy val utilJVM = util.jvm

lazy val framework = crossProject
  .dependsOn(util)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      "com.lihaoyi" %%% "autowire" % "0.2.6" ::
      "io.suzaku" %%% "boopickle" % "1.2.6" ::
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ::
      Nil
    )
  )
  .jvmSettings(
    libraryDependencies ++= (
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion ::
      "com.typesafe.akka" %% "akka-actor" % akkaVersion ::
      "com.outr" %% "scribe" % "1.4.1" ::
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
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(frameworkJS, apiJS, utilJS)
  .settings(commonSettings)
  .settings(
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    libraryDependencies ++= (
      "com.timushev" %%% "scalatags-rx" % "0.3.0" ::
      "com.github.fdietze" %%% "vectory" % "0.1.0" ::
      "com.github.fdietze" %%% "scala-js-d3v4" % "0.1.0-SNAPSHOT" ::
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ::
      Nil
    ),
    scalaJSOptimizerOptions in fastOptJS ~= { _.withDisableOptimizer(true) }, // disable optimizations for better debugging experience
    scalaJSOptimizerOptions in fullOptJS ~= { _.withDisableOptimizer(true) }, // TODO: issue with fullOpt: https://github.com/scala-js/scala-js/issues/2786
    useYarn := true, // instead of npm
    enableReloadWorkflow := true, // https://scalacenter.github.io/scalajs-bundler/reference.html#reload-workflow
    emitSourceMaps := true,
    npmDevDependencies in Compile ++= (
      "compression-webpack-plugin" -> "0.3.1" ::
      "brotli-webpack-plugin" -> "0.2.0" ::
      "webpack-closure-compiler" -> "2.1.4" ::
      Nil
    ),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.config.js")
  )

lazy val workbench = project.in(file("workbench"))
  .enablePlugins(WorkbenchPlugin, SbtWeb, ScalaJSWeb, WebScalaJSBundlerPlugin)
  .settings(
    // we have a symbolic link from src -> ../frontend/src
    // to correct the paths in the source-map
    scalaSource := baseDirectory.value / "src-not-found",

    devCommands in scalaJSPipeline ++= Seq("assets"), // build assets in dev mode
    unmanagedResourceDirectories in Assets += (baseDirectory in assets).value / "public", // include other assets

    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),

    watchSources += baseDirectory.value / "index.html",
    watchSources ++= (watchSources in assets).value,
    //TODO: deprecation-warning: https://github.com/sbt/sbt/issues/1444
    refreshBrowsers <<= refreshBrowsers.triggeredBy(WebKeys.assets in Assets) //TODO: do not refresh if compilation failed
  )

lazy val assets = project
  .enablePlugins(SbtWeb, ScalaJSWeb, WebScalaJSBundlerPlugin)
  .settings(
    unmanagedResourceDirectories in Assets += baseDirectory.value / "public",
    scalaJSProjects := Seq(frontend),
    npmAssets ++= {
      // without dependsOn, the file list is generated before webpack does its thing.
      // Which would mean that generated files by webpack do not land in the pipeline.
      val assets = ((npmUpdate in Compile in frontend).dependsOn(webpack in fullOptJS in Compile in frontend).value ** "*.gz") +++ ((npmUpdate in Compile in frontend).dependsOn(webpack in fullOptJS in Compile in frontend).value ** "*.br")
      val nodeModules = (npmUpdate in (frontend, Compile)).value
      assets.pair(relativeTo(nodeModules))
    },
    pipelineStages in Assets := Seq(scalaJSPipeline)
  //TODO: minify html
  )

lazy val backend = project
  .enablePlugins(DockerPlugin)
  .settings(dockerBackend)
  .settings(commonSettings)
  .dependsOn(frameworkJVM, apiJVM)
  .settings(
    libraryDependencies ++=
      "io.getquill" %% "quill-async-postgres" % "1.1.0" ::
      "com.roundeights" %% "hasher" % "1.2.0" ::
      "org.mindrot" % "jbcrypt" % "0.4" ::
      "io.igl" %% "jwt" % "1.2.0" ::
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ::
      Nil
  )

lazy val systemTest = project
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion % "it" ::
      "com.typesafe.akka" %% "akka-actor" % akkaVersion % "it" ::
      "org.specs2" %% "specs2-core" % specs2Version % "it" ::
      "org.seleniumhq.selenium" % "selenium-java" % "3.2.0" % "it" ::
      Nil,
    scalacOptions in Test ++= Seq("-Yrangepos") // specs2
  )

def dockerImageName(name: String, version: String) = ImageName(
  namespace = Some("woost"),
  repository = name,
  tag = Some(version)
)

val dockerBackend = Seq(
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
  },
  imageNames in docker := Seq(
    dockerImageName("wust2", "latest"),
    dockerImageName("wust2", version.value)
  )
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
      copy(baseDirectory(_ / ".." / "nginx-template-config.sh").value, "/nginx-template-config.sh")
      copy(baseDirectory(_ / "reverse-proxy.conf").value, "/templates/default.conf.tpl")
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
      run("adduser", "user", "-D", "-u", "1000")
      run("chown", "-R", "user:user", "/flyway")
      user("user")
      copy(baseDirectory(_ / "sql").value, "/flyway/sql")
      copy(baseDirectory(_ / "flyway-await-postgres.sh").value, s"/flyway/flyway-await-postgres.sh")
      entryPoint("/flyway/flyway-await-postgres.sh")
    }
  },
  imageNames in docker := Seq(
    dockerImageName("wust2.db-migration", "latest"),
    dockerImageName("wust2.db-migration", version.value)
  )
)
