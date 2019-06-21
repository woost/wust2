//import org.scalatest.{BeforeAndAfterAll, FutureOutcome, fixture}
//import wust.db.Db
//
//trait DbIntegrationTestSpec extends fixture.AsyncFreeSpec with BeforeAndAfterAll {
//
//  type FixtureParam = Db
//  def withFixture(test: OneArgAsyncTest): FutureOutcome = {
//    val tmpDbName = s"itTestTmpDb_${randomUUID.toString.replace("-", "")}"
//
//    val testDb = controlCtx
//      .executeAction(s"""CREATE DATABASE "$tmpDbName" TEMPLATE "$templateDb" CONNECTION LIMIT 1""")
//      .map(_ => Db(configWithDb(tmpDbName)))
//
//    new FutureOutcome(testDb.flatMap { testDb =>
//      complete {
//        withFixture(test.toNoArgAsyncTest(testDb)).toFuture
//      } lastly {
//        try {
//          testDb.ctx.close()
//          controlCtx.executeAction(s"""DROP DATABASE "$tmpDbName"""")
//        } catch { case e: Throwable => println(e) }
//      }
//    })
//  }
//}
