name := "wust"

//TODO: report bug that this project does not compile with 2.12.1
// scala.tools.asm.tree.analysis.AnalyzerException: While processing backend/Server$$anonfun$$nestedInanonfun$router$1$1.$anonfun$applyOrElse$3
scalaVersion in ThisBuild := "2.11.8"

lazy val commonSettings = Seq(
  resolvers ++= (
    ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") ::
    ("RoundEights" at "http://maven.spikemark.net/roundeights") ::
    Nil
  ),
  scalacOptions ++=
    "-encoding" :: "UTF-8" ::
    "-unchecked" ::
    "-deprecation" ::
    "-explaintypes" ::
    "-feature" ::
    "-language:_" ::
    "-Ywarn-unused" ::
    Nil,
  // also watch managed library dependencies (only works with scala 2.11 currently)
  watchSources <++= (managedClasspath in Compile) map { cp => cp.files },
  maxErrors := 5
)

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, backend, frameworkJS, frameworkJVM, frontend, graphJS, graphJVM, utilJS, utilJVM)
  .settings(
    publish := {},
    publishLocal := {},
    addCommandAlias("dev", "~backend/re-start")
  )

val reactVersion = "15.4.1"
val akkaVersion = "2.4.14"
val d3v4FacadeVersion = "0.1.0-SNAPSHOT"

lazy val api = crossProject.crossType(CrossType.Pure)
  .dependsOn(graph)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= (
      Nil
    )
  )
lazy val apiJS = api.js
lazy val apiJVM = api.jvm

lazy val graph = crossProject
  .settings(commonSettings: _*)
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
  .settings(commonSettings: _*)
lazy val utilJS = util.js
lazy val utilJVM = util.jvm

lazy val framework = crossProject
  .dependsOn(util)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= (
      "com.lihaoyi" %%% "autowire" % "0.2.6" ::
      "me.chrons" %%% "boopickle" % "1.2.5" ::
      Nil
    )
  )
  .jvmSettings(
    libraryDependencies ++= (
      "com.typesafe.akka" %% "akka-http" % "10.0.0" ::
      "com.typesafe.akka" %% "akka-actor" % akkaVersion ::
      // "com.typesafe.akka" %% "akka-slf4j" % akkaVersion ::
      // "com.outr" %% "scribe-slf4j" % "1.3.2" :: //TODO
      "com.outr" %% "scribe" % "1.3.2" ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      "org.scala-js" %%% "scalajs-dom" % "0.9.1" ::
      "com.outr" %%% "scribe" % "1.3.2" ::
      Nil
    )
  )
lazy val frameworkJS = framework.js
lazy val frameworkJVM = framework.jvm

//TODO: source maps
lazy val frontend = project
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb /*, WorkbenchPlugin*/ )
  .dependsOn(frameworkJS, apiJS, utilJS)
  .settings(commonSettings: _*)
  .settings(
    persistLauncher := true,
    persistLauncher in Test := false,

    libraryDependencies ++= (
      "me.chrons" %%% "diode" % "1.1.0" ::
      "me.chrons" %%% "diode-react" % "1.1.0" ::
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
      Nil
    ),

    jsDependencies ++= Seq(
      "org.webjars.bower" % "react" % reactVersion / "react-with-addons.js" minified "react-with-addons.min.js",
      "org.webjars.bower" % "react" % reactVersion / "react-dom.js" minified "react-dom.min.js" dependsOn "react-with-addons.js",
      "org.webjars.bower" % "react" % reactVersion / "react-dom-server.js" minified "react-dom-server.min.js" dependsOn "react-dom.js"
    )
  )

lazy val backend = project
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .dependsOn(frameworkJVM, apiJVM)
  .settings(
    libraryDependencies ++=
      "io.getquill" %% "quill-async-postgres" % "1.0.1" ::
      "com.roundeights" %% "hasher" % "1.2.0" ::
      "org.mindrot" % "jbcrypt" % "0.3m" :: //TODO version 0.4?
      Nil,
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    compile in Compile := ((compile in Compile) dependsOn scalaJSPipeline).value,
    // scalaJSDev <<= (scalaJSDev andFinally (refreshBrowsers in frontend)),
    // refreshBrowsers in frontend <<= ((refreshBrowsers in frontend).triggeredBy(packageBin)),
    // packageBin in Compile <<= ((packageBin in Compile) andFinally (refreshBrowsers in frontend)),
    // fastOptJS in Compile in frontend <<= ((fastOptJS in Compile in frontend) andFinally (refreshBrowsers in frontend)),
    WebKeys.packagePrefix in Assets := "public/",
    managedClasspath in Runtime += (packageBin in Assets).value,
    libraryDependencies ++= Seq("org.specs2" %% "specs2-core" % "3.8.7" % "test"),
    scalacOptions in Test ++= Seq("-Yrangepos")
  )

// loads the server project at sbt startup
onLoad in Global := (Command.process("project backend", _: State)) compose (onLoad in Global).value
