package wust.util

import com.typesafe.config.ConfigValue
import pureconfig._
import pureconfig.error.{KeyNotFound, ConvertFailure}
import pureconfig.syntax.PimpedConfigValue

object Config {
  implicit def hint[T] =
    ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  // if only keyNotFound errors for an optional value, then None
  implicit def optionConfigReader[T](implicit reader: ConfigReader[T]): ConfigReader[Option[T]] =
    ConfigReader.fromCursor[Option[T]] { cursor =>
        reader.from(cursor) match {
        case Right(config) => Right(Some(config))
        case Left(err) if err.toList.forall {
          case ConvertFailure(_ :KeyNotFound, _, _) => true
          case _ => false
        } => Right(None)
        case Left(err) => Left(err)
      }
    }
}
