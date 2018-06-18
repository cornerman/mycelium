package mycelium.server

import java.nio.ByteBuffer

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import boopickle.Default._
import mycelium.core.message._
import org.scalatest._
import monix.reactive.Observable
import monix.eval.Task

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class TestRequestHandler extends StatefulRequestHandler[ByteBuffer, String, Option[String]] {
  val clients = mutable.HashSet.empty[ClientId]

  override val initialState = Future.successful(None)

  override def onRequest(client: ClientId, state: Future[Option[String]], path: List[String], args: ByteBuffer) = {
    def deserialize[S : Pickler](ts: ByteBuffer) = Unpickle[S].fromBytes(ts)
    def serialize[S : Pickler](ts: S) = Pickle.intoBytes[S](ts)
    def streamValues[S : Pickler](ts: List[S]) = Task(Right(Observable.fromIterable(ts.map(ts => serialize(ts)))))
    def value[S : Pickler](ts: S) = Task(Right(serialize(ts)))
    def valueFut[S : Pickler](ts: Future[S]) = Task.fromFuture(ts.map(ts => Right(serialize(ts))))
    def error(ts: String) = Task(Left(ts))

    path match {
      case "true" :: Nil =>
        ResponseValue(state, value(true))
      case "api" :: Nil =>
        val str = deserialize[String](args)
        ResponseValue(state, value(str.reverse))
      case "stream" :: Nil =>
        ResponseStream(state, streamValues(4 :: 3 :: 2 :: 1 :: 0 :: Nil))
      case "state" :: Nil =>
        ResponseValue(state, valueFut(state))
      case "state" :: "change" :: Nil =>
        val otherUser = Future.successful(Option("anon"))
        ResponseValue(otherUser, value(true))
      case "state" :: "fail" :: Nil =>
        val failure = Future.failed(new Exception("failed-state"))
        ResponseValue(failure, value(true))
      case "value-broken" :: Nil =>
        ResponseValue(state, error("an error"))
      case "stream-broken" :: Nil =>
        ResponseStream(state, error("an error"))
      case _ => ResponseValue(state, error("path not found"))
    }
  }

  override def onClientConnect(client: ClientId, state: Future[Option[String]]): Unit = {
    clients += client
    ()
  }
  override def onClientDisconnect(client: ClientId, state: Future[Option[String]], reason: DisconnectReason): Unit = {
    clients -= client
    ()
  }
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers {
  import monix.execution.Scheduler.Implicits.global

  def requestHandler = new TestRequestHandler
  def newActor(handler: TestRequestHandler = requestHandler): ActorRef = TestActorRef(new ConnectedClient(handler))
  def connectActor(actor: ActorRef) = {
    actor ! ConnectedClient.Connect(self)
  }
  def connectedActor(handler: TestRequestHandler = requestHandler): ActorRef = {
    val actor = newActor(handler)
    connectActor(actor)
    expectNoMessage(0.1 seconds)
    handler.clients.size mustEqual 1
    actor
  }

  def newActor[T](f: ActorRef => T): T = f(newActor())
  def connectedActor[T](f: ActorRef => T): T = f(connectedActor())
  def connectedActor[T](f: (ActorRef, TestRequestHandler) => T): T = {
    val handler = requestHandler
    f(connectedActor(handler), handler)
  }

  val noArg = ByteBuffer.wrap(Array.empty)
  override def expectNoMessage(): Unit = expectNoMessage(1 seconds)

  "unconnected" - {
    val actor = newActor()

    "no pong" in {
      actor ! Ping()
      expectNoMessage()
    }

    "no call request" in {
      actor ! CallRequest(2, List("invalid", "path"), noArg)
      expectNoMessage()
    }

    "stop" in {
      actor ! ConnectedClient.Stop
      connectActor(actor)
      actor ! Ping()
      expectNoMessage()
    }
  }

  "ping" - {
    "expect pong" in connectedActor { actor =>
      actor ! Ping()
      expectMsg(Pong())
    }
  }

  "call request" - {
    "invalid path" in connectedActor { actor =>
      actor ! CallRequest(2, List("invalid", "path"), noArg)
      expectMsg(FailureResponse(2, "path not found"))
    }

    "exception in value api" in connectedActor { actor =>
      actor ! CallRequest(2, List("value-broken"), noArg)
      expectMsg(FailureResponse(2, "an error"))
    }

    "exception in stream api" in connectedActor { actor =>
      actor ! CallRequest(2, List("stream-broken"), noArg)
      expectMsg(FailureResponse(2, "an error"))
    }

    "call api" in connectedActor { actor =>
      val arg = Pickle.intoBytes[String]("hans")
      actor ! CallRequest(2, List("api"), arg)

      val pickledResponse = Pickle.intoBytes[String]("snah")
      expectMsg(SingleResponse(2, pickledResponse))
    }

    "stream api" in connectedActor { actor =>
      actor ! CallRequest(1, List("stream"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Int](4)
      val pickledResponse2 = Pickle.intoBytes[Int](3)
      val pickledResponse3 = Pickle.intoBytes[Int](2)
      val pickledResponse4 = Pickle.intoBytes[Int](1)
      val pickledResponse5 = Pickle.intoBytes[Int](0)
      expectMsg(StreamResponse(1, pickledResponse1))
      expectMsg(StreamResponse(1, pickledResponse2))
      expectMsg(StreamResponse(1, pickledResponse3))
      expectMsg(StreamResponse(1, pickledResponse4))
      expectMsg(StreamResponse(1, pickledResponse5))
      expectMsg(StreamCloseResponse(1))
    }

    "switch state" in connectedActor { actor =>
      actor ! CallRequest(1, List("state"), noArg)
      actor ! CallRequest(2, List("state", "change"), noArg)
      actor ! CallRequest(3, List("state"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Option[String]](None)
      val pickledResponse2 = Pickle.intoBytes[Boolean](true)
      val pickledResponse3 = Pickle.intoBytes[Option[String]](Option("anon"))
      expectMsgAllOf(
        1 seconds,
        SingleResponse(1, pickledResponse1),
        SingleResponse(2, pickledResponse2),
        SingleResponse(3, pickledResponse3))
    }

    "failed state stops actor" in connectedActor { (actor, handler) =>
      handler.clients.size mustEqual 1

      actor ! CallRequest(1, List("state"), noArg)
      actor ! CallRequest(2, List("state", "fail"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Option[String]](None)
      val pickledResponse2 = Pickle.intoBytes[Boolean](true)
      expectMsgAllOf(
        1 seconds,
        SingleResponse(1, pickledResponse1),
        SingleResponse(2, pickledResponse2))

      Thread.sleep(1000)
      actor ! CallRequest(3, List("true"), noArg)
      actor ! CallRequest(4, List("state"), noArg)

      expectNoMessage()

      handler.clients.size mustEqual 0
    }
  }

  "stop" - {
    "stops actor" in connectedActor { (actor, handler) =>
      handler.clients.size mustEqual 1
      actor ! ConnectedClient.Stop
      actor ! Ping()
      expectNoMessage()
      handler.clients.size mustEqual 0
    }
  }
}
