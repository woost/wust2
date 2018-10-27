package wust.db

import io.getquill._
import io.circe.parser._
import io.circe.syntax._
import supertagged._
import wust.ids._
import wust.ids.serialize.Circe._
import wust.util._
import java.util.{Date, UUID}

import wust.dbUtil.DbCommonCodecs

// Converters between scala classes and database entities
// Quill needs an implicit encoder/decoder for each occuring type.
// encoder/decoders for primitives and case classes are provided by quill.
class DbCoreCodecs(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCommonCodecs(ctx) {
}
