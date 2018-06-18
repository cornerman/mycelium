package mycelium.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import mycelium.core.message._

import scala.concurrent.{Future, Promise, ExecutionContext}

class RequestMap[T] {
  private val openRequests = new ConcurrentHashMap[SequenceId, Promise[T]]
  private val idSequence = new AtomicInteger(0)

  def open()(implicit ec: ExecutionContext): (SequenceId, Promise[T]) = {
    val promise = Promise[T]()
    val seqId = idSequence.incrementAndGet()
    openRequests.put(seqId, promise)
    promise.future.onComplete { _ => openRequests.remove(seqId) }

    seqId -> promise
  }

  def get(seqId: SequenceId): Option[Promise[T]] = Option(openRequests.get(seqId))

  def cancelAllRequests(): Unit = openRequests.keySet().forEach(new java.util.function.Consumer[SequenceId] {
    def accept(key: SequenceId): Unit = {
      val removed = openRequests.remove(key)
      if (removed != null) removed tryFailure RequestException.Canceled
    }
  })
}
