name := "wust"

// docker versions do not allow '+'
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))

lazy val isCI = sys.env.get("CI").isDefined // set by travis, TODO: https://github.com/sbt/sbt/issues/3653

parallelExecution in ThisBuild := !isCI // https://github.com/scalacenter/scalajs-bundler/pull/225
concurrentRestrictions in Global ++= (if(isCI) List(Tags.limit(Tags.All, 1)) else Nil)

import Def.{setting => dep}

lazy val commonSettings = Seq(
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.12", "2.12.4"), // 2.11 is needed for android app

  exportJars := true, // for android app
  resolvers ++=
    /* ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") :: */
    Resolver.jcenterRepo ::
    ("jitpack" at "https://jitpack.io") ::
    Nil,

  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.6"),

  libraryDependencies ++=
    Deps.scribe.value ::
    Deps.scalatest.value % Test ::
    Deps.mockito.value % Test ::
    Nil,

  dependencyOverrides ++= Set(
    Deps.circe.core.value,
    Deps.circe.parser.value,
    Deps.circe.generic.value,
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
      val branch = sys.env.get("TRAVIS_BRANCH") getOrElse git.gitCurrentBranch.value
      if (branch == "master") "latest" else branch
    } ::
    version.value ::
        Nil
)

lazy val sourceMapSettings = Seq(
    // To enable source map support, all sub-project folders are symlinked to webpack/project-root.
    // This folder is served via webpack devserver.
    scalacOptions += {
      val local = s"${(baseDirectory in ThisBuild).value.toURI}"
      val remote = s"/"
      s"-P:scalajs:mapSourceURI:$local->$remote"
    }
  )


