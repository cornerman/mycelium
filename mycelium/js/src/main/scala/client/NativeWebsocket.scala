package mycelium.client

import mycelium.core.JsMessageBuilder

object NativeWebsocket {
  def js[PickleType : JsMessageBuilder]: WebsocketConnection[PickleType] = new JsWebsocketConnection
}
