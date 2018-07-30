package wust.sdk

import org.scalatest._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import shapeless.PolyDefns.~>
import wust.ids.UserId

class OAuthClientBasicSpec extends FreeSpec with EitherValues with Matchers {

  "load config" in {
    val config = Config.load("wust.test")
    config should be ('right)
  }

  "can instanziate client" in {
    val client = Config.load("wust.test").map(config => OAuthClient.apply(config.oauth, config.server))

    client should be ('right)
  }

  "generate url" in {
    val client = Config.load("wust.test").map(config => OAuthClient.apply(config.oauth, config.server))

    val url = client.map(c => c.authorizeUrl(UserId.fresh, Map("state" -> "de4a77dc-373b-4aad-a1f0-5c165060de31")).map(_.toString))

    url should be ('right)
    url shouldEqual Right(Some("http://localhost/wust/oauth/authorize?state=de4a77dc-373b-4aad-a1f0-5c165060de31&scopes=read:org,read:user,repo,write:discussion&redirect_uri=http://localhost:8080/oauth/auth&client_id=clientId&response_type=code"))

  }
}

class OAuthClientRoutingSpec extends WordSpec with EitherValues with Matchers with ScalatestRouteTest {

  private val client = Config.load("wust.test").map(config => OAuthClient.apply(config.oauth, config.server)).toOption.get
  private val tokenObserver = PublishSubject[AccessToken]
  private val testRoute = client.route(tokenObserver)

  "oauth ignore empty routing" in {
      Get() ~> testRoute ~> check {
      handled shouldBe false
    }
  }

    "oauth ignore route without paramaters" in {
      Get(client.oAuthConfig.authPath) ~> testRoute ~> check {
        handled shouldBe false
      }
    }

  "oauth handles route with paramaters correctly" in {
    val routePath = s"/${client.oAuthConfig.authPath}?code=blub&state=bla"
    Get(routePath) ~> testRoute ~> check {
      handled shouldBe true
    }
  }
}
