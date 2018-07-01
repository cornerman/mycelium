package mycelium.client

import cats.effect.Concurrent
import chameleon._
import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import monix.eval.Task
import mycelium.core.message._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

case class WebsocketClientConfig(minReconnectDelay: FiniteDuration = 1.seconds, maxReconnectDelay: FiniteDuration = 60.seconds, delayReconnectFactor: Double = 1.3, connectingTimeout: FiniteDuration = 5.seconds, pingInterval: FiniteDuration = 45.seconds)
case class WebsocketObservable(connected: Observable[Unit], disconnected: Observable[Unit])

class WebsocketClientWithPayload[PickleType, Payload, Failure](
  wsConfig: WebsocketClientConfig,
  ws: WebsocketConnection[PickleType],
  requestMap: RequestMap[Either[Failure, Observable[Payload]]])(implicit
  scheduler: Scheduler,
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, Failure], PickleType]) {

  private object subjects {
    val connected = ConcurrentSubject.publish[Unit]
    val disconnected = ConcurrentSubject.publish[Unit]
    val incomingMessages = ConcurrentSubject.publish[Future[Option[PickleType]]]
    val outgoingMessages = ConcurrentSubject.publish[WebsocketMessage[PickleType]]
  }

  private val responseMessages: Observable[ServerMessage[Payload, Failure] with ServerResponse] = subjects.incomingMessages.concatMap { rawMessage =>
    Observable.fromFuture(rawMessage).flatMap {
      case Some(value) => deserializer.deserialize(value) match {
        case Right(response) => response match {
          case response: ServerResponse => Observable(response)
          case Pong => Observable.empty
        }
        case Left(error) =>
          scribe.warn(s"Ignoring message ($value). Deserializer failed: $error")
          Observable.empty
      }
      case None =>
        scribe.warn(s"Ignoring websocket message. Builder does not support message")
        Observable.empty
    }.onErrorHandleWith { t =>
      scribe.warn(s"Ignoring websocket message. Builder failed to unpack message: $t")
      Observable.empty
    }
  }

  val _ = responseMessages.groupBy(_.seqId).subscribe { observable =>
    val seqId = observable.key
    requestMap.get(seqId) match {
      case Some(promise) =>
        val _ = observable.subscribe(new Observer[ServerMessage[Payload, Failure] with ServerResponse] {
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
              case StreamCloseResponse(seqId) =>
                promise trySuccess Right(Observable())
                Ack.Stop
              case FailureResponse(seqId, msg) =>
                promise trySuccess Left(msg)
                Ack.Stop
              case ErrorResponse(seqId) =>
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
        })
      case None =>
        // signal to grouped observable that we are not subscribing
        val _ = observable.take(0).subscribe(_ => ???)
        scribe.warn(s"Ignoring incoming messages. Unknown sequence id: $seqId")
    }

    Ack.Continue
  }

  val observable = WebsocketObservable(subjects.connected, subjects.disconnected)

  def send(path: List[String], payload: Payload, sendType: SendType, requestTimeout: Option[FiniteDuration]): Task[Either[Failure, Observable[Payload]]] = Task.deferFuture {
    val (seqId, promise) = requestMap.open()
    val request = CallRequest(seqId, path, payload)
    val pickledRequest = serializer.serialize(request)
    val message = sendType match {
      case SendType.NowOrFail => WebsocketMessage.Direct(pickledRequest, promise, requestTimeout)
      case SendType.WhenConnected(priority) => WebsocketMessage.Buffered(pickledRequest, promise, requestTimeout, priority)
    }

    val _ = subjects.outgoingMessages.onNext(message)

    promise.future
  }

  def run(location: String): Unit = ws.run(location, wsConfig, serializer.serialize(Ping), subjects.outgoingMessages, new WebsocketListener[PickleType] {
    def onConnect(): Unit = {
      val _ = subjects.connected.onNext(())
    }
    def onClose(): Unit = {
      requestMap.cancelAllRequests()
      val _ = subjects.disconnected.onNext(())
    }
    def onMessage(msg: Future[Option[PickleType]]): Unit = {
      val _= subjects.incomingMessages.onNext(msg)
    }
  })
}

object WebsocketClient {
  def apply[PickleType, Failure](
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Failure], PickleType]) =
    withPayload[PickleType, PickleType, Failure](connection, config)

  def withPayload[PickleType, Payload, Failure](
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[Payload], PickleType],
    deserializer: Deserializer[ServerMessage[Payload, Failure], PickleType]) = {
    val requestMap = new RequestMap[Either[Failure, Observable[Payload]]]
    new WebsocketClientWithPayload(config, connection, requestMap)
  }
}
