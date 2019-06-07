package wust.cli

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling._
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling._
import akka.stream.ActorMaterializer
import cats.implicits._
import sloth._

import scala.concurrent.Future

//TODO fix in covenant that HttpClient cannot handle headers. already implemented on observable-next branch...
object HttpClientWithHeaders {
  def apply[PickleType](
    baseUri: String,
    logger: LogHandler[Future],
    headers: => List[(String, String)]
  )(implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    unmarshaller: FromEntityUnmarshaller[PickleType],
    marshaller: ToEntityMarshaller[PickleType]): Client[PickleType, Future, ClientException] = {
    import system.dispatcher

    val transport = new RequestTransport[PickleType, Future] {
      private val sender = sendRequest[PickleType, Exception](baseUri, (r,c) => new Exception(s"Http request failed $r: $c"), headers) _
      def apply(request: Request[PickleType]): Future[PickleType] = {
        sender(request).flatMap {
          case Right(res) => Future.successful(res)
          case Left(err) => Future.failed(err)
        }
      }
    }

    Client[PickleType, Future, ClientException](transport, logger)
  }

  private def sendRequest[PickleType, ErrorType](
    baseUri: String,
    failedRequestError: (String, StatusCode) => ErrorType,
    headers: => List[(String, String)]
  )(request: Request[PickleType])(implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    unmarshaller: FromEntityUnmarshaller[PickleType],
    marshaller: ToEntityMarshaller[PickleType]) = {
    import system.dispatcher

    val uri = (baseUri :: request.path).mkString("/")
    val entity = Marshal(request.payload).to[MessageEntity]
    val httpHeaders = headers.flatMap { case (key, value) =>
      HttpHeader.parse(key, value) match {
        case ParsingResult.Error(error) =>
          scribe.warn(s"Ignoring invalid header '$key': $error")
          None
        case ParsingResult.Ok(header, _) => Some(header)
      }
    }

    entity.flatMap { entity =>
      Http()
        .singleRequest(HttpRequest(method = HttpMethods.POST, uri = uri, headers = httpHeaders, entity = entity))
        .flatMap { response =>
          response.status match {
            case StatusCodes.OK =>
              Unmarshal(response.entity).to[PickleType].map(Right(_))
            case code =>
              response.discardEntityBytes()
              Future.successful(Left(failedRequestError(uri, code)))
          }
        }
    }
  }
}
