package wust.db

import java.util.UUID.randomUUID

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import io.getquill._
import org.scalatest._

object DbIntegrationTestSpec {
  private val templateDb = "wust_template"
  private val config = ConfigFactory.load()
  private val dbConfig = config.getConfig("testDb")

  val controlCtx = new PostgresAsyncContext[LowerCase](configWithDb("postgres"))
  def configWithDb(database: String) = dbConfig.withValue("database", ConfigValueFactory.fromAnyRef(database))
}

trait DbIntegrationTestSpec extends fixture.AsyncFreeSpec with BeforeAndAfterAll {
  import DbIntegrationTestSpec._

  type FixtureParam = Db
  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val tmpDbName = s"itTestTmpDb_${randomUUID.toString.replace("-", "")}"

    val testDb = controlCtx
      .executeAction(s"""CREATE DATABASE "$tmpDbName" TEMPLATE "$templateDb" CONNECTION LIMIT 1""")
      .map(_ => Db(configWithDb(tmpDbName)))

    new FutureOutcome(testDb.flatMap { testDb =>
      complete {
        withFixture(test.toNoArgAsyncTest(testDb)).toFuture
      } lastly {
        try {
          testDb.ctx.close()
          controlCtx.executeAction(s"""DROP DATABASE "$tmpDbName"""")
        }
        catch { case e: Throwable => println(e) }
      }
    })
  }
}
