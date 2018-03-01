// build
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.22")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.10.0")
addSbtPlugin("ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.10.0")
addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.6")

// workflow
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
// addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.0.0")
// addSbtPlugin("org.wartremover" % "sbt-wartremover-contrib" % "1.0.0")

// deployment
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "2.0.0")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.2")
