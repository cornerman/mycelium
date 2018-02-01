package mycelium.client

import mycelium.core.message._

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.{ ExecutionContext, Promise }

class CallRequests[T] {
  private val openRequests = new ConcurrentHashMap[SequenceId, Promise[T]]
  private val idSequence = new AtomicInteger(0)

  def open()(implicit ctx: ExecutionContext): (SequenceId, Promise[T]) = {
    val promise = Promise[T]()
    val seqId = idSequence.incrementAndGet()
    openRequests.put(seqId, promise)
    promise.future.onComplete { _ => openRequests.remove(seqId) }

    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = Option(openRequests.get(seqId))
}
