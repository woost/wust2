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
      "com.github.fdietze" %%% "vectory" % "0.1.0-SNAPSHOT" ::
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
      "com.github.fdietze" %%% "vectory" % "0.1.0-SNAPSHOT" ::
      Nil
    ),

    jsDependencies ++= Seq(
      "org.webjars.bower" % "react" % reactVersion
        / "react-with-addons.js"
        minified "react-with-addons.min.js"
        commonJSName "React",

      "org.webjars.bower" % "react" % reactVersion
        / "react-dom.js"
        minified "react-dom.min.js"
        dependsOn "react-with-addons.js"
        commonJSName "ReactDOM",

      "org.webjars.bower" % "react" % reactVersion
        / "react-dom-server.js"
        minified "react-dom-server.min.js"
        dependsOn "react-dom.js"
        commonJSName "ReactDOMServer",

      "org.webjars.npm" % "d3-selection" % "1.0.2" / "d3-selection.js" minified "d3-selection.min.js",
      "org.webjars.npm" % "d3-collection" % "1.0.2" / "d3-collection.js" minified "d3-collection.min.js",
      "org.webjars.npm" % "d3-dispatch" % "1.0.2" / "d3-dispatch.js" minified "d3-dispatch.min.js",
      "org.webjars.npm" % "d3-quadtree" % "1.0.2" / "d3-quadtree.js" minified "d3-quadtree.min.js",
      "org.webjars.npm" % "d3-timer" % "1.0.2" / "d3-timer.js" minified "d3-timer.min.js",
      "org.webjars.npm" % "d3-force" % "1.0.4" / "d3-force.js" minified "d3-force.min.js",
      "org.webjars.npm" % "d3-zoom" % "1.1.1" / "d3-zoom.js" minified "d3-zoom.min.js",
      "org.webjars.npm" % "d3-transition" % "1.0.3" / "d3-transition.js" minified "d3-transition.min.js",
      "org.webjars.npm" % "d3-drag" % "1.0.2" / "d3-drag.js" minified "d3-drag.min.js"
    )
  )

lazy val backend = project
  .enablePlugins(SbtWeb)
  .settings(commonSettings: _*)
  .dependsOn(frameworkJVM, apiJVM)
  .settings(
    scalaJSProjects := Seq(frontend),
    pipelineStages in Assets := Seq(scalaJSPipeline),
    compile in Compile <<= ((compile in Compile) dependsOn scalaJSPipeline),
    // scalaJSDev <<= (scalaJSDev andFinally (refreshBrowsers in frontend)),
    // refreshBrowsers in frontend <<= ((refreshBrowsers in frontend).triggeredBy(packageBin)),
    // packageBin in Compile <<= ((packageBin in Compile) andFinally (refreshBrowsers in frontend)),
    // fastOptJS in Compile in frontend <<= ((fastOptJS in Compile in frontend) andFinally (refreshBrowsers in frontend)),
    WebKeys.packagePrefix in Assets := "public/",
    managedClasspath in Runtime += (packageBin in Assets).value
  )

// loads the server project at sbt startup
onLoad in Global := (Command.process("project backend", _: State)) compose (onLoad in Global).value
