import scala.concurrent.Future

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.{Source, Sink}
import akka.stream.ActorMaterializer

import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

class AliveSpec(implicit ee: ExecutionEnv) extends Specification {
  private implicit val system = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  val connection = Http().outgoingConnection("localhost", 80)

  def get(path: String): Future[HttpResponse] = {
    val request = RequestBuilding.Get(path)
    Source.single(request).via(connection).runWith(Sink.head)
  }

  "should serve index.html on root" >> {
    get("/").map { response =>
      response.status.isSuccess must beTrue
      response.entity.toString must contain("Wust")
    } await
  }
}
