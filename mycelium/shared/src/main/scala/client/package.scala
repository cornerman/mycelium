package mycelium

package object client {
  type WebsocketClient[PickleType, Failure] = WebsocketClientWithPayload[PickleType, PickleType, Failure]
}
