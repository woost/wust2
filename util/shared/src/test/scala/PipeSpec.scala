package wust.util

import org.scalatest._
import algorithm._

class PipeSpec extends FreeSpec with MustMatchers {
  import collection._

  "|>" in {
    (5 |> (_ + 1)) mustEqual 6
  }

  "||>" in {
    var done = false
    (5 ||> (x => done = x > 0)) mustEqual 5
    done mustEqual true
  }
}
