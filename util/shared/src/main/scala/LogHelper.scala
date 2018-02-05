package wust.util

object LogHelper {
  def requestLogLine(path: List[String], arguments: List[List[Any]], result: Any): String = {
    val pathString = path.mkString(".")
    val argString = arguments.map(list => "(" + list.mkString(",") + ")").mkString
    s"$pathString$argString = $result"
  }
}
