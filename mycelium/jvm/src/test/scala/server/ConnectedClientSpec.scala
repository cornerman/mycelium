package mycelium.server

import java.nio.ByteBuffer

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import boopickle.Default._
import mycelium.core.message._
import org.scalatest._
import monix.reactive.Observable

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class TestRequestHandler extends StatefulRequestHandler[ByteBuffer, String, Option[String]] {
  val clients = mutable.HashSet.empty[ClientId]

  override def initialState(clientId: ClientId) = Future.successful(None)

  override def onRequest(client: ClientId, state: Future[Option[String]], path: List[String], args: ByteBuffer) = {
    def deserialize[S : Pickler](ts: ByteBuffer) = Unpickle[S].fromBytes(ts)
    def serialize[S : Pickler](ts: S) = Right(Pickle.intoBytes[S](ts))
    def streamValues[S : Pickler](ts: List[S]) = Observable.fromIterable(ts.map(ts => serialize(ts)))
    def value[S : Pickler](ts: S) = Future.successful(serialize(ts))
    def valueFut[S : Pickler](ts: Future[S]) = ts.map(ts => serialize(ts))
    def error(ts: String) = Future.successful(Left(ts))

    path match {
      case "true" :: Nil =>
        Response(state, value(true))
      case "api" :: Nil =>
        val str = deserialize[String](args)
        Response(state, value(str.reverse))
      case "stream" :: Nil =>
        Response(state, streamValues(4 :: 2 :: 0 :: Nil))
      case "state" :: Nil =>
        Response(state, valueFut(state))
      case "state" :: "change" :: Nil =>
        val otherUser = Future.successful(Option("anon"))
        Response(otherUser, value(true))
      case "state" :: "fail" :: Nil =>
        val failure = Future.failed(new Exception("failed-state"))
        Response(failure, value(true))
      case "broken" :: Nil =>
        Response(state, error("an error"))
      case _ => Response(state, error("path not found"))
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
      expectMsg(SingleResponse(2, Left("path not found")))
    }

    "exception in api" in connectedActor { actor =>
      actor ! CallRequest(2, List("broken"), noArg)
      expectMsg(SingleResponse(2, Left("an error")))
    }

    "call api" in connectedActor { actor =>
      val arg = Pickle.intoBytes[String]("hans")
      actor ! CallRequest(2, List("api"), arg)

      val pickledResponse = Pickle.intoBytes[String]("snah")
      expectMsg(SingleResponse(2, Right(pickledResponse)))
    }

    "stream api" in connectedActor { actor =>
      actor ! CallRequest(1, List("stream"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Int](4)
      val pickledResponse2 = Pickle.intoBytes[Int](2)
      val pickledResponse3 = Pickle.intoBytes[Int](0)
      expectMsgAllOf(
        1 seconds,
        StreamResponse(1, Right(pickledResponse1)),
        StreamResponse(1, Right(pickledResponse2)),
        StreamResponse(1, Right(pickledResponse3)),
        StreamCloseResponse(1))
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
        SingleResponse(1, Right(pickledResponse1)),
        SingleResponse(2, Right(pickledResponse2)),
        SingleResponse(3, Right(pickledResponse3)))
    }

    "failed state stops actor" in connectedActor { (actor, handler) =>
      handler.clients.size mustEqual 1

      actor ! CallRequest(1, List("state"), noArg)
      actor ! CallRequest(2, List("state", "fail"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Option[String]](None)
      val pickledResponse2 = Pickle.intoBytes[Boolean](true)
      expectMsgAllOf(
        1 seconds,
        SingleResponse(1, Right(pickledResponse1)),
        SingleResponse(2, Right(pickledResponse2)))

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
