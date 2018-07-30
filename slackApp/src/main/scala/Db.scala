package wust.slack

import com.typesafe.config.{ Config => TConfig }
import io.getquill._

object Db {
  def apply(config: TConfig) = {
    new Db(new PostgresAsyncContext(LowerCase, config))
  }
}

class Db(val ctx: PostgresAsyncContext[LowerCase]) {
  import ctx._

}
