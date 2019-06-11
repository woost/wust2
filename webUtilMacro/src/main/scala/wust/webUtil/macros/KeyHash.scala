package wust.webUtil.macros

import java.security.MessageDigest

import outwatch.dom.Key

import scala.reflect.macros.blackbox.Context

// These macros are for efficiently injecting location-aware keys into our outwatch code.
// The key is an integer and is derived as the hashcode of the filepath, line and column of the source code.
// Usage.like:
//  - (mostly md5) unique key hashes for use with thunks:
//             KeyHash.uniqueKey               // generate a md5-hash of the location in the source code of this function call at compile time
//             KeyHash.uniqueKey(stringA)      // append stringA to the location-based hash
//             KeyHash.uniqueKeyed             // shortcut for `dsl.key := <hashCode of source location>`
//             KeyHash.uniqueKeyed(varA, varB) // shortcut for `dsl.key := <hashCode of source location mixed with hashCode of varA and varB>`
//
//  - faster but not necessarily unique, because using hashCode, as long as not used with thunks that is enough
//             KeyHash.keyed                   // `dsl.key := <hashCode of source location>`
//             KeyHash.keyed(varA, varB, ...)  // `dsl.key := <hashCode of source location mixed with hashCode of varA, varB, ...>`

object KeyHashMacro {
  private val digester = MessageDigest.getInstance("MD5")

  private def positionHashCode(c: Context): Int = {
    val pos = c.enclosingPosition
    List(pos.source.path, pos.line, pos.column).hashCode()
  }
  private def uniquePositionHash(c: Context): String = {
    val pos = c.enclosingPosition
    val posStr = pos.source.path + ":" + pos.line + "-" + pos.column
    new String(digester.digest(posStr.getBytes("UTF-8")), "UTF-8")
  }

  def uniqueKey(c: Context): c.Expr[String] = {
    import c.universe._

    val hash = uniquePositionHash(c)

    c.Expr[String](q"$hash")
  }
  def uniqueKeyWith(c: Context)(value: c.Expr[String]): c.Expr[String] = {
    import c.universe._

    val hash = uniquePositionHash(c) + ":"

    c.Expr[String](q"$hash + $value")
  }
  def uniqueKeyed(c: Context): c.Expr[Key] = {
    import c.universe._

    val hash = uniquePositionHash(c)

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := $hash")
  }
  def uniqueKeyedWith(c: Context)(value: c.Expr[String]): c.Expr[Key] = {
    import c.universe._

    val hash = uniquePositionHash(c) + ":"

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := $hash + $value")
  }
  def keyed(c: Context): c.Expr[Key] = {
    import c.universe._

    val hash = positionHashCode(c)

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := $hash")
  }
  def keyedWith(c: Context)(value: c.Expr[Any]): c.Expr[Key] = {
    import c.universe._

    val hash = positionHashCode(c)

    c.Expr[Key](q"_root_.outwatch.dom.dsl.key := _root_.scala.runtime.Statics.mix($hash, $value.hashCode())")
  }
}

trait KeyHash {
  def uniqueKey: String = macro KeyHashMacro.uniqueKey
  def uniqueKey(value: String): String = macro KeyHashMacro.uniqueKeyWith
  def uniqueKeyed: Key = macro KeyHashMacro.uniqueKeyed
  def uniqueKeyed(value: String): Key = macro KeyHashMacro.uniqueKeyedWith
  def keyed: Key = macro KeyHashMacro.keyed
  def keyed(value: Any): Key = macro KeyHashMacro.keyedWith
}
object KeyHash extends KeyHash
