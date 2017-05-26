// build
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.16")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.6.0")
addSbtPlugin("ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.6.0")
addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.3")
// addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC3") // better dependency fetcher

// workflow
addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("com.lihaoyi" % "workbench" % "0.3.0")
// addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.0.0")
// addSbtPlugin("org.wartremover" % "sbt-wartremover-contrib" % "1.0.0")

// deployment
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.8.5")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.1")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.4")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.1.0")