lazy val copyFastOptJS = TaskKey[Unit]("copyFastOptJS", "Copy javascript files to target directory")
lazy val webSettings = Seq(
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    requiresDOM := true, // still required by bundler: https://github.com/scalacenter/scalajs-bundler/issues/181
    scalaJSUseMainModuleInitializer := true,

    //TODO: scalaJSStage in Test := FullOptStage,
    //TODO: scalaJSOptimizerOptions in fastOptJS ~= { _.withDisableOptimizer(true) }, // disable optimizations for better debugging experience

    emitSourceMaps in fastOptJS := false, //TODO: scalaJSLinkerConfig instead of emitSOurceMaps, scalajsOptimizer,...
    emitSourceMaps in fullOptJS := false,

    useYarn := true, // instead of npm
    npmDependencies in Compile ++=
      /* "default-passive-events" -> "1.0.7" :: */
      Nil,
    npmDevDependencies in Compile ++=
      "webpack-closure-compiler" -> "2.1.6" ::
      "webpack-subresource-integrity" -> "1.0.4" ::
      "html-webpack-plugin" -> "2.30.1" ::
      "html-webpack-include-assets-plugin" -> "1.0.4" ::
      "clean-webpack-plugin" -> "0.1.18" ::
      "zopfli-webpack-plugin" -> "0.1.0" ::
      "brotli-webpack-plugin" -> "0.5.0" ::
      "node-sass" -> "4.7.2" ::
      "sass-loader" -> "6.0.6" ::
      "css-loader" -> "0.28.10" ::
      "style-loader" -> "0.20.2" ::
      "extract-text-webpack-plugin" -> "3.0.2" ::
      "offline-plugin" -> "4.9.0" ::
      "webpack-merge" -> "4.1.2" ::
      "copy-webpack-plugin" -> "4.5.0" ::
      Nil,

    version in webpack := "3.10.0",
    //TODO: version in startWebpackDevServer := "2.11.1",
    webpackResources := (baseDirectory.value / ".." / "utilWeb" / "webpack" ** "*.*"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.config.prod.js"),
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.config.dev.js"),
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(), // https://scalacenter.github.io/scalajs-bundler/cookbook.html#performance
    webpackDevServerExtraArgs := Seq("--progress", "--color"),

    // when running the "dev" alias, after every fastOptJS compile all artifacts are copied into
    // a folder which is served and watched by the webpack devserver.
    // this is a workaround for: https://github.com/scalacenter/scalajs-bundler/issues/180
    copyFastOptJS := {
      val inDir = (crossTarget in (Compile, fastOptJS)).value
      val outDir = (crossTarget in (Compile, fastOptJS)).value / "dev"
      val files = Seq(name.value.toLowerCase + "-fastopt-loader.js", name.value.toLowerCase + "-fastopt.js") map { p => (inDir / p, outDir / p) }
      IO.copy(files, overwrite = true, preserveLastModified = true)
    }
)

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, database, core, sdkJS, sdkJVM, webApp, pwaApp, idsJS, idsJVM, graphJS, graphJVM, utilJS, utilJVM, utilWeb, utilBackend, systemTest, dbMigration, slackApp, gitterApp, githubApp)
  .settings(
    publish := {},
    publishLocal := {},

    addCommandAlias("dev", "; core/compile; webApp/compile; webApp/fastOptJS::startWebpackDevServer; devwatch; webApp/fastOptJS::stopWebpackDevServer; core/reStop"),
    addCommandAlias("devwatch", "~; webApp/fastOptJS; webApp/copyFastOptJS; core/reStart"),

    addCommandAlias("devweb", "; core/compile; webApp/compile; core/reStart; project webApp; fastOptJS::startWebpackDevServer; devwatchandcopy; fastOptJS::stopWebpackDevServer; core/reStop; project root"),
    addCommandAlias("devpwa", "; core/compile; pwaApp/compile; core/reStart; project pwaApp; fastOptJS::startWebpackDevServer; devwatchandcopy; fastOptJS::stopWebpackDevServer; core/reStop; project root"),
    addCommandAlias("devwatchandcopy", "~; fastOptJS; copyFastOptJS"),
    addCommandAlias("deva", "; project androidApp; ++2.11.12; ~android:run; project root; ++2.12.4"),

    addCommandAlias("testJS", "; utilJS/test; utilWeb/test; graphJS/test; sdkJS/test; apiJS/test; webApp/test; pwaApp/test"),
    addCommandAlias("testJSNonPure", "; utilWeb/test; sdkJS/test; webApp/test; pwaApp/test"),
    addCommandAlias("testJSOpt", "; set scalaJSStage in Global := FullOptStage; testJS"),
    addCommandAlias("testJVM", "; utilJVM/test; utilBackend/test; graphJVM/test; sdkJVM/test; apiJVM/test; database/test; core/test; slackApp/test; gitterApp/test; githubApp/test"),

    // Avoid watching files in root project
    // TODO: is there a simpler less error-prone way to write this?
    // watchSources := Seq(apiJS, apiJVM, database, core, sdkJS, sdkJVM, webApp, graphJS, graphJVM, utilJS, utilJVM, systemTest, dbMigration, slackApp).flatMap(p => (watchSources in p).value)
    watchSources := (watchSources in apiJS).value ++ (watchSources in apiJVM).value ++ (watchSources in database).value ++ (watchSources in core).value ++ (watchSources in sdkJS).value ++ (watchSources in sdkJVM).value ++ (watchSources in webApp).value ++ (watchSources in idsJS).value ++ (watchSources in idsJVM).value ++ (watchSources in graphJS).value ++ (watchSources in graphJVM).value ++ (watchSources in utilJS).value ++ (watchSources in utilJVM).value ++ (watchSources in utilWeb).value ++ (watchSources in utilBackend).value ++ (watchSources in systemTest).value ++ (watchSources in dbMigration).value ++ (watchSources in slackApp).value ++ (watchSources in gitterApp).value ++ (watchSources in githubApp).value
  )

lazy val util = crossProject
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++=
      Deps.sourcecode.value ::
      Nil
  )

lazy val utilJS = util.js
lazy val utilJVM = util.jvm

lazy val utilBackend = project
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.pureconfig.value ::
      Nil
  )

