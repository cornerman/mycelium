package mycelium.server

import scala.concurrent.Future

trait RequestHandler[Payload, Event, PublishEvent, Failure, State] {
  case class Reaction(state: Future[State], events: Future[Seq[Event]])
  case class Response(reaction: Reaction, result: Future[Either[Failure, Payload]])

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients and returning the initial state.
  // the NotifiableClient can be used to send events to downstream.
  def onClientConnect(client: NotifiableClient[PublishEvent]): Reaction

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: ClientIdentity, state: Future[State]): Unit

  // a request is a (path: Seq[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: ClientIdentity, state: Future[State], path: List[String], payload: Payload): Response

  // you can send events to the clients by calling notify(event) on the NotifiableClient.
  // here you can let each client react when receiving such an event.
  def onEvent(client: ClientIdentity, state: Future[State], event: PublishEvent): Reaction
}
