package wust.util

case class AutoId(start: Int = 0, delta: Int = 1) {
  var localId = start - delta
  def apply() = { localId += delta; localId }
}
