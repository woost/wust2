package wust.webUtil.outwatchHelpers

import monix.execution.{Ack, Cancelable}
import monix.reactive.{Observable, Observer}
import outwatch._

import scala.concurrent.{ExecutionContext, Future}
import scala.scalajs.js

@inline class ModifierBooleanOps(val condition: Boolean) extends AnyVal {
  @inline def apply(m: => VDomModifier):VDomModifier = if(condition) VDomModifier(m) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier, m6: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5,m6) else VDomModifier.empty
  @inline def apply(m: => VDomModifier, m2: => VDomModifier, m3: => VDomModifier, m4: => VDomModifier, m5: => VDomModifier, m6: => VDomModifier, m7: => VDomModifier):VDomModifier = if(condition) VDomModifier(m,m2,m3,m4,m5,m6,m7) else VDomModifier.empty
}

