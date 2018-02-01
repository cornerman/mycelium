package mycelium.client

import org.scalatest._

import scala.concurrent.duration._

class CallRequestsSpec extends AsyncFreeSpec with MustMatchers {
  implicit override def executionContext = scala.concurrent.ExecutionContext.Implicits.global

  "open requests" - {
    //TODO: why does it need executionContext = global? also breaks grouping of tests in output
    "timeouts after some time" in {
      val requests = new CallRequests[Int]
      val (_, promise) = requests.open()
      requests.startTimeout(promise, 10 milliseconds)
      promise.future.failed.map(_ mustEqual TimeoutException)
    }

    "unique sequence ids" in {
      val requests = new CallRequests[Int]
      val (id1, _) = requests.open()
      val (id2, _) = requests.open()
      id1 must not equal id2
    }

    "get by id" in {
      val requests = new CallRequests[Int]
      val (id, promise) = requests.open()
      requests.get(id) mustEqual Option(promise)
    }

    "get with non-existing" in {
      val requests = new CallRequests[Int]
      requests.get(1) mustEqual None
    }

    "usable promise" in {
      val requests = new CallRequests[Int]
      val (_, promise) = requests.open()
      promise success 1
      promise.future.map(_ mustEqual 1)
    }
  }
}
