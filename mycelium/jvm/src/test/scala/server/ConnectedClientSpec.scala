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

class TestRequestHandler extends FullRequestHandler[ByteBuffer, String, String, Option[String]] {
  val clients = mutable.HashSet.empty[NotifiableClient[String, Option[String]]]
  val events = mutable.ArrayBuffer.empty[String]

  override val initialState = Future.successful(None)

  override def onRequest(client: NotifiableClient[String, Option[String]], state: Future[Option[String]], path: List[String], args: ByteBuffer) = {
    def deserialize[S : Pickler](ts: ByteBuffer) = Unpickle[S].fromBytes(ts)
    def serialize[S : Pickler](ts: S) = Right(Pickle.intoBytes[S](ts))
    def streamValues[S : Pickler](ts: List[S], events: List[String] = Nil) = Observable.fromIterable(ts.map(ts => ReturnValue(serialize(ts), events)))
    def value[S : Pickler](ts: S, events: List[String] = Nil) = Future.successful(ReturnValue(serialize(ts), events))
    def valueFut[S : Pickler](ts: Future[S], events: List[String] = Nil) = ts.map(ts => ReturnValue(serialize(ts), events))
    def error(ts: String, events: List[String] = Nil) = Future.successful(ReturnValue(Left(ts), events))

    path match {
      case "true" :: Nil =>
        Response(state, value(true))
      case "api" :: Nil =>
        val str = deserialize[String](args)
        Response(state, value(str.reverse))
      case "event" :: Nil =>
        val events = List("event")
        Response(state, value(true, events))
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

  override def onEvent(client: NotifiableClient[String, Option[String]], state: Future[Option[String]], newEvents: List[String]) = {
    events ++= newEvents
    val downstreamEvents = newEvents.map(event => s"${event}-ok")
    Reaction(state, Future.successful(downstreamEvents))
  }

  override def onClientConnect(client: NotifiableClient[String, Option[String]], state: Future[Option[String]]): Unit = {
    clients += client
    ()
  }
  override def onClientDisconnect(client: NotifiableClient[String, Option[String]], state: Future[Option[String]], reason: DisconnectReason): Unit = {
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
    expectNoMessage(0.2 seconds)
  }
  def connectedActor(handler: TestRequestHandler = requestHandler): ActorRef = {
    val actor = newActor(handler)
    connectActor(actor)
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
      actor ! CallRequest(3, List("true"), noArg)
      actor ! CallRequest(4, List("state"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Option[String]](None)
      val pickledResponse2 = Pickle.intoBytes[Boolean](true)
      expectMsgAllOf(
        2 seconds,
        SingleResponse(1, Right(pickledResponse1)),
        SingleResponse(2, Right(pickledResponse2)),
        SingleResponse(3, Right(pickledResponse2)),
        ErrorResponse(4, "failed-state"))

      actor ! CallRequest(3, List("true"), noArg)
      actor ! CallRequest(4, List("state"), noArg)

      expectNoMessage()

      handler.clients.size mustEqual 0
    }


    "send event" in connectedActor { actor =>
      actor ! CallRequest(2, List("event"), noArg)

      val pickledResponse = Pickle.intoBytes[Boolean](true)
      expectMsgAllOf(
        1 seconds,
        Notification(List("event")),
        SingleResponse(2, Right(pickledResponse)))
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
