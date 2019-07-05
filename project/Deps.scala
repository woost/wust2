import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._
import sbt._

object Deps {
  import Def.{ setting => dep }

  val silencerVersion = "1.4.1"

  val acyclicDef = "com.lihaoyi" %% "acyclic" % "0.1.9"
  val acyclic = dep(acyclicDef % "provided")

  // testing
  val scalatest = dep("org.scalatest" %%% "scalatest" % "3.0.8")
  val selenium = dep("org.seleniumhq.selenium" % "selenium-java" % "3.3.1")
  val specs2 = dep("org.specs2" %% "specs2-core" % "4.5.1")
  val mockito = dep("org.mockito" % "mockito-core" % "2.28.2")

  // core libraries
  val cats = new {
    val core = dep("org.typelevel" %%% "cats-core" % "1.6.1")
    val kittens = dep("org.typelevel" %%% "kittens" % "1.2.1")
  }
  val akka = new {
    private val version = "2.5.23"
    private val httpVersion = "10.1.8"
    val http = dep("com.typesafe.akka" %% "akka-http" % httpVersion)
    val httpCore = dep("com.typesafe.akka" %% "akka-http-core" % httpVersion)
    val httpCirce = dep("de.heikoseeberger" %% "akka-http-circe" % "1.22.0")
    val httpPlay = dep("de.heikoseeberger" %% "akka-http-play-json" % "1.22.0")
    val httpCors = dep("ch.megard" %% "akka-http-cors" % "0.3.1")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
    val httpTestkit = dep("com.typesafe.akka" %% "akka-http-testkit" % httpVersion)
  }

