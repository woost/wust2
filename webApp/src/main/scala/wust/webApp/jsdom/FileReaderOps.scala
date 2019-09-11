package wust.webApp.jsdom

import org.scalajs.dom

import scala.concurrent.{Future, Promise}

object FileReaderOps {

  def readAsText(file: dom.Blob): Future[String] = {
    val promise = Promise[String]
    val fileReader = new dom.FileReader()
    fileReader.onload = { e =>
      promise success fileReader.result.asInstanceOf[String]
    }
    fileReader.onerror = { e =>
      promise failure new Exception
    }
    fileReader.onabort = { e =>
      promise failure new Exception
    }

    fileReader.readAsText(file)
    promise.future
  }

  def readAsDataURL(file: dom.File): Future[String] = {
    val promise = Promise[String]
    val fileReader = new dom.FileReader()
    fileReader.onload = { e =>
      dom.console.log("ME", e)
      promise success fileReader.result.asInstanceOf[String]
    }
    fileReader.onerror = { e =>
      promise failure new Exception
    }
    fileReader.onabort = { e =>
      promise failure new Exception
    }

    fileReader.readAsDataURL(file)
    promise.future
  }
}
