package wust.db

import java.util.UUID.randomUUID

import wust.config.Config
import com.typesafe.config.ConfigValueFactory
import io.getquill._
import org.scalatest._

trait DbIntegrationTestSpec extends fixture.AsyncFreeSpec with BeforeAndAfterAll {
  private val templateDb = "wust_template"

  type FixtureParam = Db
  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val tmpDbName = s"itTestTmpDb_${randomUUID.toString.replace("-", "")}"

    val testDb = Db.ctx.executeAction(s"""CREATE DATABASE "$tmpDbName" TEMPLATE "$templateDb" CONNECTION LIMIT 1""").map { _ =>
      val testEnvConfig = Config.db.withValue("database", ConfigValueFactory.fromAnyRef(tmpDbName))
      new Db(new PostgresAsyncContext[LowerCase](testEnvConfig))
    }

    new FutureOutcome(testDb.flatMap { testDb =>
      complete {
        withFixture(test.toNoArgAsyncTest(testDb)).toFuture
      } lastly {
        try {
          testDb.ctx.close()
          Db.ctx.executeAction(s"""DROP DATABASE "$tmpDbName"""")
        }
        catch { case e: Throwable => println(e) }
      }
    })
  }
}
