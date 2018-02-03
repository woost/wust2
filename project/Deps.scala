import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt._

object Deps {
  import Def.{setting => dep}

  // testing
  val scalatest = dep("org.scalatest" %%% "scalatest" % "3.0.4")
  val specs2 = dep("org.specs2" %% "specs2-core" % "4.0.2")
  val mockito = dep("org.mockito" % "mockito-core" % "2.11.0")
  val selenium = dep("org.seleniumhq.selenium" % "selenium-java" % "3.3.1")

  // core libraries
  val cats = new {
    val core = dep("org.typelevel" %%% "cats-core" % "1.0.1")
    val kittens = dep("org.typelevel" %%% "kittens" % "1.0.0-RC2")
  }
  val scalaz = new {
    val core = dep("org.scalaz" %%% "scalaz-core" % "7.2.13")
  }
  val akka = new {
    private val version = "2.5.8"
    private val httpVersion = "10.1.0-RC1"
    val http = dep("com.typesafe.akka" %% "akka-http" % httpVersion)
    val httpCore = dep("com.typesafe.akka" %% "akka-http-core" % httpVersion)
    val stream = dep("com.typesafe.akka" %% "akka-stream" % version)
    val actor = dep("com.typesafe.akka" %% "akka-actor" % version)
    val testkit = dep("com.typesafe.akka" %% "akka-testkit" % version)
  }

  // serialization
  // val boopickle = dep("io.suzaku" %%% "boopickle" % "1.2.6")
  val boopickle = dep("com.github.suzaku-io.boopickle" %%% "boopickle" % "084b0c8")
  val circe = new {
    private val version = "0.9.0-M3"
    val core = dep("io.circe" %%% "circe-core" % version)
    val generic = dep("io.circe" %%% "circe-generic" % version)
    val parser = dep("io.circe" %%% "circe-parser" % version)
    val shapes = dep("io.circe" %%% "circe-shapes" % version)
  }

  // macro/meta
  val scalaReflect = dep("org.scala-lang" % "scala-reflect")
  val scalameta = dep("org.scalameta" %%% "scalameta" % "1.8.0")

  // frontend
  val d3v4 = dep("com.github.fdietze" %% "scala-js-d3v4" % "48694c6")
  val vectory = dep("com.github.fdietze" % "vectory" % "3232833")
  val scalarx = dep("com.github.fdietze" % "duality" % "9dd5e01649")
  val outwatch = dep("io.github.outwatch" % "outwatch" % "6dd72a31cb4")

  // utility
  val scribe = dep("com.outr" %%% "scribe" % "1.4.5")
  val pureconfig = dep("com.github.pureconfig" %% "pureconfig" % "0.8.0")
  val monocle = dep("com.github.julien-truffaut" %%  "monocle-macro" % "1.5.0-cats-M2")
  val sourcecode = dep("com.lihaoyi" %%% "sourcecode" % "0.1.4")
  val derive = dep("io.github.cornerman" % "derive" % "04166c6")
  val cuid = dep("io.github.cornerman" % "scala-cuid" % "4ba036a")
  val javaTime = dep("io.github.cquiroz" %%% "scala-java-time" % "2.0.0-M12")

  // rpc
  val mycelium = dep("com.github.cornerman" % "mycelium" % "4ddf314")
  val sloth = dep("io.github.cornerman" % "sloth" % "0fd028f")

  // auth
  val hasher = dep("com.roundeights" %% "hasher" % "1.2.0")
  val jbcrypt = dep("org.mindrot" % "jbcrypt" % "0.4")
  val jwt = dep("com.pauldijou" %% "jwt-circe" % "0.14.1")

  // database
  val quill = dep("io.getquill" %% "quill-async-postgres" % "2.3.1")

  // interfaces
  //val github4s = dep("com.47deg" %% "github4s" % "0.17.0") // only temporarly here
  val github4s = dep("io.github.GRBurst.github4s" %% "github4s" % "1d9681d") // master + comments + single issue
  val gitterSync = dep("com.github.amatkivskiy" % "gitter.sdk.sync" % "1.6.1")
  val gitterClient = dep("com.github.amatkivskiy" % "gitter.sdk.async" % "1.6.1")
  val slackClient = dep("com.github.gilbertw1" %% "slack-scala-client" % "0.2.2")
  val javaMail = dep("com.sun.mail" % "javax.mail" % "1.6.0")

  // NLP
  val stringmetric = dep("io.github.GRBurst.stringmetric" %% "stringmetric-core" % "91e2a03")
  //  val stringmetric = dep("com.rockymadden.stringmetric" %% "stringmetric-core" % "0.28.0-SNAPSHOT")
}
