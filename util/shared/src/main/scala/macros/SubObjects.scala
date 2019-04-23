package wust.util.macros

import scala.reflect.macros.blackbox.Context

// This macro is for expanding a class type into a list of objects that extend this class.
// Example:
// sealed trait Person
// object Female extends Person
// object Male extends Person
// val persons: List[Person] = macro SubObject.list[Person] // List(Female, Male)

object SubObjectsMacro {
  def all[Trait]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait]): c.Expr[Array[Trait]] = {

    import c.universe._
    val utils = MacroUtils(c)

    //TODO abort if not sealed
    def recursiveSubObjects(sym: Symbol): List[Tree] = sym match {
      case sym if sym.isClass && sym.asClass.isSealed =>
        sym.asClass.knownDirectSubclasses.flatMap(recursiveSubObjects(_)).toList
      case sym if sym.isClass && sym.asClass.isModuleClass =>
        utils.fullNameTree(sym) :: Nil
      case _ => Nil
    }

    val subObjects = recursiveSubObjects(traitTag.tpe.typeSymbol)
    c.Expr[Array[Trait]](q"Array(..$subObjects)")
  }
}

object SubObjects {
  def all[Trait]: Array[Trait] = macro SubObjectsMacro.all[Trait]
}
