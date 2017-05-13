package wust.backend

import org.scalatest._
import wust.backend.auth.JWT
import wust.api._
import wust.graph.{Group, User}
import wust.ids._

import scala.concurrent.Future

class StateDslSpec extends AsyncFreeSpec with MustMatchers {
  val implicitUser = User(14, "implicit", isImplicit = true, 0)
  val initialUser = User(11, "existing", isImplicit = false, 0)

  def implicitDsl = {
    var count = -1
    new StateDsl(() => {
      count = count + 1
      val auth = JWT.generateAuthentication(implicitUser.copy(revision = count))
      Future.successful(Option(auth))
    })
  }
  def nonImplicitDsl = new StateDsl(() => Future.successful(None))

  val authState = State(auth = Option(JWT.generateAuthentication(initialUser)), groupIds = Set(1,2))
  val nonAuthState = State(auth = None, groupIds = Set.empty)

  "withUser" - {
    "has user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val NoEffect(response) = fun(authState)
      for {
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
      }
    }

    "has user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val NoEffect(response) = fun(authState)
      for {
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
      }
    }

    "has no user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      an [ApiException] must be thrownBy fun(nonAuthState)
    }

    "has no user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUser { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      an [ApiException] must be thrownBy fun(nonAuthState)
    }
  }

  "withUserOrImplicit" - {
    "has user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(authState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state mustEqual authState
      }
    }

    "has user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(authState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state mustEqual authState
      }
    }

    "has no user and implicit" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(nonAuthState)
      for {
        state <- state
        response <- response
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        state.groupIds mustEqual nonAuthState.groupIds
        state.auth.map(_.user) mustEqual Option(implicitUser)
      }
    }

    "has no user and no implicit" in {
      val dsl = nonImplicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(nonAuthState)
      for {
        state <- state
      } yield {
        state mustEqual nonAuthState
      }

      recoverToSucceededIf[ApiException] { response }
    }

    "has no user and multiple implicit are equal" in {
      val dsl = implicitDsl
      import dsl._

      val fun: State => RequestEffect[String] = withUserOrImplicit { (state, user) =>
        state.auth.map(_.user) mustEqual Option(user)
        Future.successful(RequestResponse("str"))
      }

      val StateEffect(state, response) = fun(nonAuthState)
      val StateEffect(state2, response2) = fun(nonAuthState)
      for {
        state <- state
        state2 <- state2
        response <- response
        response2 <- response2
      } yield {
        response.result mustEqual "str"
        response.events mustEqual Seq.empty
        response2.result mustEqual "str"
        response2.events mustEqual Seq.empty
        state.groupIds mustEqual nonAuthState.groupIds
        state.auth.map(_.user) mustEqual Option(implicitUser)
        state2.auth mustEqual state.auth
      }
    }
  }
}

class RequestResponseSpec extends FreeSpec with MustMatchers {
  "eventsIf true" in {
    val response = RequestResponse.eventsIf(true, NewGroup(Group(1)))

    response.result mustEqual true
    response.events must contain theSameElementsAs Set(NewGroup(Group(1)))
  }

  "eventsIf false" in {
    val response = RequestResponse.eventsIf(false, NewGroup(Group(1)))

    response.result mustEqual false
    response.events.size mustEqual 0
  }
}

class StateAccessSpec extends AsyncFreeSpec with MustMatchers {

  "result to requestResponse" in {
    val initialState = State(None, Set(113))
    val events = collection.mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(initialState), ev => events += ev, () => ???)
    import access.resultIsRequestResponse

    val res: RequestResponse[Set[GroupId]] = initialState.groupIds

    access.state.map(_ mustEqual initialState)
    res.result mustEqual initialState.groupIds
    res.events.size mustEqual 0
    events.size mustEqual 0
  }

  "future result to requestResponse" in {
    val initialState = State(None, Set(113))
    val events = collection.mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(initialState), ev => events += ev, () => ???)
    import access.futureResultIsRequestResponse

    val res: Future[RequestResponse[Set[GroupId]]] = Future.successful[Set[GroupId]](initialState.groupIds)

    access.state.map(_ mustEqual initialState)
    res.map { res =>
      res.result mustEqual initialState.groupIds
      res.events.size mustEqual 0
      events.size mustEqual 0
    }
  }

  "execute result function" in {
    val initialState = State(None, Set(113))
    val events = collection.mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(initialState), ev => events += ev, () => ???)
    import access.resultFunctionIsExecuted

    val res: Future[Set[GroupId]] = { (state: State) =>
      Future.successful(state.groupIds)
    }

    access.state.map(_ mustEqual initialState)
    res.map { res =>
      res mustEqual initialState.groupIds
      events.size mustEqual 0
    }
  }

  "execute requestResponse function" in {
    val initialState = State(None, Set(113))
    val events = collection.mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(initialState), ev => events += ev, () => ???)
    import access.responseFunctionIsExecuted

    val res: Future[Set[GroupId]] = { (state: State) =>
      Future.successful(RequestResponse(state.groupIds, state.groupIds.map(id => NewGroup(Group(id))).toSeq: _*))
    }

    access.state.map(_ mustEqual initialState)
    res.map { res =>
      res mustEqual initialState.groupIds
      events must contain theSameElementsAs Set(ChannelEvent(Channel.All, NewGroup(Group(113))))
    }
  }

  "execute noEffect function" in {
    val initialState = State(None, Set(113))
    val events = collection.mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(initialState), ev => events += ev, () => ???)
    import access.effectFunctionIsExecuted

    val res: Future[Set[GroupId]] = { (state: State) =>
      val response = Future.successful(RequestResponse(state.groupIds, state.groupIds.map(id => NewGroup(Group(id))).toSeq: _*))
      NoEffect(response)
    }

    access.state.map(_ mustEqual initialState)
    res.map { res =>
      res mustEqual initialState.groupIds
      events must contain theSameElementsAs Set(ChannelEvent(Channel.All, NewGroup(Group(113))))
    }
  }

  "execute stateEffect function" in {
    val initialState = State(None, Set(113))
    val events = collection.mutable.ArrayBuffer.empty[ChannelEvent]
    val access = new StateAccess(Future.successful(initialState), ev => events += ev, () => ???)
    import access.effectFunctionIsExecuted

    val newIds = Set(GroupId(114))
    val res: Future[Set[GroupId]] = { (state: State) =>
      val newState = Future.successful(state.copyF(groupIds = _ ++ newIds))
      val response = newState.map(s => RequestResponse(s.groupIds, s.groupIds.map(id => NewGroup(Group(id))).toSeq: _*))
      StateEffect(newState, response)
    }

    access.state.map(_ mustEqual initialState.copyF(groupIds = _ ++ newIds))
    res.map { res =>
      res mustEqual initialState.groupIds ++ newIds
      events must contain theSameElementsAs Set(ChannelEvent(Channel.All, NewGroup(Group(113))), ChannelEvent(Channel.All, NewGroup(Group(114))))
    }
  }
}
