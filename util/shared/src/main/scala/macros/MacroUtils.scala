package wust.util.macros

import scala.reflect.macros.blackbox

class MacroUtils[C <: blackbox.Context](val c: C) {
  import c.universe._

  def fullNameTree(t: Symbol): Tree = {
    val names = t.fullName.split("\\.")
    names.tail.foldLeft[Tree](Ident(TermName(names.head)))((q,n) => Select(q, TermName(n)))
  }
}
object MacroUtils {
  def apply(c: blackbox.Context): MacroUtils[c.type] = new MacroUtils(c)
}
