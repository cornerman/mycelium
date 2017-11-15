package mycelium.client

import mycelium.core.message._

import scala.concurrent.{ ExecutionContext, Promise }

case object TimeoutException extends Exception

object TimeoutPromise {
  import java.util.{ Timer, TimerTask }

  def apply[T](timeoutMillis: Int)(implicit ctx: ExecutionContext): Promise[T] = {
    val promise = Promise[T]()

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
    }

    promise
  }
}

class OpenRequests[T](timeoutMillis: Int) {
  import collection.mutable

  private val openRequests = mutable.HashMap.empty[SequenceId, Promise[T]]

  private val nextSeqId: () => SequenceId = {
    var seqId = 0
    () => { seqId += 1; seqId }
  }

  def open()(implicit ctx: ExecutionContext): (SequenceId, Promise[T]) = {
    // val stopwatch = StopWatch.started //TODO stopwatch?
    val promise = TimeoutPromise[T](timeoutMillis)
    val seqId = nextSeqId()
    openRequests += seqId -> promise
    promise.future onComplete { res =>
      openRequests -= seqId
      // scribe.warn(s"Request $seqId: ${stopwatch.readMillis}ms")
      res.failed.foreach(err => scribe.error(s"Request $seqId failed: $err"))
    }

    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = openRequests.get(seqId)
}
