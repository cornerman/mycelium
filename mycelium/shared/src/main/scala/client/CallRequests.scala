package mycelium.client

import mycelium.core.message._

import java.util.{ Timer, TimerTask }
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ ExecutionContext, Promise }
import scala.concurrent.duration.FiniteDuration

case object TimeoutException extends Exception
case object DroppedMessageException extends Exception

class CallRequests[T] {
  private val openRequests = new ConcurrentHashMap[SequenceId, Promise[T]]

  private val sequence = new AtomicInteger(0)

  def open()(implicit ctx: ExecutionContext): (SequenceId, Promise[T]) = {
    val promise = Promise[T]()
    val seqId = sequence.incrementAndGet()
    openRequests.put(seqId, promise)
    promise.future.onComplete { _ =>
      openRequests.remove(seqId)
    }

    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = Option(openRequests.get(seqId))

  def drop(promise: Promise[T]): Unit = {
    promise tryFailure DroppedMessageException
    ()
  }

  def startTimeout(promise: Promise[T], timeout: FiniteDuration)(implicit ctx: ExecutionContext): Unit = {
    val timer = new Timer
    val task = new TimerTask {
      def run(): Unit = {
        promise tryFailure TimeoutException
        ()
      }
    }

    timer.schedule(task, timeout.toMillis)
    promise.future.onComplete { _ =>
      timer.cancel()
      timer.purge()
    }
  }
}
