package wust.webApp.state

import acyclic.file
import outwatch.reactive._
import rx._
import wust.api.{AuthUser, UsedFeature}
import wust.facades.segment.Segment
import wust.ids.{Feature, _}
import wust.util.time.time
import wust.webApp.{Client, DebugOnly, DevOnly}
import wust.webUtil.Elements.defer
import wust.webUtil.UI
import wust.webUtil.UI.ToastLevel

import scala.collection.{breakOut, mutable}
import scala.concurrent.ExecutionContext

object FeatureState {
  //TODO: show next on loading screen?
  val firstTimeUsed = Var[Map[Feature, EpochMilli]](Map.empty)
  val recentFirstTimeUsed: Rx[Seq[Feature]] = Rx {
    firstTimeUsed().toArray.sortBy(-_._2).map(_._1)
  }

  val recentlyUsedLimit = 1
  val recentlyUsed = Var[Vector[Feature]](Vector.empty)
  private val notSentFirstTimeFeatures = mutable.HashMap.empty[Feature, EpochMilli]
  private var currentUserId: UserId = GlobalState.userId.now

  implicit val ec = ExecutionContext.global //TODO: what else?
  GlobalState.user.foreach {
    case user: AuthUser.Persisted =>
      if (user.id != currentUserId) {
        firstTimeUsed() = Map.empty[Feature, EpochMilli]
        recentlyUsed() = Vector.empty
        notSentFirstTimeFeatures.clear()
        Client.api.getUsedFeatures().foreach { list =>
          firstTimeUsed() = list.map{ case UsedFeature(feature, timestamp) => feature -> timestamp }(breakOut): Map[Feature, EpochMilli]
          recentlyUsed() = recentFirstTimeUsed.now.distinct.take(recentlyUsedLimit).toVector
        }

        currentUserId = user.id
      } else { // user is the same
        sendNotSentFeatures()
        Client.api.getUsedFeatures().foreach { list =>
          firstTimeUsed() = firstTimeUsed.now ++ list.map{ case UsedFeature(feature, timestamp) => feature -> timestamp }(breakOut): Map[Feature, EpochMilli]
          recentlyUsed() = recentFirstTimeUsed.now.distinct.take(recentlyUsedLimit).toVector
        }
      }
    case assumedUser: AuthUser.Assumed =>
      firstTimeUsed() = Map.empty[Feature, EpochMilli]
      recentlyUsed() = Vector.empty
      currentUserId = assumedUser.id
  }

  val nextCandidates: Rx[Set[Feature]] = Rx {
    @inline def isUsed(f: Feature) = firstTimeUsed().isDefinedAt(f)
    var candidates = Feature.all.filterNot(f => isUsed(f) || Feature.secretsSet.contains(f)).toSet

    // remove features where requirements are not fulfilled
    candidates.foreach { nextFeature =>
      if (!nextFeature.requiresAll.forall(isUsed)) {
        candidates -= nextFeature
      } else if (nextFeature.requiresAny.nonEmpty && !nextFeature.requiresAny.exists(isUsed)) {
        candidates -= nextFeature
      }
    }
    Feature.startingPoints.foreach { feature =>
      if (!isUsed(feature)) candidates += feature
    }
    candidates
  }

  val nextFeatureLimit = 5
  val next: Rx[Seq[Feature]] = Rx { calculateSuggestions(recentlyUsed(), recentFirstTimeUsed(), nextCandidates(), limit = nextFeatureLimit) }

