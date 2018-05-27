package wust.api.serialize

import wust.graph._
import wust.api._
import boopickle.Default._

object Boopickle extends wust.ids.serialize.Boopickle {

  implicit val connectionPickler: Pickler[Connection] = generatePickler[Connection]
  implicit val postPickler: Pickler[Post] = generatePickler[Post]
  implicit val userAssumedPickler: Pickler[User.Assumed] = generatePickler[User.Assumed]
  implicit val userPersistedPickler: Pickler[User.Persisted] = generatePickler[User.Persisted]
  implicit val userPickler: Pickler[User] = generatePickler[User]
  implicit val membershipPickler: Pickler[Membership] = generatePickler[Membership]
  implicit val graphPickler: Pickler[Graph] = generatePickler[Graph]
  implicit val graphChangesPickler: Pickler[GraphChanges] = generatePickler[GraphChanges]

  implicit val authenticationPickler: Pickler[Authentication] = generatePickler[Authentication]

  implicit val apiEventPickler: Pickler[ApiEvent] = generatePickler[ApiEvent]

  implicit val apiErrorPickler: Pickler[ApiError] = generatePickler[ApiError]

  implicit val nlpHeuristicPickler: Pickler[NlpHeuristic] = generatePickler[NlpHeuristic]
}
