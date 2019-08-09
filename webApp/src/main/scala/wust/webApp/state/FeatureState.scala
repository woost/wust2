package wust.webApp.state

import org.scalajs.dom.console
import scala.scalajs.js
import wust.util.time.time
import collection.mutable
import wust.webApp.{ DevOnly, StagingOnly, DebugOnly }
import acyclic.file
import rx._
import wust.ids.{ Feature, _ }
import wust.webUtil.Elements.defer
import wust.webApp.Client
import wust.webUtil.UI
import UI.ToastLevel
import wust.api.UsedFeature
import monix.reactive.subjects.PublishSubject
import wust.facades.googleanalytics.Analytics

import scala.collection.breakOut
import scala.concurrent.ExecutionContext

object FeatureState {
  //TODO: show next on loading screen?
  val firstTimeUsed = Var[Map[Feature, EpochMilli]](Map.empty)
  val recentFirstTimeUsed: Rx[Seq[Feature]] = Rx {
    firstTimeUsed().toArray.sortBy(-_._2).map(_._1)
  }

  val recentlyUsedLimit = 1
  val recentlyUsed = Var[Vector[Feature]](Vector.empty)

  implicit val ec = ExecutionContext.global //TODO: what else?
  GlobalState.userId.foreach { _ =>
    //TODO: only for persisted users
    firstTimeUsed() = Map.empty[Feature, EpochMilli]
    recentlyUsed() = Vector.empty
    Client.api.getUsedFeatures().foreach { list =>
      firstTimeUsed() = list.map{ case UsedFeature(feature, timestamp) => feature -> timestamp }(breakOut): Map[Feature, EpochMilli]
      recentlyUsed() = recentFirstTimeUsed.now.distinct.take(recentlyUsedLimit).toVector
    }
  }

  val nextCandidates: Rx[Set[Feature]] = Rx {
    @inline def isUsed(f: Feature) = firstTimeUsed().isDefinedAt(f)
    var candidates = Feature.all.filterNot(f => isUsed(f) || Feature.secrets.contains(f)).toSet
    Feature.all.foreach { prevFeature =>
      prevFeature.next.foreach { nextFeature =>
        if (!isUsed(prevFeature) && !Feature.secrets.contains(prevFeature)) candidates -= nextFeature
      }
    }
    Feature.startingPoints.foreach { feature =>
      if (!isUsed(feature)) candidates += feature
    }
    candidates
  }

  val next: Rx[Seq[Feature]] = Rx { calculateSuggestions(recentlyUsed(), recentFirstTimeUsed(), nextCandidates()) }

  def calculateSuggestions(recentlyUsed: Seq[Feature], recentFirstTimeUsed: Seq[Feature], nextCandidates: Set[Feature], limit: Int = 3): Seq[Feature] = {
    val starts = mutable.Queue.empty[Feature] ++ (recentlyUsed ++ recentFirstTimeUsed ++ Feature.startingPoints).distinct
    val suggested = mutable.HashSet.empty[Feature]
    val suggestions = mutable.ArrayBuffer.empty[Feature]
    while (suggestions.length < limit && starts.nonEmpty) {
      val start = starts.dequeue()
      Feature.bfs(_(start), { feature =>
        if (suggestions.length < limit && !suggested(feature) && nextCandidates(feature)) {
          suggested += feature
          suggestions += feature
        }
      })
    }

    DevOnly {
      if ((suggestions.toSet intersect recentFirstTimeUsed.toSet).nonEmpty)
        UI.toast("suggested already used features: " + (suggestions.toSet intersect recentFirstTimeUsed.toSet), title = "FeatureState", level = ToastLevel.Error, autoclose = false)
    }
    suggestions
  }

  val usedNewFeatureTrigger = PublishSubject[Unit]

  def use(feature: Feature): Unit = {
    StagingOnly {
      defer {
        time("Feature update") {
          @inline def firstTimeUse = !firstTimeUsed.now.isDefinedAt(feature)
          if (firstTimeUse) {
            val timestamp = EpochMilli.now
            firstTimeUsed.update(_ + (feature -> timestamp))
            Client.api.useFeatureForFirstTime(UsedFeature(feature, timestamp))
            usedNewFeatureTrigger.onNext(())
            Analytics.sendEvent("first-time-feature", feature.toString)
            //TODO: add tags corresponding to features / categories to hotjar
          }
          recentlyUsed.update(recentlyUsed => (feature +: recentlyUsed).take(recentlyUsedLimit).distinct)
          Analytics.sendEvent("feature", feature.toString)

          scribe.debug("Used Feature: " + feature.toString)
          DebugOnly {
            UI.toast(feature.toString)

            console.asInstanceOf[js.Dynamic].groupCollapsed(s"Feature Dotgraph")
            console.log(Feature.dotGraph(recentFirstTimeUsed.now, recentlyUsed.now, nextCandidates.now, next.now))
            console.asInstanceOf[js.Dynamic].groupEnd()

            //TODO: self-loops, reachability

            if (recentlyUsed.now.length > recentlyUsedLimit)
              UI.toast("recentlyUsed has too many elements: " + recentlyUsed.now.length + "/" + recentlyUsedLimit, title = "FeatureState", level = ToastLevel.Error, autoclose = false)
            if (recentlyUsed.now != recentlyUsed.now.distinct)
              UI.toast("recentlyUsed is not distinct: " + recentlyUsed.now, title = "FeatureState", level = ToastLevel.Error, autoclose = false)
            if (!(next.now.toSet subsetOf nextCandidates.now.toSet))
              UI.toast("next is not subset of nextCandidates: Next:" + next.now + " / Candidates:" + nextCandidates.now.toSet, title = "FeatureState", level = ToastLevel.Error, autoclose = false)
          }
        }
      }
    }
  }
}
