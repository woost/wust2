package wust.framework.state

import org.scalatest._

import scala.concurrent.Future
import scala.collection.mutable

class StateHolderSpec extends AsyncFreeSpec with MustMatchers {
  private def newStateHolder[T](state: String): (StateHolder[String, Int], mutable.Seq[Int]) = {
    val events = mutable.ArrayBuffer.empty[Int]
    val holder = new StateHolder[String, Int](Future.successful(state), events += _)
    (holder, events)
  }

  "result to requestResponse" in {
    val initialState = ""
    val (holder, events) = newStateHolder(initialState)
    import holder.resultIsRequestResponse

    val res: RequestResponse[Int,Int] = 2

    holder.state.map(_ mustEqual initialState)
    res.result mustEqual 2
    res.events.size mustEqual 0
    events.size mustEqual 0
  }

  "future result to requestResponse" in {
    val initialState = ""
    val (holder, events) = newStateHolder(initialState)
    import holder.futureResultIsRequestResponse

    val res: Future[RequestResponse[Int,Int]] = Future.successful[Int](2)

    holder.state.map(_ mustEqual initialState)
    res.map { res =>
      res.result mustEqual 2
      res.events.size mustEqual 0
      events.size mustEqual 0
    }
  }

  "execute result function" in {
    val initialState = ""
    val (holder, events) = newStateHolder(initialState)
    import holder.resultFunctionIsExecuted

    val res: Future[Int] = { (state: String) =>
      Future.successful(2)
    }

    holder.state.map(_ mustEqual initialState)
    res.map { res =>
      res mustEqual 2
      events.size mustEqual 0
    }
  }

  "execute requestResponse function" in {
    val initialState = ""
    val (holder, events) = newStateHolder(initialState)
    import holder.responseFunctionIsExecuted

    val res: Future[Int] = { (state: String) =>
      Future.successful(RequestResponse(2, Seq(666)))
    }

    holder.state.map(_ mustEqual initialState)
    res.map { res =>
      res mustEqual 2
      events must contain theSameElementsAs Seq(666)
    }
  }

  "execute noEffect function" in {
    val initialState = ""
    val (holder, events) = newStateHolder(initialState)
    import holder.{StateEffect, effectFunctionIsExecuted}

    val res: Future[Int] = { (state: String) =>
      val response = Future.successful(RequestResponse(2, Seq(666)))
      StateEffect.none(response)
    }

    holder.state.map(_ mustEqual initialState)
    res.map { res =>
      res mustEqual 2
      events must contain theSameElementsAs Set(666)
    }
  }

  "execute stateEffect function" in {
    val initialState = ""
    val (holder, events) = newStateHolder(initialState)
    import holder.{StateEffect, effectFunctionIsExecuted}

    val nextState = "new"
    val res: Future[Int] = { (state: String) =>
      val newState = Future.successful(nextState)
      val response = Future.successful(RequestResponse(2, Seq(666)))
      StateEffect.replace(newState, response)
    }

    holder.state.map(_ mustEqual nextState)
    res.map { res =>
      res mustEqual 2
      events must contain theSameElementsAs Set(666)
    }
  }

  "StateEffect" - {
    val initialState = ""
    val (holder, _) = newStateHolder(initialState)
    import holder.{StateEffect, RequestResponse}

    "none" in {
      val response = RequestResponse(1, 666)
      val effect = StateEffect.none(Future.successful(response))

      effect.state mustEqual None
      effect.response.map(_ mustEqual response)
    }

    "replace" in {
      val response = RequestResponse(1, 666)
      val newState = "foo"
      val effect = StateEffect.replace(Future.successful(newState), Future.successful(response))
      effect.state.isDefined mustEqual true
      effect.state.get.map(_ mustEqual newState)
      effect.response.map(_ mustEqual response)
    }
  }

  "RequestResponse" - {
    val initialState = ""
    val (holder, _) = newStateHolder(initialState)
    import holder.RequestResponse

    "eventsIf true" in {
      val response = RequestResponse.eventsIf(true, 666)

      response.result mustEqual true
      response.events must contain theSameElementsAs Set(666)
    }

    "eventsIf false" in {
      val response = RequestResponse.eventsIf(false, 666)

      response.result mustEqual false
      response.events.size mustEqual 0
    }
  }
}
