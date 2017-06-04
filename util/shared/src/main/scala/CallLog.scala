package wust.util

import scala.meta._
import scala.collection.immutable.Seq

object CallLog {
  def valueWithLogs(typeName: Type.Name, value: Defn.Val, logger: Term, accTimeVar: Term.Name): Defn.Val = {
    val identifier = s"${typeName.toString}.${value.pats.mkString(",")}"

    val resultVar = Term.fresh()
    val timerVar = Term.fresh()
    val resultVarParam = Term.Param(List.empty, resultVar, None, Some(resultVar))

    // val logEntry = q"""$logger("entry: " + $identifier)"""
    val syncLogExit = List(q"""$accTimeVar($identifier) += $timerVar.passed""" ,q"""$logger("call: " + $identifier + " " + ($accTimeVar($identifier) / 1000) + "us, " + ($accTimeVar.maxBy(_._2)))""")
    val logExit = value.decltpe match {
      case Some(t"Future[$_]") => List(q"$resultVar.foreach(($resultVarParam) => {..$syncLogExit})")
      case _ => syncLogExit
    }

    val newBody = q"""
      import wust.util.time.StopWatch
      import ${Term.Name(typeName.value)}._
      val ${Pat.Var.Term(timerVar)} = StopWatch.started
      val ${Pat.Var.Term(resultVar)}: ${value.decltpe} = {${value.rhs}};
      ..$logExit;
      $resultVar
    """
    value.copy(rhs = newBody)
  }

  def methodWithLogs(typeName: Type.Name, method: Defn.Def, logger: Term, accTimeVar: Term.Name): Defn.Def = {
    val identifier = s"${typeName.toString}.${method.name.toString}"
    val paramValues = method.paramss.headOption.toList.flatten
      .map(param => Term.Name(param.name.toString))

    val paramTerm = paramValues match {
      case Nil => q"()"
      case value :: Nil => q""""(" + $value + ")""""
      case values => q"(..$values)"
    }

    val resultVar = Term.fresh()
    val timerVar = Term.fresh()
    val resultVarParam = Term.Param(List.empty, resultVar, None, Some(resultVar))

    // val logEntry = q"""$logger("entry: " + $identifier.+($paramTerm))"""
    val syncLogExit = List(q"""$accTimeVar($identifier) += $timerVar.passed""" ,q"""$logger("call: " + $identifier.+($paramTerm) + " " + ($accTimeVar($identifier) / 1000) + "us, " + ($accTimeVar.maxBy(_._2)))""")
    val logExit = method.decltpe match {
      case Some(t"Future[$_]") => List(q"$resultVar.foreach(($resultVarParam) => {..$syncLogExit})")
      case _ => syncLogExit
    }

    val newBody = q"""
      import wust.util.time.StopWatch
      import ${Term.Name(typeName.value)}._
      val ${Pat.Var.Term(timerVar)} = StopWatch.started
      val ${Pat.Var.Term(resultVar)}: ${method.decltpe} = {${method.body}}
      ..$logExit
      $resultVar
    """
    method.copy(body = newBody)
  }

  def templateWithLogs(name: Type.Name, templ: Template, logger: Term, accTimeVar: Term.Name): Template = {
    val methods = templ.stats.toSeq.flatten
    val newMethods = methods.map {
      case method: Defn.Def => methodWithLogs(name, method, logger, accTimeVar)
      case value: Defn.Val => valueWithLogs(name, value, logger, accTimeVar)
      case other => other
    }

    templ.copy(stats = Some(Seq(newMethods: _*)))
  }
}

class callLog extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    import CallLog._

    val Term.New(Template(_, Seq(Term.Apply(_, args)), _, _)) = this
    val logger = args match {
      case (term: Term) :: Nil => term
      case arg => abort(s"unexpected argument: ${arg.head.getClass}")
    }

    val accTimeVar = Term.fresh()
    val accTimeDef = q""" var ${Pat.Var.Term(accTimeVar)} = collection.mutable.HashMap.empty[String,Long].withDefaultValue(0L)"""

    val x = defn match {
      case t: Defn.Trait => q"${t.copy(templ = templateWithLogs(t.name, t.templ, logger, accTimeVar))}; object ${Term.Name(t.name.value)} { $accTimeDef }"
      case t: Defn.Class => q"${t.copy(templ = templateWithLogs(t.name, t.templ, logger, accTimeVar))}; object ${Term.Name(t.name.value)} { $accTimeDef }"
      case d => d.children match {
        case (t: Defn.Trait) :: (o: Defn.Object) :: Nil => q"${t.copy(templ = templateWithLogs(t.name, t.templ, logger, accTimeVar))}; ${o.copy(templ = o.templ.copy(stats = o.templ.stats.map(_ ++ Seq(accTimeDef))))}"
        case (t: Defn.Class) :: (o: Defn.Object) :: Nil => q"${t.copy(templ = templateWithLogs(t.name, t.templ, logger, accTimeVar))}; ${o.copy(templ = o.templ.copy(stats = o.templ.stats.map(_ ++ Seq(accTimeDef))))}"
        case _ => abort(s"unexpected annotation: $defn")




        /// wir machen zum beispiel einzelne events fuer jeden graphchange im graphchanges class. das sollte man einen event schicken, und einmal graph + machen. sone art graphchanges als event schicken einfach.

      }
    }
    // println(x.syntax)
    x
  }
}
