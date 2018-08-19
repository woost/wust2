package wust.util.macros

import scala.reflect.macros.blackbox.Context

object SubObjects {
  def list[Trait]
    (c: Context)
    (implicit traitTag: c.WeakTypeTag[Trait]): c.Expr[List[Trait]] = {

    import c.universe._

    def recursiveSubObjects(sym: Symbol): List[Tree] = sym match {
      case sym if sym.isClass && sym.asClass.isSealed =>
        sym.asClass.knownDirectSubclasses.flatMap(recursiveSubObjects(_)).toList
      case sym if sym.isClass && sym.asClass.isModuleClass =>
        q"${sym.name.toTermName}" :: Nil
      case _ => Nil
    }

    val subObjects = recursiveSubObjects(traitTag.tpe.typeSymbol)
    c.Expr[List[Trait]](q"List(..$subObjects)")
  }
}
