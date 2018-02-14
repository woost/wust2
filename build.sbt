name := "wust"

// docker versions do not allow '+'
version in ThisBuild ~= (_.replace('+', '-'))
dynver in ThisBuild ~= (_.replace('+', '-'))

lazy val isCI = sys.env.get("CI").isDefined // set by travis, TODO: https://github.com/sbt/sbt/issues/3653

scalaVersion in ThisBuild := "2.12.4"
import Def.{setting => dep}

lazy val commonSettings = Seq(
  resolvers ++= (
    /* ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") :: */
    Resolver.jcenterRepo ::
    ("jitpack" at "https://jitpack.io") ::
    Nil
  ),

  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.4"),

  libraryDependencies ++=
    Deps.scribe.value ::
    Deps.scalatest.value % Test ::
    Deps.mockito.value % Test ::
    Nil,

  dependencyOverrides ++=
    Deps.circe.core.value ::
    Deps.circe.parser.value ::
    Deps.circe.generic.value ::
    Deps.cats.core.value ::
    Deps.akka.httpCore.value ::
    Nil,

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
      "-Xlint:-unused,_" ::
      "-Yno-adapted-args" ::
      "-Ywarn-unused:-imports" ::
        // "-Ywarn-dead-code" :: // does not work with js.native
      "-Ywarn-extra-implicit" ::
      "-Ywarn-infer-any" ::
      "-Ywarn-nullary-override" ::
      "-Ywarn-nullary-unit" ::
      Nil,
)

lazy val sourceMapSettings = Seq(
    // To enable source map support, all sub-project folders are symlinked to assets/project-root.
    // This folder is served via webpack devserver.
    scalacOptions += {
      val local = s"${(ThisBuild / baseDirectory).value.toURI}"
      val remote = s"/"
      s"-P:scalajs:mapSourceURI:$local->$remote"
    },
  )

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, database, backend, sdkJS, sdkJVM, webApp, idsJS, idsJVM, graphJS, graphJVM, utilJS, utilJVM, systemTest, nginx, dbMigration, slackApp, gitterApp, githubApp)
  .settings(
    publish := {},
    publishLocal := {},

    addCommandAlias("clean", "; root/clean; assets/clean"),

    addCommandAlias("dev", "; compile; webApp/fastOptJS::startWebpackDevServer; devwatch; devstop; backend/reStop"),
    addCommandAlias("devwatch", "~; backend/reStart; webApp/fastOptJS; webApp/copyFastOptJS"),
    addCommandAlias("devstop", "webApp/fastOptJS::stopWebpackDevServer"),

    addCommandAlias("devf", "; compile; backend/reStart; project webApp; fastOptJS::startWebpackDevServer; devfwatch; devstop; backend/reStop; project root"),
    addCommandAlias("devfwatch", "~; fastOptJS; copyFastOptJS"),

    addCommandAlias("testJS", "; utilJS/test; graphJS/test; sdkJS/test; apiJS/test; webApp/test"),
    addCommandAlias("testJSOpt", "; set scalaJSStage in Global := FullOptStage; testJS"),
    addCommandAlias("testJVM", "; utilJVM/test; graphJVM/test; sdkJVM/test; apiJVM/test; database/test; backend/test; slackApp/test; gitterApp/test; githubApp/test"),

    // Avoid watching files in root project
    // TODO: is there a simpler less error-prone way to write this?
    // watchSources := (watchSources in apiJS).value ++ (watchSources in database).value ++ (watchSources in webApp).value
    // watchSources := Seq(apiJS, apiJVM, database, backend, sdkJS, sdkJVM, webApp, graphJS, graphJVM, utilJS, utilJVM, systemTest, nginx, dbMigration, slackApp).flatMap(p => (watchSources in p).value)
    watchSources := (watchSources in apiJS).value ++ (watchSources in apiJVM).value ++ (watchSources in database).value ++ (watchSources in backend).value ++ (watchSources in sdkJS).value ++ (watchSources in sdkJVM).value ++ (watchSources in webApp).value ++ (watchSources in idsJS).value ++ (watchSources in idsJVM).value ++ (watchSources in graphJS).value ++ (watchSources in graphJVM).value ++ (watchSources in utilJS).value ++ (watchSources in utilJVM).value ++ (watchSources in systemTest).value ++ (watchSources in nginx).value ++ (watchSources in dbMigration).value ++ (watchSources in slackApp).value ++ (watchSources in gitterApp).value ++ (watchSources in githubApp).value
  )

lazy val util = crossProject
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++= (
      Deps.pureconfig.value ::
      Deps.sourcecode.value ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      Deps.scalarx.value ::
      Deps.outwatch.value ::
      Nil
    )
  )
lazy val utilJS = util.js
lazy val utilJVM = util.jvm

lazy val sdk = crossProject
  .dependsOn(api)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++= (
      Deps.covenant.ws.value ::
      Deps.covenant.http.value ::
      Deps.boopickle.value ::
      Deps.monix.value ::
      Nil
    )
  )

lazy val sdkJS = sdk.js
lazy val sdkJVM = sdk.jvm

lazy val ids = crossProject.crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .settings(
    libraryDependencies ++= (
      Deps.cuid.value ::
      Deps.scalaz.core.value ::
      Nil
    )
  )
lazy val idsJS = ids.js
lazy val idsJVM = ids.jvm

