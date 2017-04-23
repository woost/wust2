package wust.util

import org.scalatest._

class CollectionSpec extends FreeSpec with MustMatchers {
  import collection._

  "RichCollection" - {
    "by" in {
      Seq(1, 2).by(_.toString) mustEqual Map("1" -> 1, "2" -> 2)
    }

    "distinctBy" in {
      case class A(x: Int, y: Int)
      Seq(A(1, 2), A(1, 3), A(2, 3)).distinctBy(_.x) mustEqual Seq(A(1, 2), A(2, 3))
      Seq(A(1, 2), A(1, 3), A(2, 3)).distinctBy(_.y) mustEqual Seq(A(1, 2), A(1, 3))
      Seq(A(1, 2), A(1, 3), A(2, 3)).distinctBy(identity) mustEqual Seq(A(1, 2), A(1, 3), A(2, 3)).distinct
    }

    "topologicalSortBy" in {
      Seq(1, 2, 3).topologicalSortBy(_ => Seq(3, 2)) mustEqual Seq(1, 3, 2)
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
      Option(1).setOrToggle(1) mustEqual None
      Option(2).setOrToggle(1) mustEqual Option(1)
      (None: Option[Int]).setOrToggle(1) mustEqual Option(1)
    }
  }
}
