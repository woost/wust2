package wust.api.serialize

import boopickle.Default._
import wust.api._
import wust.graph._

object Boopickle extends wust.ids.serialize.Boopickle {

  implicit val graphPickler: Pickler[Graph] = new Pickler[Graph] {
    override def pickle(obj: Graph)(implicit state: PickleState): Unit = {
      state.pickle(obj.nodes)
      state.pickle(obj.edges)
    }
    override def unpickle(implicit state: UnpickleState): Graph = {
      val nodes = state.unpickle[Array[Node]]
      val edges = state.unpickle[Array[Edge]]
      Graph(nodes, edges)
    }
  }

  implicit val PasswordPickler: Pickler[Password] = stringPickler.xmap(Password)(_.string)
  implicit val AuthTokenPickler: Pickler[Authentication.Token] = stringPickler.xmap(Authentication.Token)(_.string)

  implicit val postMetaPickler: Pickler[NodeMeta] = generatePickler[NodeMeta]
  implicit val connectionPickler: Pickler[Edge] = generatePickler[Edge]
  implicit val postPickler: Pickler[Node] = generatePickler[Node]
  implicit val userAssumedPickler: Pickler[AuthUser.Assumed] = generatePickler[AuthUser.Assumed]
  implicit val userPersistedPickler: Pickler[AuthUser.Persisted] =
    generatePickler[AuthUser.Persisted]
  implicit val userPickler: Pickler[AuthUser] = generatePickler[AuthUser]
  implicit val graphChangesPickler: Pickler[GraphChanges] = generatePickler[GraphChanges]

  implicit val authenticationPickler: Pickler[Authentication] = generatePickler[Authentication]

  implicit val apiEventScopePickler: Pickler[ApiEvent.Scope] = generatePickler[ApiEvent.Scope]
  implicit val apiEventPickler: Pickler[ApiEvent] = generatePickler[ApiEvent]

  implicit val apiErrorPickler: Pickler[ApiError] = generatePickler[ApiError]

  implicit val pluginUserAuthenticationPickler: Pickler[PluginUserAuthentication] = generatePickler[PluginUserAuthentication]
}
