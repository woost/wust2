package wust.backend

import wust.backend.auth.JWT

object TestDefaults {
  val jwt = new JWT("secret", 123456789)
}
