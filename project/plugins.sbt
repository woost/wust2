// resolvers needed in travis, otherwise "module not found: com.typesafe#jse_2.10;1.0.0"
resolvers += Resolver.typesafeRepo("releases")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "0.6.14")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.5.0")
addSbtPlugin("ch.epfl.scala" % "sbt-web-scalajs-bundler" % "0.5.0")
addSbtPlugin("com.vmunier" % "sbt-web-scalajs" % "1.0.3")
addSbtPlugin("com.typesafe.sbt" % "sbt-gzip" % "1.0.0")
addSbtPlugin("org.danielnixon" % "sbt-uglify" % "1.0.7") // https://github.com/danielnixon/sbt-uglify

addSbtPlugin("io.spray" % "sbt-revolver" % "0.8.0")
addSbtPlugin("com.lihaoyi" % "workbench" % "0.3.0")

addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.4.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")
