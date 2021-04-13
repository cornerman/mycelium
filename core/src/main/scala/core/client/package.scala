package mycelium.core

package object client {
  type WebsocketClient[PickleType, Event, Failure] = WebsocketClientWithPayload[PickleType, PickleType, Event, Failure]
}
