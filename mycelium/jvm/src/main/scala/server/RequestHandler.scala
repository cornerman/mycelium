package mycelium.server

import scala.concurrent.Future

trait RequestHandler[Payload, Event, PublishEvent, Failure, State] {

  case class InitialState(state: Future[State], events: Future[Seq[Event]] = Future.successful(Seq.empty))
  case class Response(result: Future[Either[Failure, Payload]], state: Option[Future[State]] = None, events: Future[Seq[Event]] = Future.successful(Seq.empty))
  case class Reaction(state: Option[Future[State]] = None, events: Future[Seq[Event]] = Future.successful(Seq.empty))

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients and returning the initial.
  // the NotifiableClient can be used to send events to downstream.
  def onClientConnect(client: NotifiableClient[PublishEvent]): InitialState

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: NotifiableClient[PublishEvent], state: Future[State]): Unit

  // a request is a (path: Seq[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: NotifiableClient[PublishEvent], state: Future[State], path: List[String], payload: Payload): Response

  // you can send events to the clients by calling notify(event) on the NotifiableClient.
  // here you can let each client react when receiving such an event.
  def onEvent(client: NotifiableClient[PublishEvent], state: Future[State], event: PublishEvent): Reaction
}

trait SimpleRequestHandler[Payload, Event, Failure, State] extends RequestHandler[Payload, Event, Nothing, Failure, State] {
  def onClientConnect(): InitialState
  def onRequest(state: Future[State], path: List[String], payload: Payload): Response

  final def onClientConnect(client: NotifiableClient[Nothing]): InitialState = onClientConnect()
  final def onClientDisconnect(client: NotifiableClient[Nothing], state: Future[State]): Unit = {}
  final def onRequest(client: NotifiableClient[Nothing], state: Future[State], path: List[String], payload: Payload): Response = onRequest(state, path, payload)
  final def onEvent(client: NotifiableClient[Nothing], state: Future[State], event: Nothing): Reaction = Reaction()
}
