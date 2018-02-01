package mycelium

package object client {
  type WebsocketClient[PickleType, Event, Failure] = WebsocketClientWithPayload[PickleType, PickleType, Event, Failure]
}
