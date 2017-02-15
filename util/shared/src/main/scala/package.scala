
package object util {
  implicit class Pipe[T](val v: T) extends AnyVal {
    def |>[U] (f: T => U): U = f(v)
    def ||>[U](f: T => U): T = {f(v); v}
  }
}
