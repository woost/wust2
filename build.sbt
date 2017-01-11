name := "wust"

//TODO: report bug that this project does not compile with 2.12.1
// scala.tools.asm.tree.analysis.AnalyzerException: While processing backend/Server$$anonfun$$nestedInanonfun$router$1$1.$anonfun$applyOrElse$3
scalaVersion in ThisBuild := "2.11.8"

lazy val commonSettings = Seq(
  resolvers ++= (
    ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") ::
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
    Nil
)

lazy val root = project.in(file("."))
  .settings(
    publish := {},
    publishLocal := {},
    addCommandAlias("dev", "~backend/re-start")
  // also watch managed library dependencies
  // watchSources <++= (managedClasspath in Compile) map { cp => cp.files }
  )

val reactVersion = "15.4.1"
val akkaVersion = "2.4.14"

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
      "com.github.fdietze" %%% "scala-js-d3v4-force" % "1.0.4" ::
      Nil
    )
  )
lazy val graphJS = graph.js
lazy val graphJVM = graph.jvm

lazy val framework = crossProject
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

//TODO: source maps
lazy val frontend = project
  .enablePlugins(ScalaJSPlugin, ScalaJSWeb /*, WorkbenchPlugin*/ )
  .dependsOn(frameworkJS, apiJS)
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

      "com.github.fdietze" %%% "scala-js-d3v4-selection" % "1.0.3" ::
      "com.github.fdietze" %%% "scala-js-d3v4-collection" % "1.0.2" ::
      "com.github.fdietze" %%% "scala-js-d3v4-dispatch" % "1.0.2" ::
      "com.github.fdietze" %%% "scala-js-d3v4-quadtree" % "1.0.2" ::
      "com.github.fdietze" %%% "scala-js-d3v4-timer" % "1.0.3" ::
      "com.github.fdietze" %%% "scala-js-d3v4-force" % "1.0.4" ::
      "com.github.fdietze" %%% "scala-js-d3v4-interpolate" % "1.1.2" ::
      "com.github.fdietze" %%% "scala-js-d3v4-ease" % "1.0.2" ::
      "com.github.fdietze" %%% "scala-js-d3v4-transition" % "1.0.3" ::
      "com.github.fdietze" %%% "scala-js-d3v4-zoom" % "1.1.1" ::
      "com.github.fdietze" %%% "scala-js-d3v4-drag" % "1.0.2" ::
      "com.github.fdietze" %%% "scala-js-d3v4-polygon" % "1.0.2" ::
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
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    compile in Compile := ((compile in Compile) dependsOn scalaJSPipeline).value,
    // scalaJSDev <<= (scalaJSDev andFinally (refreshBrowsers in frontend)),
    // refreshBrowsers in frontend <<= ((refreshBrowsers in frontend).triggeredBy(packageBin)),
    // packageBin in Compile <<= ((packageBin in Compile) andFinally (refreshBrowsers in frontend)),
    // fastOptJS in Compile in frontend <<= ((fastOptJS in Compile in frontend) andFinally (refreshBrowsers in frontend)),
    WebKeys.packagePrefix in Assets := "public/",
    managedClasspath in Runtime += (packageBin in Assets).value
  )

// loads the server project at sbt startup
onLoad in Global := (Command.process("project backend", _: State)) compose (onLoad in Global).value
