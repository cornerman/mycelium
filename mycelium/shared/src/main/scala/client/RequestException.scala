package mycelium.client

object RequestException {
  case object Canceled extends Exception
  case object Timeout extends Exception
  case object Dropped extends Exception
}
