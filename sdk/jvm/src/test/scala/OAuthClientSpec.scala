package wust.sdk

import java.util.UUID

import org.scalatest._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server._
import com.github.dakatsuka.akka.http.oauth2.client.AccessToken
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import shapeless.PolyDefns.~>
import wust.api.{AuthUser, Authentication}
import wust.ids.{NodeId, UserId}

class OAuthClientBasicSpec extends FreeSpec with EitherValues with Matchers {

  def getClient(path: String = "wust.test") = {
    Config.load(path).map(config => OAuthClient.apply(config.oAuth, config.appServer, config.wustServer))
  }

  "load config" in {
    val config = Config.load("wust.test")
    config should be ('right)
  }

  "can instanziate client" in {
    val client = getClient()

    client should be ('right)
  }

  "generate url" in {
    val client = getClient()

    val randomState = UUID.randomUUID().toString
    val url = client.map(c => c.authorizeUrlWithState(Authentication.Verified(AuthUser.Real(UserId.fresh, UserId.fresh.toBase58, 0), 0, "token"), List("read:org", "read:user", "repo" , "write:discussion"), randomState).map(_.toString))

    url should be ('right)
    url shouldEqual Right(Some(s"http://localhost/wust/oauth/authorize?state=$randomState&scope=read:org,read:user,repo,write:discussion&redirect_uri=http://localhost:8080/oauth/auth&client_id=clientId&response_type=code"))

  }

  "generate url with defaults" in {
    val client = getClient("wust.test2")

    val randomState = UUID.randomUUID().toString
    val url = client.map(c => c.authorizeUrlWithState(Authentication.Verified(AuthUser.Real(UserId.fresh, UserId.fresh.toBase58, 0), 0, "token"), List("read:org", "read:user", "repo" , "write:discussion"), randomState).map(_.toString))

    url should be ('right)
    url shouldEqual Right(Some(s"http://localhost/oauth/authorize?state=$randomState&scope=read:org,read:user,repo,write:discussion&redirect_uri=http://localhost:8080/oauth/auth&client_id=clientId&response_type=code"))

  }
}

class OAuthClientRoutingSpec extends WordSpec with EitherValues with Matchers with ScalatestRouteTest {

  private val client = Config.load("wust.test").map(config => OAuthClient.apply(config.oAuth, config.appServer, config.wustServer)).toOption.get
  private val tokenObserver = PublishSubject[AuthenticationData]
  private val testRoute = client.route(tokenObserver)

  "oauth ignore empty routing" in {
      Get() ~> testRoute ~> check {
      handled shouldBe false
    }
  }

    "oauth ignore route without paramaters" in {
      Get(client.oAuthConfig.authPath.getOrElse("oauth/auth")) ~> testRoute ~> check {
        handled shouldBe false
      }
    }

  "oauth handles route with paramaters correctly" in {
    val routePath = s"/${client.oAuthConfig.authPath.getOrElse("oauth/auth")}?code=blub&state=bla"
    Get(routePath) ~> testRoute ~> check {
      handled shouldBe true
    }
  }
}
