package example

import org.scalatest._

class AppSpec extends FreeSpec with MustMatchers {
  "test" in {
    true mustEqual true
  }
}
