package mycelium.client

import mycelium.core.AkkaMessageBuilder
import akka.actor.ActorSystem

object NativeWebsocket {
  def akka[PickleType : AkkaMessageBuilder](implicit system: ActorSystem): WebsocketConnection[PickleType] = new AkkaWebsocketConnection
}
