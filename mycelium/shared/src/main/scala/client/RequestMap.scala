package mycelium.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import monix.execution.Scheduler
import monix.reactive.Observer
import monix.reactive.subjects.{ReplaySubject, Subject}
import mycelium.core.message._

class RequestMap[T] {
  private val openRequests = new ConcurrentHashMap[SequenceId, Observer[T]]
  private val idSequence = new AtomicInteger(0)

  def open()(implicit scheduler: Scheduler): (SequenceId, Subject[T,T]) = {
    val subject = ReplaySubject[T]()
    val seqId = idSequence.incrementAndGet()
    openRequests.put(seqId, subject)
    subject.completedL.runAsync.onComplete { _ => openRequests.remove(seqId) }

    seqId -> subject
  }

  def get(seqId: SequenceId): Option[Observer[T]] = Option(openRequests.get(seqId))
}