lazy val utilWeb = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(sdkJS)
  .settings(commonSettings, sourceMapSettings)
  .settings(
    libraryDependencies ++=
      Deps.scalarx.value ::
      Deps.outwatch.value ::
      Deps.monocle.value ::
      Deps.circe.core.value ::
      Deps.circe.generic.value ::
      Deps.circe.parser.value ::
      Nil,

    npmDependencies in Compile ++=
      "marked" -> "0.3.12" ::
      Nil
  )

lazy val sdk = crossProject
  .dependsOn(api)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++=
      Deps.covenant.ws.value ::
      Deps.covenant.http.value ::
      Deps.boopickle.value ::
      Deps.monix.value ::
      Deps.colorado.value ::
      Nil
  )

lazy val sdkJS = sdk.js
lazy val sdkJVM = sdk.jvm

lazy val ids = crossProject.crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++=
      Deps.cuid.value ::
      Deps.taggedTypes.value ::
      Nil
  )
lazy val idsJS = ids.js
lazy val idsJVM = ids.jvm

lazy val graph = crossProject.crossType(CrossType.Pure)
  .dependsOn(ids, util)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++=
      Nil
  )
lazy val graphJS = graph.js
lazy val graphJVM = graph.jvm

lazy val api = crossProject.crossType(CrossType.Pure)
  .dependsOn(graph)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++=
      Deps.covenant.core.value ::
      Deps.boopickle.value % Optional ::
      Deps.circe.core.value % Optional ::
      Deps.circe.generic.value % Optional ::
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
      Deps.boopickle.value ::
      Deps.cats.kittens.value ::
      Deps.jwt.value ::
      Deps.hasher.value ::
      Deps.jbcrypt.value ::
      Deps.javaMail.value ::
      Deps.monix.value ::
      Deps.github4s.value ::
      Deps.gitterSync.value ::
      Deps.stringmetric.value ::
      Deps.webPush.value ::
      Nil,

    javaOptions in reStart += "-Xmx50m"
  )

lazy val webApp = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(utilWeb)
  .settings(commonSettings, sourceMapSettings, webSettings)
  .settings(
    libraryDependencies ++=
      Deps.vectory.value ::
      Deps.d3v4.value ::
      Nil
  )

lazy val pwaApp = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(utilWeb)
  .settings(commonSettings, sourceMapSettings, webSettings)
  .settings(
    libraryDependencies ++=
      Nil
    )
lazy val androidApp = project
  .settings(commonSettings)
  .dependsOn(sdkJVM)
  .enablePlugins(AndroidApp)
  .settings(

    resolvers ++= (
      ("google" at "https://maven.google.com") ::
      // ("vivareal" at "http://dl.bintray.com/vivareal/maven") ::
      Nil
    ),

    android.useSupportVectors,
    versionCode := Some(1),
    version := "0.1-SNAPSHOT",
    platformTarget := "android-25",
    minSdkVersion in Android :="25",
    targetSdkVersion in Android := "25",
    javacOptions in Compile ++= "-source" :: "1.7" :: "-target" :: "1.7" :: Nil,
    libraryDependencies ++=
      aar("org.macroid" %% "macroid" % "2.0") ::
      aar("org.macroid" %% "macroid-extras" % "2.0") ::
      "com.android.support" % "appcompat-v7" % "24.0.0" ::
      "com.android.support.constraint" % "constraint-layout" % "1.0.2" ::
      // "br.com.vivareal" % "cuid-android" % "0.1.0" ::
      Nil,
    dependencyOverrides ++= Set(
    ), 
    // excludeDependencies += "cool.graph.cuid-java",

    dexMaxHeap in Android :="8048M",
    dexMulti in Android := true,
    // dexAdditionalParams in Android ++= Seq("--min-sdk-version=25"),
    proguardScala in Android := true,
    proguardCache := Nil,
    shrinkResources := true,

    proguardOptions in Android ++= Seq(
      "-ignorewarnings"
    ),
    packagingOptions in Android := PackagingOptions(excludes = Seq("reference.conf"))
  )

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
      Nil
  )

lazy val githubApp = project
  .dependsOn(utilBackend, sdkJVM)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
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

