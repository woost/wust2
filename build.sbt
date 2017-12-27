name := "wust"

enablePlugins(GitVersioning)
git.useGitDescribe := true
git.baseVersion := "0.1.0"
git.uncommittedSignifier := None // TODO: appends SNAPSHOT to version, but is always(!) active.

scalaVersion in ThisBuild := "2.12.4"

lazy val commonSettings = Seq(
  resolvers ++= (
    ("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots") ::
    Resolver.jcenterRepo ::
    Resolver.bintrayRepo("daxten", "maven") :: // for Daxten/autowire
    ("jitpack" at "https://jitpack.io") ::
    Nil
  ),

  addCompilerPlugin("org.scalameta" % "paradise" % "3.0.0-M10" cross CrossVersion.full),
  scalacOptions += "-Xplugin-require:macroparadise",
  scalacOptions in (Compile, console) := Seq(), // macroparadise plugin doesn't work in repl yet. https://github.com/scalameta/paradise/issues/10
  //exclude source files generated by scala meta: https://github.com/scalameta/paradise/issues/56
  coverageExcludedFiles := "<macro>",

  libraryDependencies ++= (
    "org.scalameta" %%% "scalameta" % "1.8.0" % "provided" ::
    "org.scalatest" %%% "scalatest" % scalaTestVersion % "test" ::
    "com.outr" %%% "scribe" % "1.4.5" ::
    Nil
  ),

  // do not run tests in assembly command
  test in assembly := {},

  // watch managed library dependencies (only works with scala 2.11 currently)
  // watchSources ++= (managedClasspath in Compile).map(_.files).value,

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
      "-Xlint" ::
      "-Yno-adapted-args" ::
//      "-Ywarn-dead-code" :: // does not work with js.native
      "-Ywarn-unused:-imports,-explicits,-implicits,_" ::
      "-Ywarn-extra-implicit" ::
      "-Ywarn-infer-any" ::
      "-Ywarn-nullary-override" ::
      "-Ywarn-nullary-unit" ::
      Nil

// To enable wartremover in all projects: https://github.com/wartremover/wartremover/issues/283#issuecomment-332927623
// wartremoverErrors ++= (
//   // http://www.wartremover.org/doc/warts.html
//   // Wart.Equals :: // TODO: rather have a compiler plugin to transform == to ===
//   // Wart.FinalCaseClass :: //TODO: rather have a compiler plugin to add "final"
//   // Wart.LeakingSealed ::
//   ContribWart.SomeApply :: //TODO: rather have a compiler plugin to transform Some(..) to Option(..) ?
//   // Wart.OldTime ::
//   // Wart.AsInstanceOf ::
//   Wart.Null ::
//   Nil
// ),
// wartremoverExcluded ++= (
//   //TODO: these files are ignored because scribe uses Some
//   baseDirectory.value / "src" / "main" / "scala" / "Dispatcher.scala" ::
//   baseDirectory.value / "src" / "main" / "scala" / "Server.scala" ::
//   Nil
// )
)

lazy val isCI = sys.env.get("CI").isDefined // set by travis

lazy val root = project.in(file("."))
  .aggregate(apiJS, apiJVM, database, backend, frameworkJS, frameworkJVM, frontend, graphJS, graphJVM, utilJS, utilJVM, systemTest, nginx, dbMigration, slackApp)
  .settings(
    publish := {},
    publishLocal := {},

    addCommandAlias("clean", "; root/clean; assets/clean"),

    addCommandAlias("dev", "; project root; compile; frontend/fastOptJS::startWebpackDevServer; devwatch; devstop; backend/reStop"),
    addCommandAlias("devwatch", "~; backend/reStart; frontend/fastOptJS; frontend/copyFastOptJS"),
    addCommandAlias("devstop", "frontend/fastOptJS::stopWebpackDevServer"),

    addCommandAlias("devf", "; project root; compile; backend/reStart; project frontend; fastOptJS::startWebpackDevServer; devfwatch; devstop; backend/reStop"),
    addCommandAlias("devfwatch", "~; fastOptJS; copyFastOptJS"),

    addCommandAlias("testJS", "; utilJS/test; graphJS/test; frameworkJS/test; apiJS/test; frontend/test"),
    addCommandAlias("testJSOpt", "; set scalaJSStage in Global := FullOptStage; testJS"),
    addCommandAlias("testJVM", "; utilJVM/test; graphJVM/test; frameworkJVM/test; apiJVM/test; database/test; backend/test; slackApp/test"),

    // avoids watching files in root project
    // watchSources := (watchSources in apiJS).value ++ (watchSources in database).value ++ (watchSources in frontend).value
    // watchSources := Seq(apiJS, apiJVM, database, backend, frameworkJS, frameworkJVM, frontend, graphJS, graphJVM, utilJS, utilJVM, systemTest, nginx, dbMigration, slackApp).flatMap(p => (watchSources in p).value)

    watchSources := (watchSources in apiJS).value ++ (watchSources in apiJVM).value ++ (watchSources in database).value ++ (watchSources in backend).value ++ (watchSources in frameworkJS).value ++ (watchSources in frameworkJVM).value ++ (watchSources in frontend).value ++ (watchSources in graphJS).value ++ (watchSources in graphJVM).value ++ (watchSources in utilJS).value ++ (watchSources in utilJVM).value ++ (watchSources in systemTest).value ++ (watchSources in nginx).value ++ (watchSources in dbMigration).value ++ (watchSources in slackApp).value
  )

