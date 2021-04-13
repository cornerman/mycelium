package mycelium.core

trait Cancelable {
  def cancel(): Unit
}

object Cancelable {
  val empty = new Cancelable {
    def cancel() = ()
  }

  def apply(f: () => Unit) = new Cancelable {
    private var isCancelled = false
    def cancel() = if (!isCancelled) {
      isCancelled = true
      f()
    }
  }
}
