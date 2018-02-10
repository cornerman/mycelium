package mycelium.server

import scala.concurrent.Future

case class HandlerReaction[Event, State](state: Future[State], events: Future[List[Event]])
case class HandlerReturnValue[Payload, Event, Failure](result: Either[Failure, Payload], events: List[Event])
case class HandlerResponse[Payload, Event, Failure, State](state: Future[State], value: Future[HandlerReturnValue[Payload, Event, Failure]])

trait RequestHandler[Payload, Event, Failure, State] {
  type Reaction = HandlerReaction[Event, State]
  type ReturnValue = HandlerReturnValue[Payload, Event, Failure]
  type Response = HandlerResponse[Payload, Event, Failure, State]

  // return the initial reaction for a client
  def initialState: Future[State]

  // called when a client connects to the websocket. this allows for
  // managing/bookkeeping of connected clients. the NotifiableClient can be
  // used to send events to downstream.
  def onClientConnect(client: NotifiableClient[Event], state: Future[State]): Unit = {}

  // called when a client disconnects. this can be due to a timeout on the
  // websocket connection or the client closed the connection.
  def onClientDisconnect(client: NotifiableClient[Event], state: Future[State], reason: DisconnectReason): Unit = {}

  // a request is a (path: List[String], args: Payload), which
  // needs to be mapped to a result.  if the request cannot be handled, you can
  // return an error. this is the integration point for e.g. sloth or autowire
  def onRequest(client: NotifiableClient[Event], state: Future[State], path: List[String], payload: Payload): Response

  // you can send events to the clients by calling notify(events) on the NotifiableClient.
  // here you can let each client react when receiving such events.
  def onEvent(client: NotifiableClient[Event], state: Future[State], events: List[Event]): Reaction
}

trait FullRequestHandler[Payload, Event, Failure, State] extends RequestHandler[Payload, Event, Failure, State] {
  def Reaction(state: Future[State], events: Future[List[Event]] = Future.successful(Nil)): Reaction = HandlerReaction(state, events)
  def ReturnValue(result: Either[Failure, Payload], events: List[Event] = Nil): ReturnValue = HandlerReturnValue(result, events)
  def Response(state: Future[State], value: Future[ReturnValue]): Response = HandlerResponse(state, value)
}

trait SimpleRequestHandler[Payload, Event, Failure, State] extends FullRequestHandler[Payload, Event, Failure, State] {
  def onClientConnect(state: Future[State]): Unit = {}
  def onClientDisconnect(state: Future[State], reason: DisconnectReason): Unit = {}
  def onRequest(state: Future[State], path: List[String], payload: Payload): Response

  final override def onClientConnect(client: NotifiableClient[Event], state: Future[State]): Unit = onClientConnect(state)
  final override def onClientDisconnect(client: NotifiableClient[Event], state: Future[State], reason: DisconnectReason): Unit = onClientDisconnect(state, reason)
  final override def onRequest(client: NotifiableClient[Event], state: Future[State], path: List[String], payload: Payload): Response = onRequest(state, path, payload)
  final override def onEvent(client: NotifiableClient[Event], state: Future[State], events: List[Event]): Reaction = ???
}

trait StatelessRequestHandler[Payload, Event, Failure] extends RequestHandler[Payload, Event, Failure, Unit] {
  def Reaction(events: Future[List[Event]] = Future.successful(Nil)): Reaction = HandlerReaction(initialState, events)
  def ReturnValue(result: Either[Failure, Payload], events: List[Event] = Nil): ReturnValue = HandlerReturnValue(result, events)
  def Response(value: Future[ReturnValue]): Response = HandlerResponse(initialState, value)

  def onClientConnect(client: NotifiableClient[Event]): Unit = {}
  def onClientDisconnect(client: NotifiableClient[Event], reason: DisconnectReason): Unit = {}
  def onRequest(client: NotifiableClient[Event], path: List[String], payload: Payload): Response
  def onEvent(client: NotifiableClient[Event], events: List[Event]): Reaction

  final def initialState = Future.successful(())
  final override def onClientConnect(client: NotifiableClient[Event], state: Future[Unit]): Unit = onClientConnect(client)
  final override def onClientDisconnect(client: NotifiableClient[Event], state: Future[Unit], reason: DisconnectReason): Unit = onClientDisconnect(client, reason)
  final override def onRequest(client: NotifiableClient[Event], state: Future[Unit], path: List[String], payload: Payload): Response = onRequest(client, path, payload)
  final override def onEvent(client: NotifiableClient[Event], state: Future[Unit], events: List[Event]): Reaction = onEvent(client, events)
}

trait SimpleStatelessRequestHandler[Payload, Event, Failure] extends StatelessRequestHandler[Payload, Event, Failure] {
  def onClientConnect(): Unit = {}
  def onClientDisconnect(reason: DisconnectReason): Unit = {}
  def onRequest(path: List[String], payload: Payload): Response

  final override def onClientConnect(client: NotifiableClient[Event]): Unit = onClientConnect()
  final override def onClientDisconnect(client: NotifiableClient[Event], reason: DisconnectReason): Unit = onClientDisconnect(reason)
  final override def onRequest(client: NotifiableClient[Event], path: List[String], payload: Payload): Response = onRequest(path, payload)
  final override def onEvent(client: NotifiableClient[Event], events: List[Event]): Reaction = ???
}
