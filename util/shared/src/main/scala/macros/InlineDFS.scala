package macros

import scala.reflect.macros.blackbox.Context

object InlineDFSMacro {
  def foo0(c: Context)(bar: c.Expr[(() => Boolean) => Boolean]): c.Expr[Boolean] = {
    import c.universe._

    c.Expr[Boolean] {
      q"""
      println("sideeffect")
      if(${bar.tree}(() => false)) 5 else 7
      ${bar.tree}(() => false)
       """
    }

    //TODO: how to fix error message with reifee and splice?
//    reify[Boolean] {
//      println("sideeffect")
//      if(bar.value(() => false)) 5 else 7
//      bar.value(() => false)
//    }
  }
}

trait InlineDFS {
  def foo0(
    bar: (() => Boolean) => Boolean,
  ):Boolean = macro InlineDFSMacro.foo0

  @inline def foo1(
    bar: (() => Boolean) => Boolean,
  ):Boolean = {
    println("sideeffect")
    bar(() => false) && bar(() => false)
  }

  @inline def foo2(
    bar: (() => Boolean) => Boolean,
  ):Boolean = {
    println("sideeffect")
    if(bar(() => false)) 5 else 7
    bar(() => false)
  }
}
object InlineDFS extends InlineDFS

