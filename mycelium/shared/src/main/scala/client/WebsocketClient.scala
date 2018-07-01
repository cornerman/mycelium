package mycelium.client

import chameleon._
import monix.eval.Task
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.{ConcurrentSubject, PublishSubject}
import mycelium.core.message._

import scala.concurrent.Future
import scala.concurrent.duration._

case class WebsocketClientConfig(minReconnectDelay: FiniteDuration = 1.seconds, maxReconnectDelay: FiniteDuration = 60.seconds, delayReconnectFactor: Double = 1.3, connectingTimeout: FiniteDuration = 5.seconds, pingInterval: FiniteDuration = 45.seconds)

class WebsocketClientWithPayload[PickleType, Payload, Failure](
  wsConfig: WebsocketClientConfig,
  ws: WebsocketConnection[PickleType],
  requestMap: RequestMap[Either[Failure, Observable[Payload]]])(implicit
  scheduler: Scheduler,
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, Failure], PickleType]) {

  private object subjects {
    val connected = PublishSubject[Boolean]
    val incomingMessages = PublishSubject[Future[Option[PickleType]]]
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

  responseMessages.groupBy(_.seqId).subscribe { observable =>
    val seqId = observable.key
    requestMap.get(seqId) match {
      case Some(promise) =>
        observable.subscribe(new RequestObserver(promise))
        ()
      case None =>
        // signal to grouped observable that we are not subscribing
        observable.take(0).subscribe(_ => ???)
        scribe.warn(s"Ignoring incoming messages. Unknown sequence id: $seqId")
    }

    Ack.Continue
  }

  val connected: Observable[Boolean] = subjects.connected

  def send(path: List[String], payload: Payload, sendType: SendType, requestTimeout: Option[FiniteDuration]): Task[Either[Failure, Observable[Payload]]] = Task.deferFuture {
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

  def run(location: String): Unit = {
    val reactiveConn = ws.run(location, wsConfig, serializer.serialize(Ping))
    reactiveConn.connected.subscribe(subjects.connected)
    reactiveConn.incomingMessages.subscribe(subjects.incomingMessages)

    val messageSender = new WebsocketMessageSender[PickleType](reactiveConn.outgoingMessages)
    Observable.merge(
      subjects.outgoingMessages.map(SenderAction.SendMessage.apply),
      reactiveConn.connected.map(SenderAction.ConnectionChanged.apply)
    ).subscribe(messageSender)

    ()
  }
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
