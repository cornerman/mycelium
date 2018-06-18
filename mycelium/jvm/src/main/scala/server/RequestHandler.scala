package mycelium.server

import monix.reactive.Observable
import monix.eval.Task

import scala.concurrent.Future

sealed trait EventualResult[Failure, T] extends Any
object EventualResult {
  case class Single[Failure, T](future: Task[Either[Failure, T]]) extends AnyVal with EventualResult[Failure, T]
  case class Stream[Failure, T](observable: Task[Either[Failure, Observable[T]]]) extends AnyVal with EventualResult[Failure, T]
}

case class HandlerResponse[Payload, Failure, State](state: Future[State], value: EventualResult[Failure, Payload])

trait RequestHandler[Payload, Failure, State] {
  type Response = HandlerResponse[Payload, Failure, State]

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

trait StatefulRequestHandler[Payload, Failure, State] extends RequestHandler[Payload, Failure, State] {
  def ResponseValue(state: Future[State], value: Task[Either[Failure, Payload]]): Response = HandlerResponse(state, EventualResult.Single(value))
  def ResponseStream(state: Future[State], value: Task[Either[Failure, Observable[Payload]]]): Response = HandlerResponse(state, EventualResult.Stream(value))
}

trait StatelessRequestHandler[Payload, Failure] extends RequestHandler[Payload, Failure, Unit] {
  def ResponseValue(value: Task[Either[Failure, Payload]]): Response = HandlerResponse(initialState, EventualResult.Single(value))
  def ResponseStream(value: Task[Either[Failure, Observable[Payload]]]): Response = HandlerResponse(initialState, EventualResult.Stream(value))

  def onClientConnect(client: ClientId): Unit = {}
  def onClientDisconnect(client: ClientId, reason: DisconnectReason): Unit = {}
  def onRequest(client: ClientId, path: List[String], payload: Payload): Response

  final def initialState = Future.successful(())
  final override def onClientConnect(client: ClientId, state: Future[Unit]): Unit = onClientConnect(client)
  final override def onClientDisconnect(client: ClientId, state: Future[Unit], reason: DisconnectReason): Unit = onClientDisconnect(client, reason)
  final override def onRequest(client: ClientId, state: Future[Unit], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
}
