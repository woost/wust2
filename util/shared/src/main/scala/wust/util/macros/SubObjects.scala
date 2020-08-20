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
    def recursiveSubObjects(sym: Symbol): Set[Symbol] = sym match {
      case sym if sym.isClass && sym.asClass.isSealed =>
        sym.asClass.knownDirectSubclasses.flatMap(recursiveSubObjects(_))
      case sym if sym.isClass && sym.asClass.isModuleClass =>
        Set(sym)
      case _ => Set.empty
    }

    val subObjects: Array[Tree] = recursiveSubObjects(traitTag.tpe.typeSymbol).map(utils.fullNameTree).toArray
    c.Expr[Array[Trait]](q"..$subObjects")
/* TODO: generates too much runtime code:
  var array = [$m_Lwust_webApp_state_Feature$TaskUnchecked$(), $m_Lwust_webApp_state_Feature$TaskChecked$(), $m_Lwust_webApp_state_Feature$TaskReordered$()];
  var xs = new $c_sjs_js_WrappedArray().init___sjs_js_Array(array);
  var len = $uI(xs.array$6.length);
  var array$1 = $newArrayObject($d_Lwust_webApp_state_Feature.getArrayOf(), [len]);
  var elem$1 = 0;
  elem$1 = 0;
  var this$9 = new $c_sc_IndexedSeqLike$Elements().init___sc_IndexedSeqLike__I__I(xs, 0, $uI(xs.array$6.length));
  while (this$9.hasNext__Z()) {
    var arg1 = this$9.next__O();
    array$1.set(elem$1, arg1);
    elem$1 = ((1 + elem$1) | 0)
  };
  this.all$1 = array$1;
  this.bitmap$init$0$1 = (((16 | this.bitmap$init$0$1) << 24) >> 24);
  var this$11 = $m_s_Console$();
  var this$12 = $as_Ljava_io_PrintStream(this$11.outVar$2.v$1);
 */
  }
}

object SubObjects {
  def all[Trait]: Array[Trait] = macro SubObjectsMacro.all[Trait]
}
