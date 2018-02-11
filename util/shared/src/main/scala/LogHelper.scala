package wust.util

object LogHelper {
  def requestLogLine(path: List[String], arguments: List[List[Any]]): String = logLine(path, arguments, None)
  def requestLogLine(path: List[String], arguments: List[List[Any]], result: Any): String = logLine(path, arguments, Some(result))

  private def logLine(path: List[String], arguments: List[List[Any]], result: Option[Any]): String = {
    val pathString = path.mkString(".")
    val argString = arguments.map(list => "(" + list.mkString(",") + ")").mkString
    val request = pathString + argString
    result.fold(request)(result => s"$request = $result")
  }
}
