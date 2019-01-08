package wust.util.macros

import scala.reflect.macros.blackbox.Context

// This macro is for expanding a class type into a list of objects that extend this class.
// Example:
// sealed trait Person
// object Female extends Person
// object Male extends Person
// val persons: List[Person] = macro SubObject.list[Person] // List(Female, Male)

object SubObjects {
  def list[Trait]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait]): c.Expr[List[Trait]] = {

    import c.universe._

    //TODO abort if not sealed
    def recursiveSubObjects(sym: Symbol): List[Tree] = sym match {
      case sym if sym.isClass && sym.asClass.isSealed =>
        sym.asClass.knownDirectSubclasses.flatMap(recursiveSubObjects(_)).toList
      case sym if sym.isClass && sym.asClass.isModuleClass =>
        //TODO: use fqn of object
        q"${sym.name.toTermName}" :: Nil
      case _ => Nil
    }

    val subObjects = recursiveSubObjects(traitTag.tpe.typeSymbol)
    c.Expr[List[Trait]](q"_root_.scala.List(..$subObjects)")
  }
}
