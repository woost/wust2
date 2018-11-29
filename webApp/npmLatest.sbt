import scala.Console._
import scala.sys.process._

val npmLatest = taskKey[Unit]("Get latest npm package versions")
npmLatest := {
  val logger = streams.value.log
  val projsDeps =
    Def.task {
      (projectID.value.name, (npmDependencies in Compile).??(Nil).value)
    }.all(ScopeFilter(inAnyProject)).value

  def pad(s: String, len: Int): String = s.padTo(len, ' ')

  val maxProjLen = projsDeps.map(_._1.length).max
  val maxPkgLen = projsDeps.flatMap(_._2.map(_._1.length)).max
  val maxVerLen = projsDeps.flatMap(_._2.map(_._2.length)).max
  for ((proj, deps) <- projsDeps) {
    val latestVersions =
      for ((pkg, ver) <- deps)
        yield (pkg, ver, s"npm view $pkg dist-tags.latest".!!.trim)
    val updates = latestVersions.filter(x => x._2 != x._3)
    if (updates.nonEmpty) {
      logger.info(s"$BOLD${pad(proj, maxProjLen)} $RESET${updates.length} new npm package versions")
      for ((pkg, cur, latest) <- updates)
        logger.info(s"  $BOLD${pad(pkg, maxPkgLen)}$RESET ${pad(cur, maxVerLen)} -> $latest")
    }
  }
}
