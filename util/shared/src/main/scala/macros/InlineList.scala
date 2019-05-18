package wust.util.macros

import scala.reflect.macros.blackbox.Context

// These macros are for writing a chain of conditions in a short way with
// the list api without the overhead of allocating a list at runtime.
// Usage like: InlineList.contains(1,2,3)(3) // true

object InlineListMacro {
  def contains[T](c: Context)(values: c.Expr[T]*)(t: c.Expr[T]): c.Expr[Boolean] = {
    import c.universe._

    val tree = values.foldLeft[Tree](q"false")((tree, expr) => q"$tree || ${t.tree} == ${expr.tree}")
    c.Expr[Boolean](tree)
  }
  def exists[T](c: Context)(values: c.Expr[T]*)(f: c.Expr[T => Boolean]): c.Expr[Boolean] = {
    import c.universe._

    val tree = values.foldLeft[Tree](q"false")((tree, expr) => q"$tree || ${f.tree}(${expr.tree})")
    c.Expr[Boolean](tree)
  }
  def forall[T](c: Context)(values: c.Expr[T]*)(f: c.Expr[T => Boolean]): c.Expr[Boolean] = {
    import c.universe._

    val tree = values.foldLeft[Tree](q"true")((tree, expr) => q"$tree && ${f.tree}(${expr.tree})")
    c.Expr[Boolean](tree)
  }
  def foldLeft[T,R](c: Context)(values: c.Expr[T]*)(z: c.Expr[R])(f: c.Expr[(R, T) => R]): c.Expr[R] = {
    import c.universe._

    val tree = values.foldLeft[Tree](z.tree)((tree, expr) => q"${f.tree}($tree, ${expr.tree})")
    c.Expr[R](tree)
  }
  def foreach[T](c: Context)(values: c.Expr[T]*)(f: c.Expr[T => Unit]): c.Expr[Unit] = {
    import c.universe._

    val tree = values.foldLeft[Tree](q"()")((tree, expr) => q"$tree; ${f.tree}(${expr.tree})")
    c.Expr[Unit](tree)
  }
}

trait InlineList {
  def contains[T](values: T*)(t: T): Boolean = macro InlineListMacro.contains[T]
  def exists[T](values: T*)(f: T => Boolean): Boolean = macro InlineListMacro.exists[T]
  def forall[T](values: T*)(f: T => Boolean): Boolean = macro InlineListMacro.forall[T]
  def foldLeft[T, R](values: T*)(z: R)(f: (R, T) => R): R = macro InlineListMacro.foldLeft[T,R]
  def foreach[T](values: T*)(f: T => Unit): Unit = macro InlineListMacro.foreach[T]
}
object InlineList extends InlineList

