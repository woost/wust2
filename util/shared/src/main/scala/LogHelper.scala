package wust.util

import shapeless._

object LogHelper {
  def requestString(path: List[String], arguments: Any, result: Any): String = {
    val paramLists = arguments match {
      case list: HList => list.runtimeList.collect { case list: HList => list.runtimeList }
      case other => List(List(other))
    }

    val pathString = path.mkString(".")
    val argString = paramLists.map(list => "(" + list.mkString(",") + ")").mkString
    s"$pathString$argString = $result"
  }
}
