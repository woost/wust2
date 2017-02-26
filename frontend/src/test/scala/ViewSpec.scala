package frontend

import utest._

object HelloTests extends TestSuite{
  val tests = this{
    'test1{
      assert(1 == 1)
    }
  }
}
