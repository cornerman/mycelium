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
  reactiveConn: ReactiveWebsocketConnection[PickleType],
  requestMap: RequestMap[EventualResult[Payload, ErrorType]])(implicit
  scheduler: Scheduler,
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, ErrorType], PickleType]) extends Cancelable {

  private val responseMessages: Observable[ServerMessage[Payload, ErrorType] with ServerResponse] = reactiveConn.incomingMessages.concatMap { rawMessage =>
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

  private val outgoingMessages = ConcurrentSubject.publish[WebsocketMessage[PickleType]]

  private val cancelable = {
    val cancelable = CompositeCancelable()
    cancelable += reactiveConn.cancelable

    cancelable += responseMessages.groupBy(_.seqId).subscribe { observable =>
      val seqId = observable.key
      requestMap.get(seqId) match {
        case Some(promise) =>
          observable.subscribe(new ResponseObserver(promise))
        case None =>
          scribe.warn(s"Ignoring incoming messages. Unknown sequence id: $seqId")
          // signal to grouped observable that we subscribe and it should not cache anymore
          observable.take(0).subscribe(_ => ???)
      }

      Ack.Continue
    }

    val messageSender = new WebsocketMessageSender[PickleType](reactiveConn.outgoingMessages)
    cancelable += Observable.merge(
      outgoingMessages.map(SenderAction.SendMessage.apply),
      reactiveConn.connected.map(SenderAction.ConnectionChanged.apply)
    ).subscribe(messageSender)

    cancelable
  }

  val connected: Observable[Boolean] = connected

  def cancel(): Unit = cancelable.cancel()

  def send(path: List[String], payload: Payload, sendType: SendType, requestTimeout: Option[FiniteDuration]): Task[EventualResult[Payload, ErrorType]] = Task.deferFuture {
    val (seqId, promise) = requestMap.open()
    val request = CallRequest(seqId, path, payload)
    val pickledRequest = serializer.serialize(request)
    val message = sendType match {
      case SendType.NowOrFail => WebsocketMessage.Direct(pickledRequest, promise, requestTimeout)
      case SendType.WhenConnected(priority) => WebsocketMessage.Buffered(pickledRequest, promise, requestTimeout, priority)
    }

    outgoingMessages.onNext(message)

    promise.future
  }
}

object WebsocketClient {
  def apply[PickleType, ErrorType](
    location: String,
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, ErrorType], PickleType]) =
    withPayload[PickleType, PickleType, ErrorType](location, connection, config)

  def withPayload[PickleType, Payload, ErrorType](
    location: String,
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    scheduler: Scheduler,
    serializer: Serializer[ClientMessage[Payload], PickleType],
    deserializer: Deserializer[ServerMessage[Payload, ErrorType], PickleType]) = {
    val requestMap = new RequestMap[EventualResult[Payload, ErrorType]]
    val reactiveConn = connection.run(location, config, serializer.serialize(Ping))
    new WebsocketClientWithPayload(reactiveConn, requestMap)
  }
}
