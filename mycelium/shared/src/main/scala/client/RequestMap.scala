package mycelium.client

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import mycelium.core.message._
import monix.reactive.subjects.{PublishSubject, Subject}
import monix.eval.Task
import monix.execution.Scheduler

class RequestMap[T] {
  private val openRequests = new ConcurrentHashMap[SequenceId, Subject[T,T]]
  private val idSequence = new AtomicInteger(0)

  def open()(implicit scheduler: Scheduler): (SequenceId, Subject[T,T]) = {
    val subject = PublishSubject[T]()
    val seqId = idSequence.incrementAndGet()
    openRequests.put(seqId, subject)
    subject.completedL.runOnComplete { _ =>
      openRequests.remove(seqId)
    }

    seqId -> subject
  }

  def get(seqId: SequenceId): Option[Subject[T,T]] = Option(openRequests.get(seqId))

  def cancelAllRequests(): Unit = openRequests.values.forEach(new java.util.function.Consumer[Subject[T, T]] {
    def accept(subject: Subject[T,T]): Unit = subject onError RequestException.Canceled
  })
}
