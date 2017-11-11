package mycelium.util

class BufferedFunction[T](operation: T => Boolean) extends (T => Unit) {
  private var queue = List.empty[T]

  def apply(value: T): Unit = queue = value :: queue
  def flush(): Unit = queue = queue.reverse.dropWhile(operation).reverse
}
object BufferedFunction {
  def apply[T](f: T => Boolean): BufferedFunction[T] = new BufferedFunction(f)
}
