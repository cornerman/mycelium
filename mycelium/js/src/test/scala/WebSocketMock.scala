package test

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSImport, ScalaJSDefined}

@js.native
@JSImport("html5-websocket", JSImport.Namespace)
class WebSocket(url: js.UndefOr[String], proto: js.UndefOr[String] = js.undefined) extends js.Object

object WebSocketMock {
  def setup(): Unit = {
    if(js.isUndefined(js.Dynamic.global.WebSocket))
      js.Dynamic.global.updateDynamic("WebSocket")(js.constructorOf[WebSocket])
  }
}
