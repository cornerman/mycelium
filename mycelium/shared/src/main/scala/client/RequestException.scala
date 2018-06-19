package mycelium.client

import mycelium.core.message.ServerMessage

object RequestException {
  case object Canceled extends Exception
  case object Timeout extends Exception
  case object Dropped extends Exception
  case object ErrorResponse extends Exception
  case class IllegalResponse(response: ServerMessage[_,_]) extends Exception(s"Illegal response from server: $response")
  case object StoppedDownstream extends Exception
}
