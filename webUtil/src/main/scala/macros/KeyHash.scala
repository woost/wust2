package wust.webUtil.macros

import outwatch.dom.Key

import scala.reflect.macros.blackbox.Context

// These macros are for efficiently injecting location-aware keys into our outwatch code.
// The key is an integer and is derived as the hashcode of the filepath, line and column of the source code.
// Usage.like: KeyHash.keyValue             // generate a hashCode of the location in the source code of this function call
//             KeyHash.keyValue(varA, varB) // to make the hashcode dependent on varA and varB as well
//             KeyHash.keyed                // shortcut for `dsl.key := KeyHash.keyValue`
//             KeyHash.keyed(varA, varB)    // shortcut for `dsl.key := KeyHash.keyValue(varA, varB)`

object KeyHashMacro {
  private def positionHashCode(pos: Position): Int = List(pos.source.path, pos.line, pos.column).hashCode()

  def keyValue(c: Context): c.Expr[Int] = {
    import c.universe._

    val hash = positionHashCode(c.enclosingPosition)

    c.Expr[Int](q"$hash")
  }
  def keyValueWith(c: Context)(values: c.Expr[Any]*): c.Expr[Int] = {
    import c.universe._

    val hash = positionHashCode(c.enclosingPosition)

    c.Expr[Int](q"_root_.scala.runtime.Statics.mix($hash, ${values.toList}.hashCode())")
  }
  def key(c: Context): c.Expr[Key] = {
    import c.universe._

    val hash = positionHashCode(c.enclosingPosition)

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := $hash")
  }
  def keyWith(c: Context)(values: c.Expr[Any]*): c.Expr[Key] = {
    import c.universe._

    val hash = positionHashCode(c.enclosingPosition)

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := _root_.scala.runtime.Statics.mix($hash, ${values.toList}.hashCode())")
  }
}

trait KeyHash {
  def keyValue: Int = macro KeyHashMacro.keyValue
  def keyValue(values: Any*): Int = macro KeyHashMacro.keyValueWith
  def keyed: Key = macro KeyHashMacro.key
  def keyed(values: Any*): Key = macro KeyHashMacro.keyWith
}
object KeyHash extends KeyHash
