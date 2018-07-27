import sbt._
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport._

object Deps {
  import Def.{setting => dep}

  val acyclicDef = "com.lihaoyi" %% "acyclic" % "0.1.7"
  val acyclic = dep(acyclicDef % "provided")

  // testing
  val scalatest = dep("org.scalatest" %%% "scalatest" % "3.0.5")
  val specs2 = dep("org.specs2" %% "specs2-core" % "4.2.0")
  val mockito = dep("org.mockito" % "mockito-core" % "2.18.3")
  val selenium = dep("org.seleniumhq.selenium" % "selenium-java" % "3.3.1")

  // core libraries
  val cats = new {
    val core = dep("org.typelevel" %%% "cats-core" % "1.1.0")
    val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0")
  }
  val akka = new {
    private val version = "2.5.13"
    private val httpVersion = "10.1.3"
    val http = dep("com.typesafe.akka" %% "akka-http" % httpVersion)
    val httpCore = dep("com.typesafe.akka" %% "akka-http-core" % httpVersion)
    val httpCirce = dep("de.heikoseeberger" %% "akka-http-circe" % "1.21.0")
    val httpPlay = dep("de.heikoseeberger" %% "akka-http-play-json" % "1.21.0")
    val httpCors = dep("ch.megard" %% "akka-http-cors" % "0.2.2")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
    val httpTestkit = dep("com.typesafe.akka" %% "akka-http-testkit" % httpVersion)
  }

