package wust.sdk

import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling._
import monix.eval.Task
import monix.execution.Scheduler
import sloth._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object AkkaHttpRouteForTask {

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
