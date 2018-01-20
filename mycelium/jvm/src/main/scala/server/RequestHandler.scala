package mycelium.server

import scala.concurrent.Future

trait HandlerReaction[Event, State] {
  def state: Future[State]
  def events: Future[Seq[Event]]
}

trait HandlerResponse[Payload, Event, Failure, State] {
  def result: Future[Either[Failure, Payload]]
  def reaction: HandlerReaction[Event, State]
}

sealed trait DisconnectReason
object DisconnectReason {
  case object Stopped extends DisconnectReason
  case class StateFailed(failure: Throwable) extends DisconnectReason
}

trait RequestHandler[Payload, Event, PublishEvent, Failure, State] {

  // return the initial reaction for a client
  def initialReaction: HandlerReaction[Event, State]

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients. the NotifiableClient can be
  // used to send events to downstream.
  def onClientConnect(client: NotifiableClient[PublishEvent], state: Future[State]): Unit = {}

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: NotifiableClient[PublishEvent], state: Future[State], reason: DisconnectReason): Unit = {}

  // a request is a (path: Seq[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: NotifiableClient[PublishEvent], state: Future[State], path: List[String], payload: Payload): HandlerResponse[Payload, Event, Failure, State]

  // you can send events to the clients by calling notify(event) on the NotifiableClient.
  // here you can let each client react when receiving such an event.
  def onEvent(client: NotifiableClient[PublishEvent], state: Future[State], event: PublishEvent): HandlerReaction[Event, State]
}

trait FullRequestHandler[Payload, Event, PublishEvent, Failure, State] extends RequestHandler[Payload, Event, PublishEvent, Failure, State] {
  case class Reaction(state: Future[State], events: Future[Seq[Event]] = Future.successful(Seq.empty)) extends HandlerReaction[Event, State]
  case class Response(result: Future[Either[Failure, Payload]], reaction: Reaction) extends HandlerResponse[Payload, Event, Failure, State]
}

trait SimpleRequestHandler[Payload, Event, Failure, State] extends FullRequestHandler[Payload, Event, Nothing, Failure, State] {
  def onClientConnect(state: Future[State]): Unit = {}
  def onClientDisconnect(state: Future[State], reason: DisconnectReason): Unit = {}
  def onRequest(state: Future[State], path: List[String], payload: Payload): Response

  final override def onClientConnect(client: NotifiableClient[Nothing], state: Future[State]): Unit = onClientConnect(state)
  final override def onClientDisconnect(client: NotifiableClient[Nothing], state: Future[State], reason: DisconnectReason): Unit = onClientDisconnect(state, reason)
  final override def onRequest(client: NotifiableClient[Nothing], state: Future[State], path: List[String], payload: Payload): Response = onRequest(state, path, payload)
  final override def onEvent(client: NotifiableClient[Nothing], state: Future[State], event: Nothing): Reaction = ???
}

trait StatelessRequestHandler[Payload, Event, PublishEvent, Failure] extends RequestHandler[Payload, Event, PublishEvent, Failure, Nothing] {
  case class Reaction(events: Future[Seq[Event]] = Future.successful(Seq.empty)) extends HandlerReaction[Event, Nothing] {
    val state = Future.never
  }
  case class Response(result: Future[Either[Failure, Payload]], reaction: Reaction = Reaction()) extends HandlerResponse[Payload, Event, Failure, Nothing]

  def initialReaction = Reaction()
  def onClientConnect(client: NotifiableClient[PublishEvent]): Unit = {}
  def onClientDisconnect(client: NotifiableClient[PublishEvent], reason: DisconnectReason): Unit = {}
  def onRequest(client: NotifiableClient[PublishEvent], path: List[String], payload: Payload): Response
  def onEvent(client: NotifiableClient[PublishEvent], event: PublishEvent): Reaction

  final override def onClientConnect(client: NotifiableClient[PublishEvent], state: Future[Nothing]): Unit = onClientConnect(client)
  final override def onClientDisconnect(client: NotifiableClient[PublishEvent], state: Future[Nothing], reason: DisconnectReason): Unit = onClientDisconnect(client, reason)
  final override def onRequest(client: NotifiableClient[PublishEvent], state: Future[Nothing], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
  final override def onEvent(client: NotifiableClient[PublishEvent], state: Future[Nothing], event: PublishEvent): Reaction = onEvent(client, event)
}

trait SimpleStatelessRequestHandler[Payload, Event, Failure] extends StatelessRequestHandler[Payload, Event, Nothing, Failure] {
  def onClientConnect(): Unit = {}
  def onClientDisconnect(reason: DisconnectReason): Unit = {}
  def onRequest(path: List[String], payload: Payload): Response

  final override def onClientConnect(client: NotifiableClient[Nothing]): Unit = onClientConnect()
  final override def onClientDisconnect(client: NotifiableClient[Nothing], reason: DisconnectReason): Unit = onClientDisconnect(reason)
  final override def onRequest(client: NotifiableClient[Nothing], path: List[String], payload: Payload): Response = onRequest(path, payload)
  final override def onEvent(client: NotifiableClient[Nothing], event: Nothing): Reaction = ???
}
