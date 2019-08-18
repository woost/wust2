package wust.facades.hopscotch

import scala.scalajs.js
import scala.scalajs.js.annotation._

@js.native
@JSGlobal
object hopscotch extends js.Object {
  def startTour(tour: Tour): Unit = js.native
  def startTour(tour: Tour, stepNum: Int): Unit = js.native
  def showStep(idx: Int): Unit = js.native
  def prevStep(): Unit = js.native
  def nextStep(): Unit = js.native
  def getCurrTour(): js.UndefOr[Tour] = js.native
  def getCurrStepNum(): js.UndefOr[Int] = js.native
  def endTour(clearCookie:Boolean = true): Unit = js.native
}

trait Tour extends js.Object {
  var id: String
  var steps: js.Array[Step]

  var onEnd: js.UndefOr[js.Function0[Unit]] = js.undefined
  var i18n: js.UndefOr[I18n] = js.undefined
}

trait Step extends js.Object {
  var target: String
  var placement: String
  var title: js.UndefOr[String] = js.undefined
  var delay: js.UndefOr[Int] = js.undefined
  var content: js.UndefOr[String] = js.undefined
  var showNextButton: js.UndefOr[Boolean] = js.undefined
  var showPrevButton: js.UndefOr[Boolean] = js.undefined
  var showCTAButton: js.UndefOr[Boolean] = js.undefined
  var showSkip: js.UndefOr[Boolean] = js.undefined
  var ctaLabel: js.UndefOr[String] = js.undefined
  var onNext: js.UndefOr[js.Function0[Unit]] = js.undefined
  var onCTA: js.UndefOr[js.Function0[Unit]] = js.undefined
  var nextOnTargetClick:js.UndefOr[Boolean] = js.undefined
}

trait I18n extends js.Object {
  var doneBtn: js.UndefOr[String] = js.undefined
}
