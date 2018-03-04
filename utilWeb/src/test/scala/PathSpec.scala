package wust.utilWeb.views

import org.scalatest._

class PathSpec extends FreeSpec with MustMatchers {
  "path unapply" - {
    "empty String" in {
      Path.unapply("") mustEqual None
    }

    "path" in {
      Path.unapply("graph") mustEqual Some(Path("graph", Map.empty))
    }

    "with option" in {
      Path.unapply("graph?foo=bar") mustEqual Some(Path("graph", Map("foo" -> "bar")))
    }

    "with options" in {
      Path.unapply("graph?foo=bar&hans=heinz") mustEqual Some(Path("graph", Map("foo" -> "bar", "hans" -> "heinz")))
    }

    "suffix ?" in {
      Path.unapply("graph?") mustEqual None
    }

    "suffix &" in {
      Path.unapply("graph?foo=bar&") mustEqual Some(Path("graph", Map("foo" -> "bar")))
    }
  }

  "path toString" - {
    "empty name" in {
      Path("", Map.empty).toString mustEqual ""
    }

    "empty options" in {
      Path("graph", Map.empty).toString mustEqual "graph"
    }

    "with option" in {
      Path("graph", Map("foo" -> "bar")).toString mustEqual "graph?foo=bar"
    }
  }
}

class PathOptionSpec extends FreeSpec with MustMatchers {
  "id list" - {
    "parse empty" in {
      PathOption.IdList.parse("") mustEqual Seq.empty
    }

    "parse single" in {
      PathOption.IdList.parse("1") mustEqual Seq(1)
    }

    "parse seq" in {
      PathOption.IdList.parse("1,2") mustEqual Seq(1, 2)
    }

    "parse invalid" in {
      PathOption.IdList.parse("hans,2") mustEqual Seq(2)
    }
  }

  "flag" - {
    "parse empty" in {
      PathOption.Flag.parse("") mustEqual false
    }

    "parse false" in {
      PathOption.Flag.parse("false") mustEqual false
    }

    "parse true" in {
      PathOption.Flag.parse("true") mustEqual true
    }

    "parse invalid" in {
      PathOption.Flag.parse("hans") mustEqual false
    }
  }
}
