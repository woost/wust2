import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Deps {
  import Def.{setting => dep}

  // testing
  val scalatest = dep("org.scalatest" %%% "scalatest" % "3.0.5")
  val specs2 = dep("org.specs2" %% "specs2-core" % "4.0.3")
  val mockito = dep("org.mockito" % "mockito-core" % "2.15.0")
  val selenium = dep("org.seleniumhq.selenium" % "selenium-java" % "3.3.1")

  // core libraries
  val cats = new {
    val core = dep("org.typelevel" %%% "cats-core" % "1.0.1")
    val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0-RC2")
  }
  val akka = new {
    private val version = "2.5.10"
    private val httpVersion = "10.1.0-RC1"
    val http = dep("com.typesafe.akka" %% "akka-http" % httpVersion)
    val httpCore = dep("com.typesafe.akka" %% "akka-http-core" % httpVersion)
    val httpCirce = dep("de.heikoseeberger" %% "akka-http-circe" % "1.19.0")
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }

  // serialization
  // val boopickle = dep("io.suzaku" %%% "boopickle" % "1.2.6")
  val boopickle = dep("com.github.fdietze.boopickle" %%% "boopickle" % "e8f9331")
  val circe = new {
    private val version = "0.9.1"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }

  // webApp
  val d3v4 = dep("com.github.fdietze" %% "scala-js-d3v4" % "08fc8de")
  val vectory = dep("com.github.fdietze" % "vectory" % "d0e70f4")
  val scalarx = dep("com.github.fdietze.duality" %%% "scalarx" % "27ce1da")
  val outwatch = dep("io.github.outwatch" % "outwatch" % "6dd72a31cb4")

  // utility
  val scribe = dep("com.outr" %%% "scribe" % "2.2.0")
  val pureconfig = dep("com.github.pureconfig" %% "pureconfig" % "0.9.0")
  val monocle = dep("com.github.julien-truffaut" %%  "monocle-macro" % "1.5.0-cats")
  val sourcecode = dep("com.lihaoyi" %%% "sourcecode" % "0.1.4")
  val cuid = dep("io.github.cornerman.scala-cuid" %%% "scala-cuid" % "9589781")
  val monix = dep("io.monix" %%% "monix" % "3.0.0-M3")
  val taggedTypes = dep("io.treev" %%% "tagged-types" % "1.4")
  val colorado = dep("com.github.fdietze.colorado" %%% "colorado" % "b490fdf")

  // rpc
  val covenant = new {
    private val version = "34f726d"
    val core = dep("com.github.cornerman.covenant" %%% "covenant-core" % version)
    val ws = dep("com.github.cornerman.covenant" %%% "covenant-ws" % version)
    val http = dep("com.github.cornerman.covenant" %%% "covenant-http" % version)
  }

  // auth
  val hasher = dep("com.roundeights" %% "hasher" % "1.2.0")
  val jbcrypt = dep("org.mindrot" % "jbcrypt" % "0.4")
  val jwt = dep("com.pauldijou" %% "jwt-circe" % "0.14.1")

  // database
  val quill = dep("io.getquill" %% "quill-async-postgres" % "2.3.1")

  // interfaces
  //val github4s = dep("com.47deg" %% "github4s" % "0.17.0") // only temporarly here
  val github4s = dep("io.github.GRBurst.github4s" %% "github4s" % "1d9681d") // master + comments + single issue
  val graphQl = dep("org.sangria-graphql" %% "sangria" % "1.3.3")
  val redis = dep("net.debasishg" %% "redisclient" % "3.5")
  val gitterSync = dep("com.github.amatkivskiy" % "gitter.sdk.sync" % "1.6.1")
  val gitterClient = dep("com.github.amatkivskiy" % "gitter.sdk.async" % "1.6.1")
  val slackClient = dep("com.github.gilbertw1" %% "slack-scala-client" % "0.2.2")
  val javaMail = dep("com.sun.mail" % "javax.mail" % "1.6.1")
  val webPush = dep("nl.martijndwars" % "web-push" % "3.1.0")

  // NLP
  val stringmetric = dep("io.github.GRBurst.stringmetric" %% "stringmetric-core" % "91e2a03")
  //  val stringmetric = dep("com.rockymadden.stringmetric" %% "stringmetric-core" % "0.28.0-SNAPSHOT")
}
