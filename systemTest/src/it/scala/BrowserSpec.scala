import org.specs2.mutable

class BrowserSpec extends mutable.Specification with WustReady {
  "run without errors" in new Browser {
    browser.getTitle must contain("Woost")
    // wait for potential errors
    Thread.sleep(1000)
    browser.errors mustEqual Nil
  }
}
