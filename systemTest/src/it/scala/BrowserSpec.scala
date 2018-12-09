import org.specs2.mutable

class BrowserSpec extends mutable.Specification with WustReady {
  "run without errors" in new Browser {
    browser.getTitle mustEqual "Welcome - Woost"
    // wait for potential errors
    Thread.sleep(1000)
    browser.errors mustEqual Nil
  }
}
