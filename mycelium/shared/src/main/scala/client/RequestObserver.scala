package mycelium.client

import monix.execution.Ack
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import mycelium.core.message._

import scala.concurrent.{Future, Promise}

class RequestObserver[Payload, Failure](promise: Promise[Either[Failure, Observable[Payload]]]) extends Observer[ServerMessage[Payload, Failure] with ServerResponse] {
  private var subject: Option[PublishSubject[Payload]] = None

  override def onError(ex: Throwable): Unit = subject.foreach(_.onError(ex))
  override def onComplete(): Unit = subject.foreach(_.onComplete())

  override def onNext(elem: ServerMessage[Payload, Failure] with ServerResponse): Future[Ack] = subject match {
    case None => elem match {
      case SingleResponse(_, result) =>
        promise trySuccess Right(Observable(result))
        Ack.Stop
      case StreamResponse(_, result) =>
        val newSubject = PublishSubject[Payload]()
        promise trySuccess Right(newSubject)
        subject = Some(newSubject)
        newSubject.onNext(result)
      case StreamCloseResponse(_) =>
        promise trySuccess Right(Observable())
        Ack.Stop
      case FailureResponse(_, msg) =>
        promise trySuccess Left(msg)
        Ack.Stop
      case ErrorResponse(_) =>
        promise tryFailure RequestException.ErrorResponse
        Ack.Stop
    }
    case Some(subject) => elem match {
      case StreamResponse(_, result) =>
        subject.onNext(result)
      case StreamCloseResponse(_) =>
        subject.onComplete()
        Ack.Stop
      case ErrorResponse(_) =>
        subject.onError(RequestException.ErrorResponse)
        Ack.Stop
      case response =>
        subject.onError(RequestException.IllegalResponse(response))
        Ack.Stop
    }
  }
}
