package wust.util.algorithm

import flatland._

import scala.collection.mutable

package object dfs {
  // Variations: (only needed combinations are implemented)
  // unfiltered | filtered
  // processResult => follow everything | global stop | skip branch
  // toArray | manual append | exists

  // mode selection helpers
  @inline def withStart(start: Int, foreachSuccessor: (Int, Int => Unit) => Unit, stack: ArrayStackInt, visited: ArraySet): Unit = {
    visited += start
    stack.push(start)
  }
  @inline def afterStart(start: Int, foreachSuccessor: (Int, Int => Unit) => Unit, stack: ArrayStackInt, visited: ArraySet): Unit = {
    foreachSuccessor(start, { next =>
      visited += next
      stack.push(next)
    })
  }
  @inline def withoutStart(start: Int, foreachSuccessor: (Int, Int => Unit) => Unit, stack: ArrayStackInt, visited: ArraySet): Unit = {
    visited += start
    foreachSuccessor(start, { next =>
      visited += next
      stack.push(next)
    })
  }
  type StartMode = (Int, (Int, Int => Unit) => Unit, ArrayStackInt, ArraySet) => Unit

  // @inline inlines lambda parameters
  @inline def toArray(
    starts: (Int => Unit) => Unit,
    startMode: StartMode,
    successors: NestedArrayInt
  ): Array[Int] = {
    val builder = new mutable.ArrayBuilder.ofInt
    successors.depthFirstSearchGeneric(
      init = (stack, visited) => starts(start => startMode(start, successors.foreachElement(_)(_), stack, visited)),
      processVertex = builder += _
    )
    builder.result()
  }

  // @inline inlines lambda parameters
  // stops whole traversal if append returns false
  @inline def withManualAppend(
    starts: (Int => Unit) => Unit,
    startMode: StartMode,
    successors: NestedArrayInt,
    append: Int => Unit
  ): Unit = {
    successors.depthFirstSearchGeneric(
      init = (stack, visited) => starts(start => startMode(start, successors.foreachElement(_)(_), stack, visited)),
      processVertex = append
    )
  }

  // @inline inlines lambda parameters
  // stops only traversing local branch
  @inline def withContinue(
    starts: (Int => Unit) => Unit,
    startMode: StartMode,
    successors: NestedArrayInt,
    continue: Int => Boolean
  ): Unit = {
    successors.depthFirstSearchGeneric[Boolean](
      init = (stack, visited) => starts(start => startMode(start, successors.foreachElement(_)(_), stack, visited)),
      processVertex = continue,
      advanceGuard = (aggResult, advance) => if (aggResult) advance()
    )
  }

  // @inline inlines lambda parameters
  @inline def withManualAppendStopIfAppendFalse(
    start: Int,
    successors: NestedArrayInt,
    continue: Int => Boolean
  ): Unit = {
    var running = true
    successors.depthFirstSearchGeneric(
      init = (stack, _) => stack.push(start),
      processVertex = { elem =>
        if (!continue(elem)) running = false
      },
      loopConditionGuard = condition => running && condition()
    )
  }

  // @inline inlines lambda parameters
  @inline def withManualAppendSkipIfAppendFalse(
    start: Int,
    successors: NestedArrayInt,
    continue: Int => Boolean
  ): Unit = {
    successors.depthFirstSearchGeneric[Boolean](
      init = (stack, _) => stack.push(start),
      processVertex = continue,
      advanceGuard = (aggResult, advance) => if (aggResult) advance()
    )
  }

  @inline def exists(
    starts: (Int => Unit) => Unit,
    startMode: StartMode,
    successors: NestedArrayInt,
    isIncluded: Int => Boolean = _ => true,
    isFound: Int => Boolean
  ): Boolean = {
    var notFound = true
    successors.depthFirstSearchGeneric(
      init = { (stack, visited) =>
        starts(start =>
          startMode(
            start,
            (idx, f) => successors.foreachElement(idx)(elem => if (isIncluded(elem)) f(elem)),
            stack,
            visited
          ))
      },
      processVertex = elem => if (isFound(elem)) notFound = false,
      loopConditionGuard = condition => notFound && condition(),
      enqueueGuard = (elem, enqueue) => if (isIncluded(elem)) enqueue()
    )
    !notFound
  }

  // @inline inlines lambda parameters
  @inline def withManualSuccessors(
    starts: (Int => Unit) => Unit,
    size: Int,
    successors: Int => (Int => Unit) => Unit,
    processVertex: Int => Unit
  ): Unit = {
    flatland.depthFirstSearchGeneric(
      vertexCount = size,
      foreachSuccessor = (idx, f) => successors(idx)(f),
      init = (stack, _) => starts(stack.push),
      processVertex = processVertex,
    )
  }

  @deprecated("This is the old, slow version of DFS", "")
  def withStartInCycleDetection[V](start: V, continue: V => Iterable[V]) = new Iterable[V] {
    private var _startInvolvedInCycle = false
    def startInvolvedInCycle = _startInvolvedInCycle

    def iterator = new Iterator[V] {

      val stack = mutable.Stack(start)
      val onStack = mutable.Set[V]()
      val seen = mutable.Set[V]()

      override def hasNext: Boolean = stack.nonEmpty
      override def next: V = {
        val current = stack.pop
        onStack -= current
        seen += current

        for (candidate <- continue(current)) {
          if (candidate == start) _startInvolvedInCycle = true
          if (!seen(candidate) && !onStack(candidate)) {
            stack push candidate
            onStack += candidate
          }
        }

        current
      }
    }
    iterator.size // consume iterator
  }

}