val akkaVersion = "2.4.20"
val akkaHttpVersion = "10.0.10"
val circeVersion = "0.9.0-M2"
val specs2Version = "4.0.1"
val scalaTestVersion = "3.0.4"
val mockitoVersion = "2.11.0"
val scalazVersion = "7.2.13"
val boopickleVersion = "1.2.6"
val quillVersion = "2.3.1"
val outwatch = "io.github.mariusmuja" % "outwatch" % "3a29dd0"
val dualityVersion =  "9dd5e01649"
val catsVersion = "1.0.0-RC1"

lazy val util = crossProject
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      "com.github.pureconfig" %% "pureconfig" % "0.8.0" ::
      "com.lihaoyi" %%% "sourcecode" % "0.1.4" ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      "com.github.fdietze" % "duality" % dualityVersion ::
      outwatch ::
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
      "de.daxten" %%% "autowire" % "0.3.3" ::
      "io.suzaku" %%% "boopickle" % boopickleVersion ::
      Nil
    )
  )
  .jvmSettings(
    libraryDependencies ++= (
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion ::
      "com.typesafe.akka" %% "akka-actor" % akkaVersion ::
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test" ::
      Nil
    )
  )
  .jsSettings(
    libraryDependencies ++= (
      "org.scala-js" %%% "scalajs-dom" % "0.9.3" ::
      Nil
    )
  )

lazy val frameworkJS = framework.js
lazy val frameworkJVM = framework.jvm

lazy val ids = crossProject
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      "org.scalaz" %%% "scalaz-core" % scalazVersion ::
      "io.suzaku" %%% "boopickle" % boopickleVersion ::
      "io.circe" %%% "circe-core" % circeVersion ::
      "io.circe" %%% "circe-generic" % circeVersion ::
      "io.circe" %%% "circe-parser" % circeVersion ::
      Nil
    )
  )
lazy val idsJS = ids.js
lazy val idsJVM = ids.jvm

lazy val graph = crossProject
  .settings(commonSettings)
  .dependsOn(ids)
  .settings(
    libraryDependencies ++= (
      "com.github.cornerman" %% "derive" % "0.1.0-SNAPSHOT" ::
      Nil
    )
  )
  .dependsOn(util)
lazy val graphJS = graph.js
lazy val graphJVM = graph.jvm

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

lazy val database = project
  .settings(commonSettings)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .dependsOn(idsJVM, utilJVM)
  .settings(
    libraryDependencies ++=
      "io.getquill" %% "quill-async-postgres" % quillVersion ::
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test,it" ::
      Nil
  // parallelExecution in IntegrationTest := false
  )

lazy val backend = project
  .settings(commonSettings)
  .dependsOn(frameworkJVM, apiJVM, database)
  .configs(IntegrationTest)
  .settings(Defaults.itSettings)
  .settings(
    libraryDependencies ++=
      "com.roundeights" %% "hasher" % "1.2.0" ::
      "org.mindrot" % "jbcrypt" % "0.4" ::
      "com.pauldijou" %% "jwt-circe" % "0.14.1" ::
      "javax.mail" % "javax.mail-api" % "1.6.0" ::
      "com.sun.mail" % "javax.mail" % "1.6.0" ::
      "com.roundeights" %% "hasher" % "1.2.0" ::
      "org.mindrot" % "jbcrypt" % "0.4" ::
      "com.github.cornerman" %% "derive" % "0.1.0-SNAPSHOT" ::
      "com.github.cornerman" %% "delegert" % "0.1.0-SNAPSHOT" ::
      "org.mockito" % "mockito-core" % mockitoVersion % "test" ::
      "org.scalatest" %%% "scalatest" % scalaTestVersion % "test,it" ::
      Nil,
      javaOptions in reStart += "-Xmx50m"
  )

lazy val copyFastOptJS = TaskKey[Unit]("copyFastOptJS", "Copy javascript files to target directory")