lazy val graph = crossProject.crossType(CrossType.Pure)
  .settings(commonSettings)
  .jsSettings(sourceMapSettings)
  .dependsOn(ids, util)
  .settings(
    libraryDependencies ++=
      Deps.javaTime.value ::
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
  .settings(commonSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(idsJVM, utilJVM)
  .settings(
    libraryDependencies ++=
      Deps.quill.value ::
      Nil
  // parallelExecution in IntegrationTest := false
  )

lazy val backend = project
  .settings(commonSettings)
  .dependsOn(apiJVM, database)
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
      Nil,

    javaOptions in reStart += "-Xmx50m"
  )

lazy val copyFastOptJS = TaskKey[Unit]("copyFastOptJS", "Copy javascript files to target directory")

lazy val webApp = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(sdkJS, utilJS)
  .settings(commonSettings, sourceMapSettings)
  .settings(
    libraryDependencies ++= (
      Deps.outwatch.value ::
      Deps.scalarx.value ::
      Deps.vectory.value ::
      Deps.d3v4.value ::
      Deps.monocle.value ::
      Deps.circe.core.value ::
      Deps.circe.generic.value ::
      Deps.circe.parser.value ::
      Nil
    ),

    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    requiresDOM := true, // still required by bundler: https://github.com/scalacenter/scalajs-bundler/issues/181
    scalaJSUseMainModuleInitializer := true,
    //TODO: scalaJSStage in Test := FullOptStage,

    // scalaJSOptimizerOptions in fastOptJS ~= { _.withDisableOptimizer(true) }, // disable optimizations for better debugging experience
    emitSourceMaps in fastOptJS := true, //TODO: scalaJSLinkerConfig instead of emitSOurceMaps, scalajsOptimizer,...
    emitSourceMaps in fullOptJS := false,

    version in webpack := "3.10.0",
    version in startWebpackDevServer := "2.9.6", // watchOptions is only fixed in this version. https://github.com/scalacenter/scalajs-bundler/issues/200
    useYarn := true, // instead of npm
    npmDependencies in Compile ++=
      "marked" -> "0.3.12" ::
      Nil,
    npmDevDependencies in Compile ++=
      "webpack-closure-compiler" -> "2.1.6" ::
      "zopfli-webpack-plugin" -> "0.1.0" ::
      "brotli-webpack-plugin" -> "0.5.0" ::
      Nil,
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.config.prod.js"),

    // Devserver and hot-reload configuration:
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.config.dev.js"),
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(), // https://scalacenter.github.io/scalajs-bundler/cookbook.html#performance
    webpackDevServerExtraArgs := Seq("--progress", "--color"),
    // when running the "dev" alias, after every fastOptJS compile all artifacts are copied into
    // a folder which is served and watched by the webpack devserver.
    // this is a workaround for: https://github.com/scalacenter/scalajs-bundler/issues/180
    copyFastOptJS := {
      val inDir = (crossTarget in (Compile, fastOptJS)).value
      val outDir = (crossTarget in (Compile, fastOptJS)).value / "fastopt"
      val files = Seq("webapp-fastopt-loader.js", "webapp-fastopt.js", "webapp-fastopt.js.map") map { p => (inDir / p, outDir / p) }
      IO.copy(files, overwrite = true, preserveLastModified = true, preserveExecutable = true)
    }
  )

lazy val slackApp = project
  .settings(commonSettings)
  .dependsOn(sdkJVM, apiJVM, utilJVM)
  .settings(
    libraryDependencies ++=
      Deps.slackClient.value ::
        Nil
  )

lazy val gitterApp = project
  .settings(commonSettings)
  .dependsOn(sdkJVM, apiJVM, utilJVM)
  .settings(
    libraryDependencies ++=
      Deps.gitterClient.value ::
      Nil
  )

lazy val githubApp = project
  .settings(commonSettings)
  .dependsOn(sdkJVM, apiJVM, utilJVM)
  .settings(
    libraryDependencies ++=
      Deps.github4s.value ::
      Deps.graphQl.value ::
      Deps.akka.httpCirce.value ::
      Nil
  )

lazy val assets = project
  .enablePlugins(SbtWeb, ScalaJSWeb, WebScalaJSBundlerPlugin)
  .settings(
    //TODO: https://github.com/jantimon/html-webpack-plugin for asset checksums
    //TODO: minify html
    resourceGenerators in Assets += Def.task {
      val file = (resourceManaged in Assets).value / "version.txt"
      IO.write(file, version.value)
      Seq(file)
    },
    unmanagedResourceDirectories in Assets ++= (
      baseDirectory.value / "public" ::
      baseDirectory.value / "prod" ::
      Nil
    ),
    scalaJSProjects := Seq(webApp),
    npmAssets ++= {
      // without dependsOn, the file list is generated before webpack does its thing.
      // Which would mean that generated files by webpack do not land in the pipeline.
      val assets =
        ((npmUpdate in Compile in webApp).dependsOn(webpack in fullOptJS in Compile in webApp).value ** "*.gz") +++
          ((npmUpdate in Compile in webApp).dependsOn(webpack in fullOptJS in Compile in webApp).value ** "*.br")
      val nodeModules = (npmUpdate in (webApp, Compile)).value
      assets.pair(Path.relativeTo(nodeModules))
    },
    pipelineStages in Assets := Seq(scalaJSPipeline)
  )

lazy val systemTest = project
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++=
      Deps.akka.http.value % IntegrationTest ::
      Deps.specs2.value % IntegrationTest ::
      Deps.selenium.value % IntegrationTest ::
      Nil,
    scalacOptions in Test ++= Seq("-Yrangepos") // specs2
  )

lazy val nginx = project
lazy val dbMigration = project
