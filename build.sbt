// shadow sbt-scalajs' crossProject and CrossType until Scala.js 1.0.0 is released
import sbtcrossproject.{crossProject, CrossType}

name := "wust"

// docker versions do not allow '+'
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-')) // TODO: https://github.com/dwijnand/sbt-dynver/issues/52

import Def.{setting => dep}

lazy val commonSettings = Seq(
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq("2.11.12", "2.12.6"), // 2.11 is needed for android app

//  exportJars := true, // for android app
  resolvers ++=
    ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") ::
    Resolver.jcenterRepo ::
    ("jitpack" at "https://jitpack.io") ::
    Nil,

  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),

  libraryDependencies ++=
    Deps.scribe.value ::
    Deps.sourcecode.value ::
    Deps.scalatest.value % Test ::
    Deps.mockito.value % Test ::
    Nil,

  dependencyOverrides ++= List(
    Deps.circe.core.value,
    Deps.circe.parser.value,
    Deps.circe.generic.value,
    Deps.circe.genericExtras.value,
    Deps.cats.core.value,
    Deps.akka.httpCore.value
  ),

  // do not run tests in assembly command
  test in assembly := {},

  // watch managed library dependencies https://github.com/sbt/sbt/issues/2834
  watchSources ++= (managedClasspath in Compile).map(_.files).value,

  scalacOptions ++=
      // https://www.threatstack.com/blog/useful-scalac-options-for-better-scala-development-part-1/
      // https://tpolecat.github.io/2017/04/25/scalac-flags.html
      "-encoding" :: "UTF-8" ::
      "-unchecked" :: // Enable additional warnings where generated code depends on assumptions
      "-deprecation" ::
      "-explaintypes" :: // Explain type errors in more detail
      "-feature" ::
      "-language:_" ::
      "-Xfuture" ::
      "-Yno-adapted-args" ::
      "-Ywarn-infer-any" ::
      "-Ywarn-nullary-override" ::
      "-Ywarn-nullary-unit" ::
      Nil,

    scalacOptions ++=(CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, scalaMajor)) if scalaMajor >= 12 =>
        "-Xlint:-unused,_" ::
        "-Ywarn-unused:-imports" ::
        // "-Ywarn-dead-code" :: // does not work with js.native
        "-Ywarn-extra-implicit" ::
        Nil
      case _ => Nil
    }),

  Defs.dockerVersionTags :=
    {
      val branch = sys.env.get("TRAVIS_BRANCH") orElse scala.util.Try(git.gitCurrentBranch.value).filter(_.nonEmpty).toOption getOrElse "dirty"
      if (branch == "master") "latest" else branch
    } ::
    version.value ::
        Nil
)

lazy val commonWebSettings = Seq(
  useYarn := true, // makes scalajs-bundler use yarn instead of npm

  scalacOptions += "-P:scalajs:sjsDefinedByDefault",

  scalacOptions ++= {
    if(isDevRun.?.value.getOrElse(false)) {
      // To enable dev source map support,
      // The whole project root folder is served via webpack devserver.
      val local = s"${(baseDirectory in ThisBuild).value.toURI}"
      val remote = s"/"
      Some(s"-P:scalajs:mapSourceURI:$local->$remote")
    } else {
      // enable production source-map support and link to correct commit hash on github:
      git.gitHeadCommit.value.map { headCommit =>
        val local = (baseDirectory in ThisBuild).value.toURI
        val remote = s"https://raw.githubusercontent.com/woost/wust2/${headCommit}/"
        s"-P:scalajs:mapSourceURI:$local->$remote"
      }
    }
  }
)


