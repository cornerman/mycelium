package mycelium.server

import monix.reactive.Observable

import scala.concurrent.Future

sealed trait EventualResult[T] extends Any
object EventualResult {
  case class Single[T](future: Future[T]) extends AnyVal with EventualResult[T]
  case class Stream[T](observable: Observable[T]) extends AnyVal with EventualResult[T]
}

case class HandlerResponse[Payload, Failure, State](state: Future[State], value: EventualResult[Either[Failure, Payload]])

trait RequestHandler[Payload, Event, Failure, State] {
  type Response = HandlerResponse[Payload, Failure, State]

  // return the initial reaction for a client
  def initialState: Future[State]

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients. the Client can be
  // used to send events to downstream.
  def onClientConnect(client: Client[Event], state: Future[State]): Unit = {}

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: Client[Event], state: Future[State], reason: DisconnectReason): Unit = {}

  // a request is a (path: List[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: Client[Event], state: Future[State], path: List[String], payload: Payload): Response

  // you can send events to the clients via the client observer. here you can
  // react to events that were send as NotifyWithReaction
  def onEvent(client: Client[Event], state: Future[State], events: List[Event]): Future[State]
}

trait StatefulRequestHandler[Payload, Event, Failure, State] extends RequestHandler[Payload, Event, Failure, State] {
  def Reaction(state: Future[State], events: Future[List[Event]] = Future.successful(Nil)): Reaction = HandlerReaction(state, events)
  def Response(state: Future[State], value: Future[Either[Failure, Payload]]): Response = HandlerResponse(state, EventualResult.Single(value))
  def Response(state: Future[State], value: Observable[Either[Failure, Payload]]): Response = HandlerResponse(state, EventualResult.Stream(value))

  def onEvent(client: Client[Event], state: Future[State], events: List[Event]): Reaction = Reaction(state, Future.successful(events))
}
trait StatelessRequestHandler[Payload, Event, Failure] extends RequestHandler[Payload, Event, Failure, Unit] {
  def Reaction(events: Future[List[Event]] = Future.successful(Nil)): Reaction = HandlerReaction(initialState, events)
  def Response(value: Future[Either[Failure, Payload]]): Response = HandlerResponse(initialState, EventualResult.Single(value))
  def Response(value: Observable[Either[Failure, Payload]]): Response = HandlerResponse(initialState, EventualResult.Stream(value))

  def onClientConnect(client: Client[Event]): Unit = {}
  def onClientDisconnect(client: Client[Event], reason: DisconnectReason): Unit = {}
  def onRequest(client: Client[Event], path: List[String], payload: Payload): Response
  def onEvent(client: Client[Event], events: List[Event]): Reaction = Reaction(Future.successful(events))

  final def initialState = Future.successful(())
  final override def onClientConnect(client: Client[Event], state: Future[Unit]): Unit = onClientConnect(client)
  final override def onClientDisconnect(client: Client[Event], state: Future[Unit], reason: DisconnectReason): Unit = onClientDisconnect(client, reason)
  final override def onRequest(client: Client[Event], state: Future[Unit], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
  final override def onEvent(client: Client[Event], state: Future[Unit], events: List[Event]): Reaction = onEvent(client, events)
}
