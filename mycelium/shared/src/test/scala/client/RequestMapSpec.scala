package mycelium.client

import org.scalatest._

import scala.concurrent.ExecutionContext

class RequestMapSpec extends AsyncFreeSpec with MustMatchers {
  import monix.execution.Scheduler.Implicits.global
  override def executionContext: ExecutionContext = implicitly

  "open requests" - {
    "unique sequence ids" in {
      val requests = new RequestMap[Int]
      val (id1, _) = requests.open()
      val (id2, _) = requests.open()
      id1 must not equal id2
    }

    "get by id" in {
      val requests = new RequestMap[Int]
      val (id, promise) = requests.open()
      requests.get(id) mustEqual Option(promise)
    }

    "get with non-existing" in {
      val requests = new RequestMap[Int]
      requests.get(1) mustEqual None
    }

    "usable subject" in {
      val requests = new RequestMap[Int]
      val (_, subject) = requests.open()
      subject.onNext(1)
      subject.onComplete()
      subject.lastL.runAsync.map(_ mustEqual 1)
    }
  }
}
