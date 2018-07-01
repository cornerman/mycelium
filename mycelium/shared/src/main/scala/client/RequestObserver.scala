package mycelium.client

import monix.execution.Ack
import monix.reactive.subjects.PublishSubject
import monix.reactive.{Observable, Observer}
import mycelium.core.EventualResult
import mycelium.core.message._

import scala.concurrent.{Future, Promise}

class RequestObserver[Payload, ErrorType](promise: Promise[EventualResult[Payload, ErrorType]]) extends Observer[ServerMessage[Payload, ErrorType] with ServerResponse] {
  private var subjectOpt: Option[PublishSubject[Payload]] = None

  override def onError(ex: Throwable): Unit = subjectOpt.foreach(_.onError(ex))
  override def onComplete(): Unit = subjectOpt.foreach(_.onComplete())

  override def onNext(elem: ServerMessage[Payload, ErrorType] with ServerResponse): Future[Ack] = subjectOpt match {
    case None => elem match {
      case SingleResponse(_, result) =>
        promise trySuccess EventualResult.Single(result)
        Ack.Stop
      case StreamResponse(_, result) =>
        val subject = PublishSubject[Payload]()
        promise trySuccess EventualResult.Stream(subject)
        subjectOpt = Some(subject)
        subject.onNext(result)
      case StreamCloseResponse(_) =>
        promise trySuccess EventualResult.Stream(Observable.empty)
        Ack.Stop
      case ErrorResponse(_, msg) =>
        promise trySuccess EventualResult.Error(msg)
        Ack.Stop
      case ExceptionResponse(_) =>
        promise tryFailure RequestException.ExceptionResponse
        Ack.Stop
    }
    case Some(subject) => elem match {
      case StreamResponse(_, result) =>
        subject.onNext(result)
      case StreamCloseResponse(_) =>
        subject.onComplete()
        Ack.Stop
      case ExceptionResponse(_) =>
        subject.onError(RequestException.ExceptionResponse)
        Ack.Stop
      case response =>
        subject.onError(RequestException.IllegalResponse(response))
        Ack.Stop
    }
  }
}
