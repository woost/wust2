package wust.util

import org.scalatest._
import algorithm._

class CollectionSpec extends FreeSpec with MustMatchers {
  import collection._

  "RichCollection" - {
    "by" in {
      Seq(1,2).by(_.toString) mustEqual Map("1" -> 1, "2" -> 2)
    }

    "topologicalSortBy" in {
      Seq(1,2,3).topologicalSortBy(_ => Seq(3,2)) mustEqual Seq(1,3,2)
    }
  }

  "RichSet" - {
    "toggle" in {
      Set.empty[Int].toggle(1) mustEqual Set(1)
      Set(1, 2).toggle(1) mustEqual Set(2)
    }
  }

  "RichOption" - {
    "setOrToggle" in {
      Some(1).setOrToggle(1) mustEqual None
      Some(2).setOrToggle(1) mustEqual Some(1)
      (None: Option[Int]).setOrToggle(1) mustEqual Some(1)
    }
  }
}
