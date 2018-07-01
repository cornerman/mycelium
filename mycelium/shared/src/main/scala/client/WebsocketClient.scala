package mycelium.client

import chameleon._
import monix.eval.Task
import monix.execution.cancelables.CompositeCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import mycelium.core.EventualResult
import mycelium.core.message._

import scala.concurrent.Future
import scala.concurrent.duration._

case class WebsocketClientConfig(minReconnectDelay: FiniteDuration = 1.seconds, maxReconnectDelay: FiniteDuration = 60.seconds, delayReconnectFactor: Double = 1.3, connectingTimeout: FiniteDuration = 5.seconds, pingInterval: FiniteDuration = 45.seconds)

class WebsocketClientWithPayload[PickleType, Payload, ErrorType](
  wsConfig: WebsocketClientConfig,
  ws: WebsocketConnection[PickleType],
  requestMap: RequestMap[EventualResult[Payload, ErrorType]])(implicit
  scheduler: Scheduler,
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, ErrorType], PickleType]) {

  private object subjects {
    val connected = PublishSubject[Boolean]
    val incomingMessages = PublishSubject[Future[Option[PickleType]]]
    val outgoingMessages = ConcurrentSubject.publish[WebsocketMessage[PickleType]]
  }

  private val responseMessages: Observable[ServerMessage[Payload, ErrorType] with ServerResponse] = subjects.incomingMessages.concatMap { rawMessage =>
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

  responseMessages.groupBy(_.seqId).subscribe { observable =>
    val seqId = observable.key
    requestMap.get(seqId) match {
      case Some(promise) =>
        observable.subscribe(new RequestObserver(promise))
        ()
      case None =>
        // signal to grouped observable that we do not need any elements from this group
        observable.take(0).subscribe(_ => ???)
        scribe.warn(s"Ignoring incoming messages. Unknown sequence id: $seqId")
    }

    Ack.Continue
  }

  val connected: Observable[Boolean] = subjects.connected

  def send(path: List[String], payload: Payload, sendType: SendType, requestTimeout: Option[FiniteDuration]): Task[EventualResult[Payload, ErrorType]] = Task.deferFuture {
    val (seqId, promise) = requestMap.open()
    val request = CallRequest(seqId, path, payload)
    val pickledRequest = serializer.serialize(request)
    val message = sendType match {
      case SendType.NowOrFail => WebsocketMessage.Direct(pickledRequest, promise, requestTimeout)
      case SendType.WhenConnected(priority) => WebsocketMessage.Buffered(pickledRequest, promise, requestTimeout, priority)
    }

    subjects.outgoingMessages.onNext(message)

    promise.future
  }

  def run(location: String): Cancelable = {
    val reactiveConn = ws.run(location, wsConfig, serializer.serialize(Ping))

    val cancelable = CompositeCancelable()
    cancelable += reactiveConn.cancelable
    cancelable += reactiveConn.connected.subscribe(subjects.connected)
    cancelable += reactiveConn.incomingMessages.subscribe(subjects.incomingMessages)

    val messageSender = new WebsocketMessageSender[PickleType](reactiveConn.outgoingMessages)
    cancelable += Observable.merge(
      subjects.outgoingMessages.map(SenderAction.SendMessage.apply),
      reactiveConn.connected.map(SenderAction.ConnectionChanged.apply)
    ).subscribe(messageSender)

    cancelable
  }
}

object WebsocketClient {
  def apply[PickleType, ErrorType](
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, ErrorType], PickleType]) =
    withPayload[PickleType, PickleType, ErrorType](connection, config)

  def withPayload[PickleType, Payload, ErrorType](
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[Payload], PickleType],
    deserializer: Deserializer[ServerMessage[Payload, ErrorType], PickleType]) = {
    val requestMap = new RequestMap[EventualResult[Payload, ErrorType]]
    new WebsocketClientWithPayload(config, connection, requestMap)
  }
}