  // serialization
  // val boopickle = dep("com.github.suzaku-io.boopickle" %%% "boopickle-shapeless" % "680e03c")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.1")
  val circe = new {
    private val version = "0.11.1"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val genericExtras = dep("io.circe" %%% "circe-generic-extras" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }

  // webApp
  val scalaJsDom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.6")
  val d3v4 = dep("com.github.fdietze" %% "scala-js-d3v4" % "e9ce7a9")
  // val d3v4 = dep("com.github.fdietze" %%% "scala-js-d3v4" % "master-SNAPSHOT")
  val fontawesome = dep("com.github.grburst" % "scala-js-fontawesome" % "d673579a18")
  val vectory = dep("com.github.fdietze" % "vectory" % "63430be")
  // val scalarx = dep("com.lihaoyi" %%% "scalarx" % "0.4.0")
  val scalarx = dep("com.github.fdietze.duality" %%% "scalarx" % "fc761c9")
  // val scalarx = dep("com.github.fdietze.duality" %%% "scalarx" % "94c6d80") // jitpack cannot handle the . in repo name scala.rx
  val outwatch = dep("com.github.outwatch" % "outwatch" % "9111ea5")
  // val outwatch = dep("io.github.outwatch" %%% "outwatch" % "0.11.1-SNAPSHOT")
  val bench = dep("com.github.fdietze.bench" %%% "bench" % "e66a721")
  // val bench = dep("com.github.fdietze" %%% "bench" % "master-SNAPSHOT")

  // utility
  val scribe = new {
    val perfolation = dep("com.github.fdietze.perfolation" %%% "perfolation" % "6854947")
    val core = dep("com.outr" %%% "scribe" % "2.6.0")
  }
  val pureconfig = dep("com.github.pureconfig" %% "pureconfig" % "0.9.2")
  val monocle = dep("com.github.julien-truffaut" %% "monocle-macro" % "1.5.1-cats")
  val monocleCore = dep("com.github.julien-truffaut" %% "monocle-core" % "1.5.1-cats")
  val sourcecode = dep("com.github.cornerman.sourcecode" %%% "sourcecode" % "998ee90c15")
  val cuid = dep("io.github.cornerman.scala-cuid" %%% "scala-cuid" % "9589781")
  val base58s = dep("io.github.fdietze.base58s" %%% "base58s" % "fbedca4")
  val monix = dep("io.monix" %%% "monix" % "3.0.0-RC2")
  val taggedTypes = dep("org.rudogma" %%% "supertagged" % "1.4")
  val colorado = dep("com.github.fdietze.colorado" %%% "colorado" % "8722023")
  val scalacss = dep("com.github.japgolly.scalacss" %%% "core" % "0.5.6")
  val kantanRegex = new {
    private val version = "0.4.0"
    val core = dep("com.nrinaudo" %%% "kantan.regex" % version)
    val generic = dep("com.nrinaudo" %%% "kantan.regex-generic" % version)
  }
  val kantanCSV = new {
    private val version = "0.4.0"
    val core = dep("com.nrinaudo" %%% "kantan.csv" % version)
    val generic = dep("com.nrinaudo" %%% "kantan.csv-generic" % version)
  }
  val flatland = dep("com.github.fdietze.flatland" %%% "flatland" % "2b169c4")
  val caseApp = dep("com.github.alexarchambault" %%% "case-app" % "2.0.0-M3")

  // graalvm
  val substrateVM = dep("com.oracle.substratevm" % "svm" % "1.0.0-rc8" % Provided) // make sure the version matches GraalVM version used to run native-image

  // rpc
  val covenant = new {
    private val version = "39b34ac"
    val core = dep("com.github.cornerman.covenant" %%% "covenant-core" % version)
    val ws = dep("com.github.cornerman.covenant" %%% "covenant-ws" % version)
    val http = dep("com.github.cornerman.covenant" %%% "covenant-http" % version)
  }

  // auth
  val hasher = dep("com.roundeights" %% "hasher" % "1.2.0")
  val jbcrypt = dep("org.mindrot" % "jbcrypt" % "0.4")
  val jwt = dep("com.pauldijou" %% "jwt-circe" % "2.1.0")
  val oAuthServer = dep("com.nulab-inc" %% "scala-oauth2-core" % "1.3.0")
  val oAuthAkkaProvider = dep("com.nulab-inc" %% "akka-http-oauth2-provider" % "1.3.0")
  val oAuthClient = dep("com.github.fdietze" % "akka-http-oauth2-client" % "cf77841")

  // database
  val quill = dep("io.getquill" %% "quill-async-postgres" % "3.2.2")

  // interfaces
  //val github4s = dep("com.47deg" %% "github4s" % "0.17.0") // only temporarly here
  val github4s = dep("io.github.GRBurst.github4s" %% "github4s" % "1d9681d") // master + comments + single issue
  val graphQl = dep("org.sangria-graphql" %% "sangria" % "1.4.2")
  val redis = dep("net.debasishg" %% "redisclient" % "3.8")
  val gitterSync = dep("com.github.amatkivskiy" % "gitter.sdk.sync" % "1.6.1")
  val gitterClient = dep("com.github.amatkivskiy" % "gitter.sdk.async" % "1.6.1")
  val slackClient = dep("com.github.GRBurst" % "slack-scala-client" % "65cd560") //b88f22e
  val javaMail = dep("com.sun.mail" % "javax.mail" % "1.6.2")
  val webPush = dep("nl.martijndwars" % "web-push" % "3.1.1")
  val awsSdk = new {
    //dep("software.amazon.awssdk" % "aws-sdk-java" % "2.1.3") // TODO: Does not work because of newer netty dependency than postgres-async => runtime error.
    private val version = "1.11.568"
    val s3 = dep("com.amazonaws" % "aws-java-sdk-s3" % version)
    val ses = dep("com.amazonaws" % "aws-java-sdk-ses" % version)
  }
  val javaEmojis = dep("com.github.GRBurst" % "emoji-java" % "24b956c")

  val webpackVersion = "4.34.0"
  val webpackDevServerVersion = "3.2.1"

  object npm {
    val defaultPassiveEvents = "default-passive-events" -> "1.0.10"
    val intersectionObserver = "intersection-observer" -> "0.7.0"
    val marked = "marked" -> "0.6.3"
    val markedSanitizer = "marked-sanitizer-github" -> "1.0.0"
    val highlight = "highlight.js" -> "9.15.8"
    val dateFns = "date-fns" -> "v2.0.0-alpha.27"
    val draggable = "@shopify/draggable" -> "1.0.0-beta.8"
    val fomanticUi = "fomantic-ui-css" -> "2.7.6"
    val emoji = "emoji-js" -> "3.4.1"
    val emojiDatasource = "emoji-datasource" -> "4.1.0"
    val emojiDatasourceTwitter = "emoji-datasource-twitter" -> "4.1.0"
    val hammerjs = "hammerjs" -> "2.0.8"
    val propagatingHammerjs = "propagating-hammerjs" -> "1.4.6"
    val immediate = "immediate" -> "3.2.3"
    val mobileDetect = "mobile-detect" -> "1.4.3"
    val jsSha256 = "js-sha256" -> "0.9.0"
    val clipboardjs = "clipboard" -> "2.0.4"
    val jqueryTablesort = "jquery-tablesort" -> "0.0.11"
    val juration = "juration" -> "0.1.0"
    val wdtEmojiBundle = "wdt-emoji-bundle" -> "git://github.com/fdietze/wdt-emoji-bundle.git#fcf05d9"
    val tribute = "tributejs" -> "3.7.1"

    val webpackDependencies =
      "webpack-closure-compiler" -> "git://github.com/roman01la/webpack-closure-compiler.git#3677e5e" :: //TODO: "closure-webpack-plugin" -> "1.0.1" :: https://github.com/webpack-contrib/closure-webpack-plugin/issues/47
        // "webpack-subresource-integrity" -> "1.1.0-rc.4" ::
        "html-webpack-plugin" -> "3.2.0" ::
        "html-webpack-include-assets-plugin" -> "1.0.7" ::
        "clean-webpack-plugin" -> "1.0.1" ::
        "compression-webpack-plugin" -> "2.0.0" ::
        "@gfx/zopfli" -> "1.0.11" :: // zopfli compiled to webassembly
        // "brotli-webpack-plugin" -> "1.1.0" ::
        "brotli-webpack-plugin" -> "git://github.com/GRBurst/brotli-webpack-plugin.git#c0a8fff" ::
        // "node-sass" -> "4.7.2" ::
        // "sass-loader" -> "6.0.7" ::
        "css-loader" -> "2.1.0" ::
        "style-loader" -> "0.23.1" ::
        "extract-text-webpack-plugin" -> "4.0.0-beta.0" ::
        "webpack-merge" -> "4.2.1" ::
        "copy-webpack-plugin" -> "5.0.0" ::
        "workbox-webpack-plugin" -> "4.3.1" ::
        "optimize-css-assets-webpack-plugin" -> "5.0.1" ::
        "cssnano" -> "4.1.10" ::
        Nil
  }

  object docker {
    val nginx = "nginx:1.13.12-alpine"
    val alpine = "alpine:3.10"
    val flyway = "boxfuse/flyway:5.2.1-alpine"
    val pgtap = "cornerman/docker-pgtap:4aa7be07511ffeac26ec588324606137258298d5"
  }
}
