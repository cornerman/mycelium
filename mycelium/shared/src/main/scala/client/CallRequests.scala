package mycelium.client

import mycelium.core.message._

import java.util.{ Timer, TimerTask }
import scala.concurrent.{ ExecutionContext, Promise }

case object TimeoutException extends Exception

class OpenRequests[T](timeoutMillis: Int) {
  import collection.mutable

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[T]]

  private val nextSeqId: () => SequenceId = {
    var seqId = 0
    () => { seqId += 1; seqId }
  }

  def open()(implicit ctx: ExecutionContext): (SequenceId, Promise[T]) = {
    val promise = Promise[T]()
    val seqId = nextSeqId()
    openRequests += seqId -> promise
    promise.future.onComplete { res =>
      openRequests -= seqId
    }

    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = openRequests.get(seqId)

  def startTimeout(promise: Promise[T])(implicit ctx: ExecutionContext): Unit = {
    val timer = new Timer
    val task = new TimerTask {
      def run(): Unit = {
        promise tryFailure TimeoutException
        ()
      }
    }

    timer.schedule(task, timeoutMillis)
    promise.future.onComplete { _ =>
      timer.cancel()
      timer.purge()
    }
  }
}
