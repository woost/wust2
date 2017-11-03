package wust.frontend

trait LocalStorageMock {
  import scala.collection.mutable
  import scala.scalajs.js

  if(js.isUndefined(js.Dynamic.global.localStorage))
    js.Dynamic.global.updateDynamic("localStorage")(new js.Object {
      val map = new mutable.HashMap[String,String]

      def getItem(key: String) = map.getOrElse(key, null)
      def setItem(key: String, value: String) = { map += key -> value; js.undefined }
    })
}
