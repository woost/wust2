package wust.cli

import caseapp.core
import caseapp.core.argparser.{ArgParser, SimpleArgParser}
import supertagged.{Tagged, TaggedType}
import wust.ids._

import scala.util.Try

object CustomParsers {
  implicit def taggedParser[T, U](implicit f: ArgParser[T]): ArgParser[Tagged[T, U]] = f.asInstanceOf[ArgParser[Tagged[T, U]]]
  implicit def overTaggedParser[R, T <: TaggedType[R], U](implicit f: ArgParser[T#Type]): ArgParser[Tagged[T#Type, U]] = f.asInstanceOf[ArgParser[Tagged[T#Type, U]]]

  implicit val cuidParser: ArgParser[Cuid] = SimpleArgParser.from[Cuid]("Cuid") { str =>
    Try(Cuid.fromBase58(str)).toOption.toRight(core.Error.MalformedValue("Cuid", s"Value '$str' is not a valid cuid"))
  }

  implicit val parser: ArgParser[NodeRole] = SimpleArgParser.from[NodeRole]("NodeRole") {
    case "message" => Right(NodeRole.Message)
    case "task" => Right(NodeRole.Task)
    case "project" => Right(NodeRole.Project)
    case s => Left(core.Error.MalformedValue("NodeRole", s"Found '$s', expected one of: message, task, project"))
  }
}