  def calculateSuggestions(recentlyUsed: Seq[Feature], recentFirstTimeUsed: Seq[Feature], nextCandidates: Set[Feature], limit: Int): Seq[Feature] = {
    val starts = mutable.Queue.empty[Feature] ++ (recentlyUsed ++ recentFirstTimeUsed ++ Feature.startingPoints).distinct
    val suggested = mutable.HashSet.empty[Feature]
    val suggestions = mutable.ArrayBuffer.empty[Feature]
    while (suggestions.length < limit && starts.nonEmpty) {
      var start = starts.dequeue()
      val backPath = mutable.Queue.empty[Feature]
      Feature.dfsBack(_(start), backPath += _) //TODO: cache
      while (suggestions.length < limit && backPath.nonEmpty) {
        val backPathStart = backPath.dequeue()
        // Important: The order of suggested features (_.next) should be preserved
        // Bfs itself preserves the order of items as listed in feature.next
        //TODO: cache if bfs for node is empty (all succeeding features are used)
        Feature.bfs(_(backPathStart), { feature =>
          if (suggestions.length < limit && !suggested(feature) && nextCandidates(feature)) {
            suggested += feature
            suggestions += feature
          }
        })
      }
    }

    DevOnly {
      if ((suggestions.toSet intersect recentFirstTimeUsed.toSet).nonEmpty)
        UI.toast("suggested already used features: " + (suggestions.toSet intersect recentFirstTimeUsed.toSet), title = "FeatureState", level = ToastLevel.Error, autoclose = false)
    }
    suggestions
  }

  val usedNewFeatureTrigger = SinkSourceHandler.publish[Unit]

  def canUseApi: Boolean = {
    GlobalState.user.now match {
      case _: AuthUser.Persisted => true
      case _                     => false
    }
  }

  private def sendNotSentFeatures(): Unit = {
    if (canUseApi) {
      notSentFirstTimeFeatures.map {
        case (feature, timestamp) =>
          //TODO: track timestamp in backend to become the source of truth
          Client.api.useFeatureForFirstTime(UsedFeature(feature, timestamp)).foreach { _ =>
            notSentFirstTimeFeatures -= feature
          }
      }
    }
  }
  private def persistFirstTimeUsage(feature: Feature, timestamp: EpochMilli) = {
    if (canUseApi) {
      Client.api.useFeatureForFirstTime(UsedFeature(feature, timestamp))
      sendNotSentFeatures()
    } else
      notSentFirstTimeFeatures += (feature -> timestamp)
  }

  def use(feature: Feature): Unit = {
    defer {
      time("Feature update") {
        @inline def firstTimeUse = !firstTimeUsed.now.isDefinedAt(feature)
        if (firstTimeUse) {
          val timestamp = EpochMilli.now
          firstTimeUsed.update(_ + (feature -> timestamp))
          persistFirstTimeUsage(feature, timestamp)
          if (Feature.allWithoutSecretsSet.contains(feature)) usedNewFeatureTrigger.onNext(())
        }
        recentlyUsed.update(recentlyUsed => (feature +: recentlyUsed).take(recentlyUsedLimit).distinct)

        Segment.trackEvent(feature.toString)

        scribe.debug("Used Feature: " + feature.toString)
        DebugOnly {
          UI.toast(feature.toString)

          if (Feature.selfLoops.nonEmpty)
            UI.toast("selfLoops: " + Feature.selfLoops.toList, title = "FeatureState", level = ToastLevel.Error, autoclose = true)
          if (Feature.unreachable.nonEmpty)
            UI.toast("unreachable: " + Feature.unreachable, title = "FeatureState", level = ToastLevel.Error, autoclose = true)
          if (recentlyUsed.now.length > recentlyUsedLimit)
            UI.toast("recentlyUsed has too many elements: " + recentlyUsed.now.length + "/" + recentlyUsedLimit, title = "FeatureState", level = ToastLevel.Error, autoclose = true)
          if (recentlyUsed.now != recentlyUsed.now.distinct)
            UI.toast("recentlyUsed is not distinct: " + recentlyUsed.now, title = "FeatureState", level = ToastLevel.Error, autoclose = true)
          if (!(next.now == next.now.distinct))
            UI.toast("next is not distinct: Next:" + next.now, title = "FeatureState", level = ToastLevel.Error, autoclose = true)
          if (!(next.now.toSet subsetOf nextCandidates.now.toSet))
            UI.toast("next is not subset of nextCandidates: Next:" + next.now + " / Candidates:" + nextCandidates.now.toSet, title = "FeatureState", level = ToastLevel.Error, autoclose = true)
        }
      }
    }
  }
}
