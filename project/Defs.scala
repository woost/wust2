import sbt._

object Defs {
  val dockerVersionTags = settingKey[Seq[String]]("Version tags for docker images")
}
