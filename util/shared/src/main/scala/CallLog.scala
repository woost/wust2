package wust.util

import scala.meta._
import scala.collection.immutable.Seq

object CallLog {
  def methodWithLogs(typeName: Type.Name, method: Defn.Def, logger: Term): Defn.Def = {
    val identifier = s"${typeName.toString}.${method.name.toString}"
    val paramValues = method.paramss.headOption.toList.flatten
      .map(param => Term.Name(param.name.toString))

    val paramTerm = paramValues match {
      case Nil => q"()"
      case value :: Nil => q""""(" + $value + ")""""
      case values => q"(..$values)"
    }

    val resultVar = Term.fresh()
    val resultVarParam = Term.Param(List.empty, resultVar, None, Some(resultVar))

    val logEntry = q"""$logger("entry: " + $identifier.+($paramTerm))"""
    val syncLogExit = q"""$logger("exit: " + $identifier + " = " + $resultVar)"""
    val logExit = method.decltpe match {
      case Some(t"Future[$_]") => q"$resultVar.foreach(($resultVarParam) => $syncLogExit)"
      case _ => syncLogExit
    }

    val newBody = q"""
      $logEntry;
      val ${Pat.Var.Term(resultVar)}: ${method.decltpe} = ${method.body};
      $logExit;
      $resultVar
    """
    method.copy(body = newBody)
  }

  def templateWithLogs(name: Type.Name, templ: Template, logger: Term): Template = {
    val methods = templ.stats.toSeq.flatten
    val newMethods = methods.map {
      case method: Defn.Def => methodWithLogs(name, method, logger)
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

    defn match {
      case t: Defn.Trait => t.copy(templ = templateWithLogs(t.name, t.templ, logger))
      case c: Defn.Class => c.copy(templ = templateWithLogs(c.name, c.templ, logger))
      case _ => abort(s"unexpected annotation: $defn")
    }
  }
}
