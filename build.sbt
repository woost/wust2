name := "wust"

scalaVersion in ThisBuild := "2.11.8"

lazy val commonSettings = Seq(
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

addCommandAlias("dev", "~; backend/re-start; frontend/fastOptJS")

// also watch managed library dependencies
watchSources <++= (managedClasspath in Compile) map { cp => cp.files }

val autowireVersion = "0.2.6"
val boopickleVersion = "1.2.5"
val reactVersion = "15.4.1"
val akkaVersion = "2.4.14"

lazy val api = crossProject.crossType(CrossType.Pure)
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= (
      "com.lihaoyi" %%% "autowire" % autowireVersion ::
      "me.chrons" %%% "boopickle" % boopickleVersion ::
      Nil
    )
  )
lazy val apiJS = api.js
lazy val apiJVM = api.jvm

lazy val frontend = project
  .enablePlugins(ScalaJSPlugin, WorkbenchPlugin)
  .disablePlugins(RevolverPlugin)
  .dependsOn(apiJS)
  .settings(commonSettings: _*)
  .settings(
    persistLauncher := true,
    persistLauncher in Test := false,

    libraryDependencies ++= (
      "com.lihaoyi" %%% "autowire" % autowireVersion ::
      "me.chrons" %%% "boopickle" % boopickleVersion ::
      "me.chrons" %%% "diode" % "1.1.0" ::
      "me.chrons" %%% "diode-react" % "1.1.0" ::
      "com.github.japgolly.scalajs-react" %%% "core" % "0.11.3" ::
      "org.scala-js" %%% "scalajs-dom" % "0.9.1" ::
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
        commonJSName "ReactDOMServer"
    )
  )

lazy val backend = project
  .enablePlugins(sbtdocker.DockerPlugin)
  .settings(dockerizeBackend: _*)
  .settings(commonSettings: _*)
  .dependsOn(apiJVM)
  .settings(
    libraryDependencies ++= (
      "com.typesafe.akka" %% "akka-http" % "10.0.0" ::
      "com.typesafe.akka" %% "akka-actor" % akkaVersion ::
      "com.lihaoyi" %%% "autowire" % autowireVersion ::
      "me.chrons" %%% "boopickle" % boopickleVersion ::
      Nil
    )
  // declare frontend compiled artifacts as backend resources
  // and therefore depend on frontend compilation
  // (resources in Compile) ++= (
  //   {
  //     (fastOptJS in (frontend, Compile)).value
  //     (artifactPath in (frontend, Compile, fastOptJS)).value
  //   } ::
  //   {
  //     (fastOptJS in (frontend, Compile)).value
  //     (artifactPath in (frontend, Compile, packageScalaJSLauncher)).value
  //   } ::
  //   {
  //     (packageJSDependencies in (frontend, Compile)).value
  //     (artifactPath in (frontend, Compile, packageJSDependencies)).value
  //   } ::
  //   Nil
  // )
  )

val dockerOrganization = "woost"
val dockerBackendName = "wust2"
def dockerImageName(name: String, version: String) = ImageName(
  namespace = Some(dockerOrganization),
  repository = name,
  tag = Some(version)
)

lazy val dockerizeBackend = Seq(
  dockerfile in docker := {
    // The assembly task generates a fat JAR file
    val artifact: File = assembly.value
    val artifactTargetPath = s"/app/${artifact.name}"

    new Dockerfile {
      from("anapsix/alpine-java")
      add(artifact, artifactTargetPath)
      entryPoint("java", "-jar", artifactTargetPath)
    }
  },
  imageNames in docker := Seq(
    dockerImageName(dockerBackendName, "latest"),
    dockerImageName(dockerBackendName, s"v${version.value}")
  )
)
