package wust.css

// write scalacss generated styles to file
object Main extends App {
  import java.io.{File, PrintWriter}

  val w = new PrintWriter(new File("webApp/src/css/scalacss.css"))
  w.write(StyleRendering.renderAll)
  w.close()
}