lazy val frontend = project
  .enablePlugins(ScalaJSPlugin, ScalaJSBundlerPlugin)
  .dependsOn(frameworkJS, apiJS, utilJS)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= (
      outwatch ::
      "com.github.fdietze" % "duality" % dualityVersion ::
      "com.github.fdietze" % "vectory" % "3232833" ::
      "com.github.fdietze" %% "scala-js-d3v4" % "579b9df" :: 
      "com.github.julien-truffaut" %%  "monocle-macro" % "1.5.0-cats-M2" ::
      "com.github.cornerman" %% "derive" % "0.1.0-SNAPSHOT" ::
      "com.github.cornerman" %% "delegert" % "0.1.0-SNAPSHOT" ::
      Nil
    ),
    requiresDOM := true, // still required by bundler: https://gitter.im/scala-js/scala-js?at=59b55f12177fb9fe7ea2beff
    // jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv(), // runs scalajs tests with node + jsdom. Requires jsdom to be installed

    scalacOptions += "-P:scalajs:sjsDefinedByDefault",

    scalaJSUseMainModuleInitializer := true,
    // scalaJSOptimizerOptions in fastOptJS ~= { _.withDisableOptimizer(true) }, // disable optimizations for better debugging experience
    //TODO: scalaJSLinkerConfig instead of emitSOurceMaps, scalajsOptimizer,...
    emitSourceMaps in fastOptJS := true,
    // emitSourceMaps in fullOptJS := true,

    useYarn := true, // instead of npm
    npmDependencies in Compile ++= (
      "cuid" -> "1.3.8" ::
      Nil
    ),
    npmDevDependencies in Compile ++= (
      "compression-webpack-plugin" -> "0.3.1" ::
      "brotli-webpack-plugin" -> "0.2.0" ::
      "webpack-closure-compiler" -> "2.1.4" ::
      Nil
    ),

    //TODO: scalaJSStage in Test := FullOptStage,

    // artifactPath.in(Compile, fastOptJS) := ((crossTarget in (Compile, fastOptJS)).value / "fastopt" / ((moduleName in fastOptJS).value + "-fastopt.js")),

    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.config.dev.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.config.prod.js"),
    // https://scalacenter.github.io/scalajs-bundler/cookbook.html#performance
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(),
    webpackBundlingMode in fullOptJS := BundlingMode.Application,
    webpackDevServerPort := 12345,
    webpackDevServerExtraArgs := Seq("--progress", "--color"),

    // this is a workaround for: https://github.com/scalacenter/scalajs-bundler/issues/180
    copyFastOptJS := {
      val inDir = (crossTarget in (Compile, fastOptJS)).value
      val outDir = (crossTarget in (Compile, fastOptJS)).value / "fastopt"
      val files = Seq("frontend-fastopt-loader.js", "frontend-fastopt.js", "frontend-fastopt.js.map") map { p => (inDir / p, outDir / p) }
      IO.copy(files, overwrite = true, preserveLastModified = true, preserveExecutable = true)
    }
  )

lazy val slackApp = project
  .settings(commonSettings)
  .dependsOn(frameworkJVM, apiJVM, utilJVM)
  .settings(
    libraryDependencies ++=
      "cool.graph" % "cuid-java" % "0.1.1" ::
      "com.github.cornerman" %% "derive" % "0.1.0-SNAPSHOT" ::
      "com.github.gilbertw1" %% "slack-scala-client" % "0.2.2" ::
      Nil
  )

//TODO: https://github.com/jantimon/html-webpack-plugin for asset checksums

lazy val assets = project
  .enablePlugins(SbtWeb, ScalaJSWeb, WebScalaJSBundlerPlugin)
  .settings(
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
    scalaJSProjects := Seq(frontend),
    npmAssets ++= {
      // without dependsOn, the file list is generated before webpack does its thing.
      // Which would mean that generated files by webpack do not land in the pipeline.
      val assets =
        ((npmUpdate in Compile in frontend).dependsOn(webpack in fullOptJS in Compile in frontend).value ** "*.gz") +++
          ((npmUpdate in Compile in frontend).dependsOn(webpack in fullOptJS in Compile in frontend).value ** "*.br")
      val nodeModules = (npmUpdate in (frontend, Compile)).value
      assets.pair(Path.relativeTo(nodeModules))
    },
    pipelineStages in Assets := Seq(scalaJSPipeline)
  //TODO: minify html
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
      "org.seleniumhq.selenium" % "selenium-java" % "3.3.1" % "it" ::
      Nil,
    scalacOptions in Test ++= Seq("-Yrangepos") // specs2
  )

lazy val nginx = project
lazy val dbMigration = project
