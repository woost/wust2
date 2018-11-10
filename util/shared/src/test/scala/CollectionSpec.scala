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

  "leftPadTo" - {
    "same number" in {
      List(1,2).leftPadTo(2,0) mustEqual List(1,2)
    }
    "smaller number" in {
      List(1,2).leftPadTo(5,0) mustEqual List(0,0,0,1,2)
    }
    "bigger number" in {
      List(1,2,3,4).leftPadTo(2,0) mustEqual List(1,2,3,4)
    }

    "with string" in {
      "tenor".leftPadTo(6,'a') mustEqual "atenor"
    }
  }
}
