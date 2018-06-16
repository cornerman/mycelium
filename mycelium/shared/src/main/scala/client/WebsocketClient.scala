package mycelium.client

import chameleon._
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import mycelium.core.message._

import scala.concurrent.duration._

case class WebsocketClientConfig(minReconnectDelay: FiniteDuration = 1.seconds, maxReconnectDelay: FiniteDuration = 60.seconds, delayReconnectFactor: Double = 1.3, connectingTimeout: FiniteDuration = 5.seconds, pingInterval: FiniteDuration = 45.seconds)
case class WebsocketObservable(connected: Observable[Unit], disconnected: Observable[Unit])

class WebsocketClientWithPayload[PickleType, Payload, Failure](
  wsConfig: WebsocketClientConfig,
  ws: WebsocketConnection[PickleType],
  requestMap: RequestMap[Either[Failure, Payload]])(implicit
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, Failure], PickleType]) {

  private object subject {
    val connected = PublishSubject[Unit]()
    val disconnected = PublishSubject[Unit]()
  }
  val observable = WebsocketObservable(subject.connected, subject.disconnected)

  def send(path: List[String], payload: Payload, sendType: SendType, requestTimeout: Option[FiniteDuration])(implicit scheduler: Scheduler): Observable[Either[Failure, Payload]] = {
    val (seqId, subject) = requestMap.open()
    val request = CallRequest(seqId, path, payload)
    val pickledRequest = serializer.serialize(request)
    val message = sendType match {
      case SendType.NowOrFail => WebsocketMessage.Direct(pickledRequest, subject, requestTimeout)
      case SendType.WhenConnected(priority) => WebsocketMessage.Buffered(pickledRequest, subject, requestTimeout, priority)
    }

    ws.send(message)

    subject
  }

  def run(location: String): Unit = ws.run(location, wsConfig, serializer.serialize(Ping()), new WebsocketListener[PickleType] {
    def onConnect() = subject.connected.onNext(())
    def onClose() = {
      requestMap.cancelAllRequests()
      subject.disconnected.onNext(())
    }
    def onMessage(msg: PickleType): Unit = {
      def withRequestObserver(seqId: SequenceId)(f: Observer[Either[Failure, Payload]] => Unit) = requestMap.get(seqId) match {
        case Some(subject) => f(subject)
        case None => scribe.warn(s"Ignoring incoming response for '$seqId', unknown sequence id.")
      }
      deserializer.deserialize(msg) match {
        case Right(response) => response match {
          case SingleResponse(seqId, result) => withRequestObserver(seqId) { obs =>
            val _ = obs.onNext(result)
            obs.onComplete()
          }
          case StreamResponse(seqId, result) => withRequestObserver(seqId) { obs =>
            val _ = obs.onNext(result)
          }
          case StreamCloseResponse(seqId) => withRequestObserver(seqId) { obs =>
            obs.onComplete()
          }
          case ErrorResponse(seqId, msg) => withRequestObserver(seqId) { obs =>
            obs.onError(RequestException.ErrorResponse(msg))
          }
          case Pong() =>
          // do nothing
        }
        case Left(error) =>
          scribe.warn(s"Ignoring message. Deserializer failed: $error")
      }
    }
  })
}

object WebsocketClient {
  def apply[PickleType, Failure](
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    serializer: Serializer[ClientMessage[PickleType], PickleType],
    deserializer: Deserializer[ServerMessage[PickleType, Failure], PickleType]) =
    withPayload[PickleType, PickleType, Failure](connection, config)

  def withPayload[PickleType, Payload, Failure](
    connection: WebsocketConnection[PickleType],
    config: WebsocketClientConfig)(implicit
    serializer: Serializer[ClientMessage[Payload], PickleType],
    deserializer: Deserializer[ServerMessage[Payload, Failure], PickleType]) = {
    val requestMap = new RequestMap[Either[Failure, Payload]]
    new WebsocketClientWithPayload(config, connection, requestMap)
  }
}
