package mycelium.server

import monix.reactive.Observable
import monix.eval.Task

import scala.concurrent.Future

sealed trait HandlerResponse[Payload, ErrorType, State] {
  def state: Future[State]
}
object HandlerResponse {
  case class Single[Payload, ErrorType, State](state: Future[State], result: Task[Either[ErrorType, Payload]]) extends HandlerResponse[Payload, ErrorType, State]
  case class Stream[Payload, ErrorType, State](state: Future[State], result: Observable[Either[ErrorType, Payload]]) extends HandlerResponse[Payload, ErrorType, State]
}

trait RequestHandler[Payload, ErrorType, State] {
  type Response = HandlerResponse[Payload, ErrorType, State]

  // return the initial state for a client
  def initialState: Future[State]

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients. the ClientId uniquely
  // identitifes this client connection.
  def onClientConnect(client: ClientId, state: Future[State]): Unit = {}

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: ClientId, state: Future[State], reason: DisconnectReason): Unit = {}

  // a request is a (path: List[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: ClientId, state: Future[State], path: List[String], payload: Payload): Response
}

trait StatefulRequestHandler[Payload, ErrorType, State] extends RequestHandler[Payload, ErrorType, State] {
  def Response(state: Future[State], task: Task[Either[ErrorType, Payload]]): Response = HandlerResponse.Single(state, task)
  def Response(state: Future[State], observable: Observable[Either[ErrorType, Payload]]): Response = HandlerResponse.Stream(state, observable)
}

trait StatelessRequestHandler[Payload, ErrorType] extends RequestHandler[Payload, ErrorType, Unit] {
  def Response(task: Task[Either[ErrorType, Payload]]): Response = HandlerResponse.Single(initialState, task)
  def Response(observable: Observable[Either[ErrorType, Payload]]): Response = HandlerResponse.Stream(initialState, observable)

  def onClientConnect(client: ClientId): Unit = {}
  def onClientDisconnect(client: ClientId, reason: DisconnectReason): Unit = {}
  def onRequest(client: ClientId, path: List[String], payload: Payload): Response

  final def initialState = Future.successful(())
  final override def onClientConnect(client: ClientId, state: Future[Unit]): Unit = onClientConnect(client)
  final override def onClientDisconnect(client: ClientId, state: Future[Unit], reason: DisconnectReason): Unit = onClientDisconnect(client, reason)
  final override def onRequest(client: ClientId, state: Future[Unit], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
}
