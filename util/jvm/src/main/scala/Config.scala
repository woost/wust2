package wust.util

import com.typesafe.config.ConfigValue
import pureconfig._
import pureconfig.error.KeyNotFound
import pureconfig.syntax.PimpedConfigValue

object Config {
  implicit def hint[T] =
    ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

  // if only keyNotFound errors for an optional value, then None
  implicit def optionConfigReader[T](implicit reader: Derivation[ConfigReader[T]]): ConfigReader[Option[T]] =
    new ConfigReader[Option[T]] {
      override def from(config: ConfigValue) = config.to[T] match {
        case Right(config) => Right(Some(config))
        case Left(err) if err.toList.forall {
          case _ :KeyNotFound => true
          case _ => false
        } => Right(None)
        case Left(err) => Left(err)
      }
    }
}
