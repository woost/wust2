package wust.db

import io.getquill._
import wust.dbUtil.DbCommonCodecs

// Converters between scala classes and database entities
// Quill needs an implicit encoder/decoder for each occuring type.
// encoder/decoders for primitives and final case classes are provided by quill.
class DbCoreCodecs(override val ctx: PostgresAsyncContext[LowerCase]) extends DbCommonCodecs(ctx) {
}
