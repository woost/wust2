package wust.sdk

import java.util.UUID

import akka.http.scaladsl.testkit.ScalatestRouteTest
import monix.reactive.subjects.PublishSubject
import org.scalatest._
import org.scalatest.matchers._
import org.scalatest.freespec.AnyFreeSpec
import wust.api.{AuthUser, Authentication}
import wust.ids.UserId

class OAuthClientBasicSpec extends AnyFreeSpec with EitherValues with must.Matchers {

  def getClient(path: String = "wust.test") = {
    Config.load(path).map(config => OAuthClient.apply(config.oAuth, config.appServer, config.wustServer))
  }

  "load config" in {
    val config = Config.load("wust.test")
    config must be ('right)
  }

  "can instanziate client" in {
    val client = getClient()

    client must be ('right)
  }

  "generate url" in {
    val client = getClient()

    val randomState = UUID.randomUUID().toString
    val url = client.map(c => c.authorizeUrlWithState(Authentication.Verified(AuthUser.Real(UserId.fresh, UserId.fresh.toBase58, 0, None), 0, Authentication.Token("token")), List("read:org", "read:user", "repo" , "write:discussion"), randomState).map(_.toString))

    url must be ('right)
    url mustEqual Right(Some(s"http://localhost/wust/oauth/authorize?state=$randomState&scope=read:org,read:user,repo,write:discussion&redirect_uri=http://localhost:8080/oauth/auth&client_id=clientId&response_type=code"))

  }

  "generate url with defaults" in {
    val client = getClient("wust.test2")

    val randomState = UUID.randomUUID().toString
    val url = client.map(c => c.authorizeUrlWithState(Authentication.Verified(AuthUser.Real(UserId.fresh, UserId.fresh.toBase58, 0, None), 0, Authentication.Token("token")), List("read:org", "read:user", "repo" , "write:discussion"), randomState).map(_.toString))

    url must be ('right)
    url mustEqual Right(Some(s"http://localhost/oauth/authorize?state=$randomState&scope=read:org,read:user,repo,write:discussion&redirect_uri=http://localhost:8080/oauth/auth&client_id=clientId&response_type=code"))

  }
}

// class OAuthClientRoutingSpec extends AnyFreeSpec with EitherValues with must.Matchers with ScalatestRouteTest {

//   private val client = Config.load("wust.test").map(config => OAuthClient.apply(config.oAuth, config.appServer, config.wustServer)).toOption.get
//   private val tokenObserver = PublishSubject[AuthenticationData]
//   private val testRoute = client.route(tokenObserver)

//   "oauth ignore empty routing" in {
//       Get() ~> testRoute ~> check {
//       handled mustBe false
//     }
//   }

//     "oauth ignore route without paramaters" in {
//       Get(client.oAuthConfig.authPath.getOrElse("oauth/auth")) ~> testRoute ~> check {
//         handled mustBe false
//       }
//     }

//   "oauth handles route with paramaters correctly" in {
//     val routePath = s"/${client.oAuthConfig.authPath.getOrElse("oauth/auth")}?code=blub&state=bla"
//     Get(routePath) ~> testRoute ~> check {
//       handled mustBe true
//     }
//   }
// }