  // serialization
  // val boopickle = dep("com.github.suzaku-io.boopickle" %%% "boopickle-shapeless" % "680e03c")
  val boopickle = dep("io.suzaku" %%% "boopickle" % "1.3.0")
  val circe = new {
    private val version = "0.9.3"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val genericExtras = dep("io.circe" %%% "circe-generic-extras" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }

  // webApp
  val scalaJsDom = dep("org.scala-js" %%% "scalajs-dom" % "0.9.6")
  val d3v4 = dep("com.github.fdietze" %% "scala-js-d3v4" % "08fc8de")
  val fontawesome = dep("com.github.fdietze" % "scala-js-fontawesome" % "b90c44d")
  val vectory = dep("com.github.fdietze" % "vectory" % "d0e70f4")
  val scalarx = dep("com.lihaoyi" %%% "scalarx" % "0.4.0")
  // val scalarx = dep("com.github.fdietze.duality" %%% "scalarx" % "94c6d80") // jitpack cannot handle the . in repo name scala.rx
  val outwatch = dep("com.github.cornerman" % "outwatch" % "cc784ea")

  // utility
  val scribe = dep("com.outr" %%% "scribe" % "2.5.0")
  val pureconfig = dep("com.github.pureconfig" %% "pureconfig" % "0.9.1")
  val monocle = dep("com.github.julien-truffaut" %% "monocle-macro" % "1.5.1-cats")
  val monocleCore = dep("com.github.julien-truffaut" %% "monocle-core" % "1.5.1-cats")
  val sourcecode = dep("com.lihaoyi" %%% "sourcecode" % "0.1.4")
  val cuid = dep("io.github.cornerman.scala-cuid" %%% "scala-cuid" % "9589781")
  val base58s = dep("io.github.fdietze.base58s" %%% "base58s" % "fbedca4")
  val monix = dep("io.monix" %%% "monix" % "3.0.0-RC1")
  val taggedTypes = dep("org.rudogma" %%% "supertagged" % "1.4")
  val colorado = dep("com.github.fdietze.colorado" %%% "colorado" % "8722023")
  val fastparse = dep("com.lihaoyi" %%% "fastparse" % "1.0.0")
  val scalacss = dep("com.github.japgolly.scalacss" %%% "core" % "0.5.3")

  // rpc
  val covenant = new {
    private val version = "e618443"
    val core = dep("com.github.cornerman.covenant" %%% "covenant-core" % version)
    val ws = dep("com.github.cornerman.covenant" %%% "covenant-ws" % version)
    val http = dep("com.github.cornerman.covenant" %%% "covenant-http" % version)
  }

  // auth
  val hasher = dep("com.roundeights" %% "hasher" % "1.2.0")
  val jbcrypt = dep("org.mindrot" % "jbcrypt" % "0.4")
  val jwt = dep("com.pauldijou" %% "jwt-circe" % "0.16.0")
  val oAuthServer = dep("com.nulab-inc" %% "scala-oauth2-core" % "1.3.0")
  val oAuthAkkaProvider = dep("com.nulab-inc" %% "akka-http-oauth2-provider" % "1.3.0")
  val oAuthClient = dep("com.github.GRBurst" % "akka-http-oauth2-client" % "260bf29")

  // database
  val quill = dep("io.getquill" %% "quill-async-postgres" % "2.5.4")

  // interfaces
  //val github4s = dep("com.47deg" %% "github4s" % "0.17.0") // only temporarly here
  val github4s = dep("io.github.GRBurst.github4s" %% "github4s" % "1d9681d") // master + comments + single issue
  val graphQl = dep("org.sangria-graphql" %% "sangria" % "1.4.1")
  val redis = dep("net.debasishg" %% "redisclient" % "3.7")
  val gitterSync = dep("com.github.amatkivskiy" % "gitter.sdk.sync" % "1.6.1")
  val gitterClient = dep("com.github.amatkivskiy" % "gitter.sdk.async" % "1.6.1")
//  val slackClient = dep("com.github.gilbertw1" %% "slack-scala-client" % "0.2.3")
  val slackClient = dep("com.github.GRBurst" %% "slack-scala-client" % "4209003")
  val javaMail = dep("com.sun.mail" % "javax.mail" % "1.6.1")
  val webPush = dep("nl.martijndwars" % "web-push" % "3.1.0")

  // NLP
  val stringmetric = dep("io.github.GRBurst.stringmetric" %% "stringmetric-core" % "91e2a03")
  //  val stringmetric = dep("com.rockymadden.stringmetric" %% "stringmetric-core" % "0.28.0-SNAPSHOT")

  val webpackVersion = "4.16.1"
  val webpackDevServerVersion = "3.1.4"

  object npm {
    val defaultPassiveEvents = "default-passive-events" -> "1.0.7"
    val marked = "marked" -> "0.3.12"
    val dateFns = "date-fns" -> "v2.0.0-alpha.10"
    val draggable = "@shopify/draggable" -> "1.0.0-beta.7"

    //TODO open PR at snabbdom for checking-parent-exists branch
    val snabbdom = "snabbdom" -> "git://github.com/cornerman/snabbdom.git#0.7.1"

    val webpackDependencies =
      "webpack-closure-compiler" -> "2.1.6" :: // TODO: use https://github.com/google/closure-compiler-js#webpack instead (after https://github.com/google/closure-compiler-js/issues/24 is fixed)
        "webpack-subresource-integrity" -> "1.1.0-rc.4" ::
        "html-webpack-plugin" -> "3.2.0" ::
        "html-webpack-include-assets-plugin" -> "1.0.4" ::
        "clean-webpack-plugin" -> "0.1.19" ::
        "zopfli-webpack-plugin" -> "0.1.0" ::
        "brotli-webpack-plugin" -> "0.5.0" ::
        "source-map-loader" -> "0.2.3" ::
        "node-sass" -> "4.7.2" ::
        "sass-loader" -> "6.0.7" ::
        "css-loader" -> "1.0.0" ::
        "style-loader" -> "0.21.0" ::
        "extract-text-webpack-plugin" -> "4.0.0-beta.0" ::
        "webpack-merge" -> "4.1.2" ::
        "copy-webpack-plugin" -> "4.5.1" ::
        "workbox-webpack-plugin" -> "3.3.1" ::
        Nil
  }

  object docker {
    val nginx = "nginx:1.13.12-alpine"
    val openjdk8 = "openjdk:8-jre-alpine"
    val flyway = "boxfuse/flyway:5.1.1-alpine"
  }
}
