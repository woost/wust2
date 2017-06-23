import org.specs2.mutable

class BrowserSpec extends mutable.Specification with WustReady {
  "run without errors" in new Browser {
    browser.getTitle mustEqual "Woost"
    // wait for potential errors
    Thread.sleep(500)
    browser.hasErrors mustEqual false
  }
}
