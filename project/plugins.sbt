// build
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.1.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % "0.18.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.19")

// scalablytyped
resolvers += Resolver.bintrayRepo("oyvindberg", "converter")
addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta21")

// workflow
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
/* addSbtPlugin("org.wartremover" % "sbt-wartremover" % "2.4.2") */
/* addSbtPlugin("org.wartremover" % "sbt-wartremover-contrib" % "1.3.1") */

// deployment
addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "1.0.0")
addSbtPlugin("com.dwijnand" % "sbt-dynver" % "3.0.0")
addSbtPlugin("se.marcuslonnberg" % "sbt-docker" % "1.5.0")
addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.7")
/* addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1") */
/* addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.6") */

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.19")
