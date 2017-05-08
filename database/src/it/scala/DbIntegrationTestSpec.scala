package wust.dbSpec

import org.scalatest._

import scala.util.{ Failure, Success }
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }
import io.getquill._
import wust.Db
import java.util.UUID.randomUUID
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }

import wust.ids._

object DbIntegrationTestSpec {
  val defaultDbConfig = ConfigFactory.load().getConfig("db")
  val integrationTestDbConfig = ConfigFactory.load().getConfig("integrationTestDb")
}

trait DbIntegrationTestSpec extends fixture.AsyncFreeSpec with BeforeAndAfterAll {
  import DbIntegrationTestSpec._
  val dbCreatorCtx = new PostgresJdbcContext[LowerCase](integrationTestDbConfig)

  override def afterAll() {
    dbCreatorCtx.close()
  }

  type FixtureParam = Db
  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val tmpDbName = s"itTestTmpDb_${randomUUID.toString.replace("-", "")}"

    // println(s"$tmpDbName: creating...")
    dbCreatorCtx.executeAction(s"""CREATE DATABASE "$tmpDbName" TEMPLATE wust_template CONNECTION LIMIT 1""")

    val testEnvConfig = defaultDbConfig.withValue("database", ConfigValueFactory.fromAnyRef(tmpDbName))
    val testDb = new Db(new PostgresAsyncContext[LowerCase](testEnvConfig))
    complete {
      // println(s"$tmpDbName: running test...")
      withFixture(test.toNoArgAsyncTest(testDb))
    } lastly {
      testDb.ctx.close()
      // println(s"$tmpDbName: closed test conn")
      try {
        // println(s"$tmpDbName: dropping...")
        dbCreatorCtx.executeAction(s"""DROP DATABASE "$tmpDbName"""")
      } catch { case e: Throwable => println(e) }
    }
  }
}