val withSourceMaps:Boolean = sys.env.get("SOURCEMAPS").fold(false)(_ == "true")
lazy val isDevRun = TaskKey[Boolean]("isDevRun", "is full opt") //TODO derive from scalaJSStage value
lazy val copyFastOptJS = TaskKey[Unit]("copyFastOptJS", "Copy javascript files to target directory")
lazy val webSettings = Seq(
    requiresDOM := true, // still required by bundler: https://github.com/scalacenter/scalajs-bundler/issues/181
    scalaJSUseMainModuleInitializer := true,

    //TODO ?: scalaJSStage in Test := FullOptStage,

    scalaJSLinkerConfig in (Compile, fastOptJS) ~= { _.withSourceMap(withSourceMaps).withOptimizer(!withSourceMaps) }, // disable optimizations for better debugging experience
    scalaJSLinkerConfig in (Compile, fullOptJS) ~= { _.withSourceMap(withSourceMaps) },



    npmDevDependencies in Compile ++= Deps.npm.webpackDependencies,

    version in webpack := Deps.webpackVersion,
    version in startWebpackDevServer := Deps.webpackDevServerVersion,

    webpackResources := (baseDirectory.value / "webpack" ** "*.*"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack" / "webpack.config.prod.js"),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack" / "webpack.config.dev.js"),
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(), // https://scalacenter.github.io/scalajs-bundler/cookbook.html#performance
    webpackDevServerExtraArgs := Seq("--progress", "--color"),

    // when running the "dev" alias, after every fastOptJS compile all artifacts are copied into
    // a folder which is served and watched by the webpack devserver.
    // this is a workaround for: https://github.com/scalacenter/scalajs-bundler/issues/180
    copyFastOptJS := {
      val inDir = (crossTarget in (Compile, fastOptJS)).value
      val outDir = (crossTarget in (Compile, fastOptJS)).value / "dev"
      val outputFiles = Seq(
        name.value.toLowerCase + "-fastopt-loader.js",
        name.value.toLowerCase + "-fastopt.js"
      )
      val sourceMapFiles = if(withSourceMaps) Seq(name.value.toLowerCase + "-fastopt.js.map") else Seq.empty
      val files = (outputFiles ++ sourceMapFiles) map { p => (inDir / p, outDir / p) }
      IO.copy(files, overwrite = true, preserveLastModified = true, preserveExecutable = true)
    }
)

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, database, core, sdkJS, sdkJVM, webApp, idsJS, idsJVM, graphJS, graphJVM, utilJS, utilJVM, utilBackend, systemTest, dbMigration, slackApp, gitterApp, githubApp)
  .settings(
    publish := {},
    publishLocal := {},

    addCommandAlias("dev", "; set every isDevRun := true; set scalacOptions += \"-Xcheckinit\"; core/compile; webApp/compile; webApp/fastOptJS::startWebpackDevServer; devwebwatch_; webApp/fastOptJS::stopWebpackDevServer; core/reStop"),
    addCommandAlias("devwebwatch_", "~; core/reStop; webApp/fastOptJS; webApp/copyFastOptJS; core/reStart"),

    addCommandAlias("devf", "; set scalacOptions += \"-Xcheckinit\"; core/compile; webApp/compile; core/reStart; project webApp; fastOptJS::startWebpackDevServer; devwatchandcopy; fastOptJS::stopWebpackDevServer; core/reStop; project root"),
    addCommandAlias("devwatchandcopy", "~; fastOptJS; copyFastOptJS"),
    // addCommandAlias("deva", "; project androidApp; ++2.11.12; ~android:run; project root; ++2.12.6"),

    // addCommandAlias("compileAndroid", "; project androidApp; ++2.11.12; compile; project root; ++2.12.6"),
    addCommandAlias("testJS", "; set scalacOptions += \"-Xcheckinit\"; utilJS/test; graphJS/test; sdkJS/test; apiJS/test; webApp/test"),
    addCommandAlias("testJSNonPure", "; set scalacOptions += \"-Xcheckinit\"; sdkJS/test; webApp/test"),
    addCommandAlias("testJSOpt", "; set scalacOptions += \"-Xcheckinit\"; set scalaJSStage in Global := FullOptStage; testJS"),
    addCommandAlias("testJVM", "; set scalacOptions += \"-Xcheckinit\"; utilJVM/test; utilBackend/test; graphJVM/test; sdkJVM/test; apiJVM/test; database/test; core/test; slackApp/test; gitterApp/test; githubApp/test"),

    // Avoid watching files in root project
    // TODO: is there a simpler less error-prone way to write this?
    // watchSources := Seq(apiJS, apiJVM, database, core, sdkJS, sdkJVM, graphJS, graphJVM, utilJS, utilJVM, systemTest, dbMigration, slackApp).flatMap(p => (watchSources in p).value)
    watchSources := (watchSources in apiJS).value ++ (watchSources in apiJVM).value ++ (watchSources in database).value ++ (watchSources in core).value ++ (watchSources in sdkJS).value ++ (watchSources in sdkJVM).value ++ (watchSources in idsJS).value ++ (watchSources in idsJVM).value ++ (watchSources in graphJS).value ++ (watchSources in graphJVM).value ++ (watchSources in utilJS).value ++ (watchSources in utilJVM).value ++ (watchSources in utilBackend).value ++ (watchSources in systemTest).value ++ (watchSources in dbMigration).value ++ (watchSources in slackApp).value ++ (watchSources in gitterApp).value ++ (watchSources in githubApp).value
  )

