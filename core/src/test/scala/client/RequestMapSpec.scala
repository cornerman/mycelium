package mycelium.core.client

import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.must.Matchers

class RequestMapSpec extends AsyncFreeSpec with Matchers {
  implicit override def executionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  "open requests" - {
    "unique sequence ids" in {
      val requests = new RequestMap[Int]
      val (id1, _) = requests.open()
      val (id2, _) = requests.open()
      id1 must not equal id2
    }

    "get by id" in {
      val requests      = new RequestMap[Int]
      val (id, promise) = requests.open()
      requests.get(id) mustEqual Option(promise)
    }

    "get with non-existing" in {
      val requests = new RequestMap[Int]
      requests.get(1) mustEqual None
    }

    "usable promise" in {
      val requests     = new RequestMap[Int]
      val (_, promise) = requests.open()
      promise success 1
      promise.future.map(_ mustEqual 1)
    }
  }
}
