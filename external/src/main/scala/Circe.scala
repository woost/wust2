package wust.external

import io.circe.{Decoder, Encoder, Json}
import wust.ids.EpochMilli

object Circe {
  implicit val EpochMilliDecoder: Decoder[EpochMilli] = Decoder.decodeString.emap(str => EpochMilli.parse(str).toRight("Not a DateTime"))
  implicit val EpochMilliEncoder: Encoder[EpochMilli] = epochMilli => Json.fromString(epochMilli.isoDateAndTime)

}