lazy val util = crossProject(JSPlatform, JVMPlatform)
  .settings(commonSettings)
  .jsSettings(commonWebSettings)
  .settings(
    libraryDependencies ++=
      Deps.cats.core.value ::
      Nil
  )

lazy val utilJS = util.js
lazy val utilJVM = util.jvm

lazy val utilBackend = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.pureconfig.value ::
      Deps.akka.http.value ::
      Deps.akka.stream.value ::
      Nil
  )

lazy val sdk = crossProject(JSPlatform, JVMPlatform)
  .dependsOn(api)
  .settings(commonSettings)
  .jsSettings(commonWebSettings)
  .settings(
    libraryDependencies ++=
      Deps.covenant.ws.value ::
      Deps.covenant.http.value ::
      Deps.monix.value ::
      Deps.colorado.value ::
      Nil
  )

lazy val sdkJS = sdk.js
lazy val sdkJVM = sdk.jvm

//TODO: rename to atoms/basetypes
lazy val ids = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(commonWebSettings)
  .settings(
    libraryDependencies ++=
      Deps.cuid.value ::
      Deps.taggedTypes.value ::
      Deps.circe.core.value % Optional ::
      Deps.circe.genericExtras.value % Optional ::
      Deps.boopickle.value % Optional ::
      Nil
  )
lazy val idsJS = ids.js
lazy val idsJVM = ids.jvm

lazy val graph = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure)
  .dependsOn(ids, util)
  .settings(commonSettings)
  .jsSettings(commonWebSettings)
  .settings(
    libraryDependencies ++=
      Nil
  )
lazy val graphJS = graph.js
lazy val graphJVM = graph.jvm

lazy val api = crossProject(JSPlatform, JVMPlatform).crossType(CrossType.Pure)
  .dependsOn(graph)
  .settings(commonSettings)
  .jsSettings(commonWebSettings)
  .settings(
    libraryDependencies ++=
      Deps.covenant.core.value ::
      Deps.boopickle.value ::
      Deps.circe.core.value ::
      Deps.circe.parser.value ::
      Deps.circe.genericExtras.value ::
      Nil
  )
lazy val apiJS = api.js
lazy val apiJVM = api.jvm

lazy val database = project
  .dependsOn(idsJVM, utilJVM)
  .settings(commonSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    libraryDependencies ++=
      Deps.circe.core.value ::
      Deps.circe.parser.value ::
      Deps.circe.genericExtras.value ::
      Deps.quill.value ::
      Deps.scalatest.value % IntegrationTest ::
      Nil
  // parallelExecution in IntegrationTest := false
  )

