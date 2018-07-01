package mycelium

package object client {
  type WebsocketClient[PickleType, ErrorType] = WebsocketClientWithPayload[PickleType, PickleType, ErrorType]
}
