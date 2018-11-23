package wust.webUtil.macros

import outwatch.dom.Key

import scala.reflect.macros.blackbox.Context

object KeyHashMacro {
  def keyValue(c: Context): c.Expr[Int] = {
    import c.universe._

    val pos = c.enclosingPosition
    val hash = List(pos.source.path, pos.line, pos.column).hashCode()

    c.Expr[Int](q"$hash")
  }
  def keyValueWith(c: Context)(values: c.Expr[Any]*): c.Expr[Int] = {
    import c.universe._

    val pos = c.enclosingPosition
    val hash = List(pos.source.path, pos.line, pos.column).hashCode()

    c.Expr[Int](q"_root_.scala.runtime.Statics.mix($hash, ${values.toList}.hashCode())")
  }
  def key(c: Context): c.Expr[Key] = {
    import c.universe._

    val pos = c.enclosingPosition
    val hash = List(pos.source.path, pos.line, pos.column).hashCode()

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := $hash")
  }
  def keyWith(c: Context)(values: c.Expr[Any]*): c.Expr[Key] = {
    import c.universe._

    val pos = c.enclosingPosition
    val hash = List(pos.source.path, pos.line, pos.column).hashCode()

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