lazy val core = project
  .dependsOn(utilBackend, apiJVM, database)
  .settings(commonSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    libraryDependencies ++=
      Deps.covenant.ws.value ::
      Deps.covenant.http.value ::
      Deps.akka.httpCors.value ::
      Deps.boopickle.value ::
      Deps.cats.kittens.value ::
      Deps.jwt.value ::
      Deps.hasher.value ::
      Deps.jbcrypt.value ::
      Deps.javaMail.value ::
      Deps.monix.value ::
      Deps.stringmetric.value ::
      Deps.webPush.value ::
      Nil,

    javaOptions in reStart += "-Xmx50m"
  )

lazy val webApp = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(sdkJS)
  .settings(commonSettings, commonWebSettings, webSettings)
  .settings(
    libraryDependencies ++=
      Deps.scalaJsDom.value ::
      Deps.scalarx.value ::
      Deps.outwatch.value ::
      Deps.monocle.value ::
      Deps.vectory.value ::
      Deps.d3v4.value ::
      Deps.fastparse.value ::
      Deps.fontawesome.value ::
      Nil,

    npmDependencies in Compile ++=
      // Deps.npm.defaultPassiveEvents ::
      Deps.npm.marked ::
        Nil
    )


// lazy val androidApp = project
//   .settings(commonSettings)
//   .dependsOn(sdkJVM)
//   .enablePlugins(AndroidGms, AndroidApp)
//   .settings(

//     resolvers ++= (
//       ("google" at "https://maven.google.com") ::
//       // ("vivareal" at "http://dl.bintray.com/vivareal/maven") ::
//       Nil
//     ),

//     android.useSupportVectors,
//     versionCode := Some(1),
//     version := "0.1-SNAPSHOT",
//     platformTarget := "android-25",
//     minSdkVersion in Android :="25",
//     targetSdkVersion in Android := "25",
//     javacOptions in Compile ++= "-source" :: "1.7" :: "-target" :: "1.7" :: Nil,
//     libraryDependencies ++=
//       aar("org.macroid" %% "macroid" % "2.0") ::
//       aar("org.macroid" %% "macroid-extras" % "2.0") ::
//       "com.android.support" % "appcompat-v7" % "24.2.1" ::
//       "com.android.support.constraint" % "constraint-layout" % "1.0.2" ::
//         "com.google.firebase"       % "firebase-messaging" % "11.8.0" ::
//       "com.google.android.gms"    % "play-services"     % "11.8.0" ::
// //      "com.google.firebase" % "firebase-core" % "11.8.0" ::
//       // "br.com.vivareal" % "cuid-android" % "0.1.0" ::
//       Nil,
//     dependencyOverrides ++= Set(
//     ), 
//     // excludeDependencies += "cool.graph.cuid-java",

//     dexMaxHeap in Android :="8048M",
//     dexMulti in Android := true,
//     // dexAdditionalParams in Android ++= Seq("--min-sdk-version=25"),
//     proguardScala in Android := true,
//     proguardCache := Nil,
//     shrinkResources := true,

//     proguardOptions in Android ++= Seq(
//       "-ignorewarnings"
//     ),
// //    proguardConfig -= "-dontoptimize",
//     packagingOptions in Android := PackagingOptions(excludes = Seq("reference.conf"))
//   )

lazy val slackApp = project
  .dependsOn(utilBackend, sdkJVM)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.slackClient.value ::
      Nil
  )

lazy val gitterApp = project
  .dependsOn(utilBackend, sdkJVM)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.gitterClient.value ::
      Deps.gitterSync.value ::
      Nil
  )

lazy val githubApp = project
  .dependsOn(utilBackend, sdkJVM)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.akka.httpCors.value ::
      Deps.github4s.value ::
      Deps.graphQl.value ::
      Deps.akka.httpCirce.value ::
      Deps.redis.value ::
      Nil
  )

lazy val systemTest = project
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.akka.http.value % IntegrationTest ::
      Deps.akka.actor.value % IntegrationTest ::
      Deps.akka.stream.value % IntegrationTest ::
      Deps.specs2.value % IntegrationTest ::
      Deps.selenium.value % IntegrationTest ::
      Nil,
    scalacOptions in Test ++= Seq("-Yrangepos") // specs2
  )

lazy val dbMigration = project

