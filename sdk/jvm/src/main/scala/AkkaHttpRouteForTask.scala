package wust.sdk

import sloth._
import covenant.core.util.StopWatch
import covenant.core.api._
import covenant.http.api._

import akka.util.ByteString
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.unmarshalling._
import akka.http.scaladsl.model._
import akka.stream.Materializer
import monix.execution.Scheduler
import monix.eval.Task

import cats.data.EitherT
import cats.syntax.either._

import java.nio.ByteBuffer
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure}

object AkkaHttpRouteForTask {
   import covenant.core.util.LogHelper._

  def fromTaskRouter[PickleType : FromRequestUnmarshaller : ToResponseMarshaller](router: Router[PickleType, Task])(implicit scheduler: Scheduler): Route = {
    requestFunctionToRoute[PickleType](r => router(r).toEither.map(_.map(complete(_)).runToFuture))
  }

  private def requestFunctionToRoute[PickleType : FromRequestUnmarshaller : ToResponseMarshaller](router: Request[PickleType] => Either[ServerFailure, Future[Route]]): Route = {
    (path(Remaining) & post) { pathRest =>
      decodeRequest {
        val path = pathRest.split("/").toList
        entity(as[PickleType]) { entity =>
          router(Request(path, entity)) match {
            case Right(result) => onComplete(result) {
              case Success(r) => r
              case Failure(e) => complete(StatusCodes.InternalServerError -> e.toString)
            }
            case Left(err) => complete(StatusCodes.BadRequest -> err.toString)
          }
        }
      }
    }
  }
}
