package mycelium.server

import monix.reactive.Observable
import monix.eval.Task

import scala.concurrent.Future

sealed trait EventualResult[T] extends Any
object EventualResult {
  case class Single[T](value: T) extends AnyVal with EventualResult[T]
  case class Stream[T](observable: Observable[T]) extends AnyVal with EventualResult[T]
}

case class HandlerResponse[Payload, Failure, State](state: Future[State], task: Task[Either[Failure, EventualResult[Payload]]])

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
  def ResponseValue(state: Future[State], task: Task[Either[Failure, Payload]]): Response = HandlerResponse(state, task.map(_.right.map(EventualResult.Single(_))))
  def ResponseStream(state: Future[State], task: Task[Either[Failure, Observable[Payload]]]): Response = HandlerResponse(state, task.map(_.right.map(EventualResult.Stream(_))))
}

trait StatelessRequestHandler[Payload, Failure] extends RequestHandler[Payload, Failure, Unit] {
  def ResponseValue(task: Task[Either[Failure, Payload]]): Response = HandlerResponse(initialState, task.map(_.right.map(EventualResult.Single(_))))
  def ResponseStream(task: Task[Either[Failure, Observable[Payload]]]): Response = HandlerResponse(initialState, task.map(_.right.map(EventualResult.Stream(_))))

  def onClientConnect(client: ClientId): Unit = {}
  def onClientDisconnect(client: ClientId, reason: DisconnectReason): Unit = {}
  def onRequest(client: ClientId, path: List[String], payload: Payload): Response

  final def initialState = Future.successful(())
  final override def onClientConnect(client: ClientId, state: Future[Unit]): Unit = onClientConnect(client)
  final override def onClientDisconnect(client: ClientId, state: Future[Unit], reason: DisconnectReason): Unit = onClientDisconnect(client, reason)
  final override def onRequest(client: ClientId, state: Future[Unit], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
}
