package wust.sdk

import wust.api._, wust.api.serialize.Boopickle._
import covenant.ws._
import chameleon.ext.boopickle._
import boopickle.Default._

import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext

private[sdk] trait NativeWustClient {
  def apply(location: String)(implicit ec: ExecutionContext) =
    new WustClientFactory(WsClient[ByteBuffer, ApiEvent, ApiError](location, WustClient.config))
}
