package wust.db

import java.util.UUID.randomUUID

import wust.config.Config
import com.typesafe.config.ConfigValueFactory
import io.getquill._
import org.scalatest._

object DbIntegrationTestSpec {
  private val config = Config.db.withValue("database", ConfigValueFactory.fromAnyRef("postgres"))
  val copyCtx = new PostgresAsyncContext[LowerCase](config)
}

trait DbIntegrationTestSpec extends fixture.AsyncFreeSpec with BeforeAndAfterAll {
  import DbIntegrationTestSpec._
  private val templateDb = "wust_template"

  type FixtureParam = Db
  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
    val tmpDbName = s"itTestTmpDb_${randomUUID.toString.replace("-", "")}"

    val testDb = copyCtx.executeAction(s"""CREATE DATABASE "$tmpDbName" TEMPLATE "$templateDb" CONNECTION LIMIT 1""").map { _ =>
      val testEnvConfig = Config.db.withValue("database", ConfigValueFactory.fromAnyRef(tmpDbName))
      new Db(new PostgresAsyncContext[LowerCase](testEnvConfig))
    }

    new FutureOutcome(testDb.flatMap { testDb =>
      complete {
        withFixture(test.toNoArgAsyncTest(testDb)).toFuture
      } lastly {
        try {
          testDb.ctx.close()
          copyCtx.executeAction(s"""DROP DATABASE "$tmpDbName"""")
        }
        catch { case e: Throwable => println(e) }
      }
    })
  }
}
