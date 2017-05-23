import wust.util.callLog
import org.scalatest._
import scala.concurrent.{ExecutionContext, Future}

@callLog(println)
class Ding {
  def no = 1
  def one(i: Int) = i + no
  def two(i: Int)(j: Int) = i + j
  def future(i: Int)(implicit ec: ExecutionContext): Future[Int] = Future.successful(one(i))
}

class CallLogSpec extends AsyncFreeSpec with MustMatchers {
  "works" in {
    val ding = new Ding
    ding.no mustEqual 1
    ding.one(2) mustEqual 3
    ding.two(1)(3) mustEqual 4
    ding.future(2).map(_ mustEqual 3)
  }
}
