package wust.framework.state

import org.scalatest._

import scala.concurrent.Future
import scala.collection.mutable

class StateHolderSpec extends AsyncFreeSpec with MustMatchers {
  private def newStateHolder[T](state: String) = {
    new StateHolder[String, Int](Future.successful(state))
  }

  "result to requestResponse" in {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.resultIsRequestResponse

    val res: RequestResponse[Int,Int] = 2

    holder.state.map(_ mustEqual initialState)
    holder.events.map(_.size mustEqual 0)
    res.result mustEqual 2
    res.events.size mustEqual 0
  }

  "future result to requestResponse" in {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.futureResultIsRequestResponse

    val res: Future[RequestResponse[Int,Int]] = Future.successful[Int](2)

    holder.state.map(_ mustEqual initialState)
    holder.events.map(_.size mustEqual 0)
    res.map { res =>
      res.result mustEqual 2
      res.events.size mustEqual 0
    }
  }

  "execute result function" in {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.resultFunctionIsExecuted

    val res: Future[Int] = { (state: String) =>
      Future.successful(2)
    }

    holder.state.map(_ mustEqual initialState)
    holder.events.map(_.size mustEqual 0)
    res.map { res =>
      res mustEqual 2
    }
  }

  "execute requestResponse function" in {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.{respondWithEvents, responseFunctionIsExecuted}

    val res: Future[Int] = { (state: String) =>
      Future.successful(respondWithEvents(2, 666))
    }

    holder.state.map(_ mustEqual initialState)
    holder.events.map(_ must contain theSameElementsAs Set(666))
    res.map { res =>
      res mustEqual 2
    }
  }

  "execute noEffect function" in {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.{keepState, respondWithEvents, effectFunctionIsExecuted}

    val res: Future[Int] = { (state: String) =>
      val response = Future.successful(respondWithEvents(2, 666))
      keepState(response)
    }

    holder.state.map(_ mustEqual initialState)
    holder.events.map(_ must contain theSameElementsAs Set(666))
    res.map { res =>
      res mustEqual 2
    }
  }

  "execute stateEffect function" in {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.{replaceState, respondWithEvents, effectFunctionIsExecuted}

    val nextState = "new"
    val res: Future[Int] = { (state: String) =>
      val newState = Future.successful(nextState)
      val response = Future.successful(respondWithEvents(2, 666))
      replaceState(newState, response)
    }

    holder.state.map(_ mustEqual nextState)
    holder.events.map(_ must contain theSameElementsAs Set(666))
    res.map { res =>
      res mustEqual 2
    }
  }

  "StateEffect" - {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.{respondWithEvents, keepState, replaceState}

    "keepState" in {
      val response = respondWithEvents(1, 666)
      val effect = keepState(Future.successful(response))

      effect.state mustEqual None
      effect.response.map(_ mustEqual response)
    }

    "replaceState" in {
      val response = respondWithEvents(1, 666)
      val newState = "foo"
      val effect = replaceState(Future.successful(newState), Future.successful(response))
      effect.state.isDefined mustEqual true
      effect.state.get.map(_ mustEqual newState)
      effect.response.map(_ mustEqual response)
    }
  }

  "RequestResponse" - {
    val initialState = ""
    val holder = newStateHolder(initialState)
    import holder.respondWithEventsIf

    "respondWithEventsIf true" in {
      val response = respondWithEventsIf(true, 666)

      response.result mustEqual true
      holder.events.map(_.size mustEqual 0)
    }

    "respondWithEventsIf false" in {
      val response = respondWithEventsIf(false, 666)

      response.result mustEqual false
      holder.events.map(_.size mustEqual 0)
    }
  }
}
