package mycelium.client

import chameleon._
import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.PublishSubject
import mycelium.core.message._

import scala.concurrent.duration._
import scala.concurrent.Future

case class WebsocketClientConfig(minReconnectDelay: FiniteDuration = 1.seconds, maxReconnectDelay: FiniteDuration = 60.seconds, delayReconnectFactor: Double = 1.3, connectingTimeout: FiniteDuration = 5.seconds, pingInterval: FiniteDuration = 45.seconds)
case class WebsocketObservable(connected: Observable[Unit], disconnected: Observable[Unit])

class WebsocketClientWithPayload[PickleType, Payload, Failure](
  wsConfig: WebsocketClientConfig,
  ws: WebsocketConnection[PickleType],
  requestMap: RequestMap[Either[Failure, Payload]])(implicit
  scheduler: Scheduler,
  serializer: Serializer[ClientMessage[Payload], PickleType],
  deserializer: Deserializer[ServerMessage[Payload, Failure], PickleType]) {

  private object subjects {
    val connected = PublishSubject[Unit]()
    val disconnected = PublishSubject[Unit]()
    val messages = PublishSubject[ServerMessage[Payload, Failure] with ServerResponse]()
  }

  val _ = subjects.messages.groupBy(_.seqId).subscribe { observable =>
    val seqId = observable.key
    requestMap.get(seqId) match {
      case Some(subject) =>
        val _ = observable.subscribe(new Observer[ServerMessage[Payload, Failure] with ServerResponse] {
          override def onError(ex: Throwable): Unit = requestMap.get(seqId).foreach(_.onError(ex))
          override def onComplete(): Unit = requestMap.get(seqId).foreach(_.onComplete())

          override def onNext(elem: ServerMessage[Payload, Failure] with ServerResponse): Future[Ack] = elem match {
            case SingleResponse(_, result) =>
              val _ = subject onNext Right(result)
              subject.onComplete()
              Ack.Stop
            case StreamResponse(_, result) =>
              subject onNext Right(result)
            case StreamCloseResponse(seqId) =>
              subject.onComplete()
              Ack.Stop
            case FailureResponse(seqId, msg) =>
              val _ = subject onNext Left(msg)
              subject.onComplete()
              Ack.Stop
            case ErrorResponse(seqId) =>
              subject onError RequestException.ErrorResponse
              Ack.Stop
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

  def send(path: List[String], payload: Payload, sendType: SendType, requestTimeout: Option[FiniteDuration]): Observable[Either[Failure, Payload]] = Observable.defer {
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

  def run(location: String): Unit = ws.run(location, wsConfig, serializer.serialize(Ping), new WebsocketListener[PickleType] {
    def onConnect() = {
      val _ = subjects.connected.onNext(())
    }
    def onClose() = {
      requestMap.cancelAllRequests()
      val _ = subjects.disconnected.onNext(())
    }
    def onMessage(msg: PickleType): Future[Unit] = {
      deserializer.deserialize(msg) match {
        case Right(response) => response match {
          case response: ServerResponse => subjects.messages.onNext(response).flatMap {
            case Ack.Continue => Future.successful(())
            case Ack.Stop =>
              scribe.warn("Cannot push further messages, received Stop.")
              Future.failed(RequestException.StoppedDownstream)
          }
          case Pong =>
            Future.successful(())
        }
        case Left(error) =>
          scribe.warn(s"Ignoring message. Deserializer failed: $error")
          Future.successful(())
      }
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
    val requestMap = new RequestMap[Either[Failure, Payload]]
    new WebsocketClientWithPayload(config, connection, requestMap)
  }
}
