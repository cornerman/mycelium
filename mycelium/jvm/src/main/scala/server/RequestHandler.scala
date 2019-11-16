package mycelium.server

import monix.eval.Task
import mycelium.core.EventualResult

import scala.concurrent.Future

case class HandlerResponse[Payload, ErrorType, State](state: Future[State], task: Task[EventualResult[Payload, ErrorType]])

trait RequestHandler[Payload, ErrorType, State] {
  type Response = HandlerResponse[Payload, ErrorType, State]

  // return the initial state for a client
  def initialState: Future[State]

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients. the ClientId uniquely
  // identitifes this client connection.
  def onClientConnect(client: ClientId): Unit = {}

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: ClientId, reason: DisconnectReason): Unit = {}

  // a request is a (path: List[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: ClientId, state: Future[State], path: List[String], payload: Payload): Response
}

trait StatefulRequestHandler[Payload, ErrorType, State] extends RequestHandler[Payload, ErrorType, State] {
  def Response(state: Future[State], task: Task[EventualResult[Payload, ErrorType]]): Response = HandlerResponse(state, task)
}

trait StatelessRequestHandler[Payload, ErrorType] extends RequestHandler[Payload, ErrorType, Unit] {
  def Response(task: Task[EventualResult[Payload, ErrorType]]): Response = HandlerResponse(initialState, task)

  def onRequest(client: ClientId, path: List[String], payload: Payload): Response

  final def initialState = Future.successful(())
  final override def onRequest(client: ClientId, state: Future[Unit], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
}
