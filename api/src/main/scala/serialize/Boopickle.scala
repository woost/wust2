package wust.api.serialize

import wust.graph._
import wust.api._
import wust.ids._
import boopickle.DefaultBasic._, PicklerGenerator._
import io.treev.tag._

object Boopickle {
  implicit val postIdPickler = transformPickler[PostId, UuidType](PostId(_))(identity)
  implicit val groupIdPickler = transformPickler[GroupId, IdType](GroupId(_))(identity)
  implicit val userIdPickler = transformPickler[UserId, UuidType](UserId(_))(identity)
  implicit val labelPickler = transformPickler[Label, String](Label(_))(identity)

  implicit val epochMilliPickler: Pickler[EpochMilli] = transformPickler((t: Long) => EpochMilli(t))(identity)

  implicit val connectionPickler = generatePickler[Connection]
  implicit val postPickler = generatePickler[Post]
  implicit val ownershipPickler = generatePickler[Ownership]
  implicit val userPickler1 = generatePickler[User.Assumed]
  implicit val userPickler2 = generatePickler[User.Real]
  implicit val userPickler3 = generatePickler[User.Implicit]
  implicit val userPickler4 = generatePickler[User.Persisted]
  implicit val userPickler = generatePickler[User]
  implicit val groupPickler = generatePickler[Group]
  implicit val membershipPickler = generatePickler[Membership]
  implicit val graphPickler = generatePickler[Graph]
  implicit val graphChangesPickler = generatePickler[GraphChanges]

  implicit val authenticationPickler1 = generatePickler[Authentication.Verified]
  implicit val authenticationPickler2 = generatePickler[Authentication.Assumed]
  implicit val authenticationPickler = generatePickler[Authentication]

  implicit val apiEventPickler1 = generatePickler[ApiEvent.NewUser]
  implicit val apiEventPickler2 = generatePickler[ApiEvent.NewGroup]
  implicit val apiEventPickler3 = generatePickler[ApiEvent.NewMembership]
  implicit val apiEventPickler5 = generatePickler[ApiEvent.NewGraphChanges.ForPublic]
  implicit val apiEventPickler6 = generatePickler[ApiEvent.NewGraphChanges.ForPrivate]
  implicit val apiEventPickler7 = generatePickler[ApiEvent.NewGraphChanges.ForAll]
  implicit val apiEventPickler8 = generatePickler[ApiEvent.ReplaceGraph]
  implicit val apiEventPickler9 = generatePickler[ApiEvent.LoggedIn]
  implicit val apiEventPickler10 = generatePickler[ApiEvent.AssumeLoggedIn]
  implicit val apiEventPickler = generatePickler[ApiEvent]

  implicit val apiErrorPickler1 = generatePickler[ApiError.ServerError]
  implicit val apiErrorPickler2 = generatePickler[ApiError.InternalServerError.type]
  implicit val apiErrorPickler3 = generatePickler[ApiError.Unauthorized.type]
  implicit val apiErrorPickler4 = generatePickler[ApiError.Forbidden.type]
  implicit val apiErrorPickler = generatePickler[ApiError]

  implicit val nlpHeuristicPickler1 = generatePickler[NlpHeuristic.DiceSorensen]
  implicit val nlpHeuristicPickler2 = generatePickler[NlpHeuristic.Hamming.type]
  implicit val nlpHeuristicPickler3 = generatePickler[NlpHeuristic.Jaccard]
  implicit val nlpHeuristicPickler4 = generatePickler[NlpHeuristic.Jaro.type]
  implicit val nlpHeuristicPickler5 = generatePickler[NlpHeuristic.JaroWinkler.type]
  implicit val nlpHeuristicPickler6 = generatePickler[NlpHeuristic.Levenshtein.type]
  implicit val nlpHeuristicPickler7 = generatePickler[NlpHeuristic.NGram]
  implicit val nlpHeuristicPickler8 = generatePickler[NlpHeuristic.Overlap]
  implicit val nlpHeuristicPickler9 = generatePickler[NlpHeuristic.RatcliffObershelp.type]
  implicit val nlpHeuristicPickler10 = generatePickler[NlpHeuristic.WeightedLevenshtein]
  implicit val nlpHeuristicPickler = generatePickler[NlpHeuristic]
}
