package mycelium.akka.server

import akka.actor._
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import boopickle.Default._
import java.nio.ByteBuffer
import mycelium.core.message._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable
import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.must.Matchers

class TestRequestHandler extends FullRequestHandler[ByteBuffer, String, String, Option[String]] {
  val clients = mutable.HashSet.empty[NotifiableClient[String, Option[String]]]
  val events  = mutable.ArrayBuffer.empty[String]

  override val initialState = Future.successful(None)

  override def onRequest(
      client: NotifiableClient[String, Option[String]],
      state: Future[Option[String]],
      path: List[String],
      args: ByteBuffer,
  ) = {
    def deserialize[S: Pickler](ts: ByteBuffer) = Unpickle[S].fromBytes(ts)
    def serialize[S: Pickler](ts: S)            = Right(Pickle.intoBytes[S](ts))
    def value[S: Pickler](ts: S, events: List[String] = Nil) =
      Future.successful(ReturnValue(serialize(ts), events))
    def valueFut[S: Pickler](ts: Future[S], events: List[String] = Nil) =
      ts.map(ts => ReturnValue(serialize(ts), events))
    def error(ts: String, events: List[String] = Nil) =
      Future.successful(ReturnValue(Left(ts), events))

    path match {
      case "true" :: Nil =>
        Response(state, value(true))
      case "api" :: Nil =>
        val str = deserialize[String](args)
        Response(state, value(str.reverse))
      case "event" :: Nil =>
        val events = List("event")
        Response(state, value(true, events))
      case "state" :: Nil =>
        Response(state, valueFut(state))
      case "state" :: "change" :: Nil =>
        val otherUser = Future.successful(Option("anon"))
        Response(otherUser, value(true))
      case "state" :: "fail" :: Nil =>
        val failure = Future.failed(new Exception("minus"))
        Response(failure, value(true))
      case "broken" :: Nil =>
        Response(state, error("an error"))
      case _ => Response(state, error("path not found"))
    }
  }

  override def onEvent(
      client: NotifiableClient[String, Option[String]],
      state: Future[Option[String]],
      newEvents: List[String],
  ) = {
    events ++= newEvents
    val downstreamEvents = newEvents.map(event => s"${event}-ok")
    Reaction(state, Future.successful(downstreamEvents))
  }

  override def onClientConnect(
      client: NotifiableClient[String, Option[String]],
      state: Future[Option[String]],
  ): Unit = {
    client.notify(_ => Future.successful("started" :: Nil))
    clients += client
    ()
  }
  override def onClientDisconnect(
      client: NotifiableClient[String, Option[String]],
      state: Future[Option[String]],
      reason: DisconnectReason,
  ): Unit = {
    clients -= client
    ()
  }
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with AnyFreeSpecLike with Matchers {

  def requestHandler = new TestRequestHandler
  def newActor(handler: TestRequestHandler = requestHandler): ActorRef =
    TestActorRef(new ConnectedClient(handler))
  def connectActor(actor: ActorRef, shouldConnect: Boolean = true) = {
    actor ! ConnectedClient.Connect(self)
    if (shouldConnect) expectMsg(Notification(List("started-ok")))
    else expectNoMessage()
  }
  def connectedActor(handler: TestRequestHandler = requestHandler): ActorRef = {
    val actor = newActor(handler)
    connectActor(actor)
    actor
  }

  def newActor[T](f: ActorRef => T): T       = f(newActor())
  def connectedActor[T](f: ActorRef => T): T = f(connectedActor())
  def connectedActor[T](f: (ActorRef, TestRequestHandler) => T): T = {
    val handler = requestHandler
    f(connectedActor(handler), handler)
  }

  val noArg                            = ByteBuffer.wrap(Array.empty)
  override def expectNoMessage(): Unit = expectNoMessage(1.seconds)

  "unconnected" - {
    val actor = newActor()

    "no pong" in {
      actor ! Ping
      expectNoMessage()
    }

    "no call request" in {
      actor ! CallRequest(2, List("invalid", "path"), noArg)
      expectNoMessage()
    }

    "stop" in {
      actor ! ConnectedClient.Stop
      connectActor(actor, shouldConnect = false)
      actor ! Ping
      expectNoMessage()
    }
  }

  "ping" - {
    "expect pong" in connectedActor { actor =>
      actor ! Ping
      expectMsg(Pong)
    }
  }

  "call request" - {
    "invalid path" in connectedActor { actor =>
      actor ! CallRequest(2, List("invalid", "path"), noArg)
      expectMsg(CallResponseFailure(2, "path not found"))
    }

    "exception in api" in connectedActor { actor =>
      actor ! CallRequest(2, List("broken"), noArg)
      expectMsg(CallResponseFailure(2, "an error"))
    }

    "call api" in connectedActor { actor =>
      val arg = Pickle.intoBytes[String]("hans")
      actor ! CallRequest(2, List("api"), arg)

      val pickledResponse = Pickle.intoBytes[String]("snah")
      expectMsg(CallResponse(2, pickledResponse))
    }

    "switch state" in connectedActor { actor =>
      actor ! CallRequest(1, List("state"), noArg)
      actor ! CallRequest(2, List("state", "change"), noArg)
      actor ! CallRequest(3, List("state"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Option[String]](None)
      val pickledResponse2 = Pickle.intoBytes[Boolean](true)
      val pickledResponse3 = Pickle.intoBytes[Option[String]](Option("anon"))
      expectMsgAllOf(
        1.seconds,
        CallResponse(1, pickledResponse1),
        CallResponse(2, pickledResponse2),
        CallResponse(3, pickledResponse3),
      )
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
        1.seconds,
        CallResponse(1, pickledResponse1),
        CallResponse(2, pickledResponse2),
      )

      handler.clients.size mustEqual 0
    }

    "send event" in connectedActor { actor =>
      actor ! CallRequest(2, List("event"), noArg)

      val pickledResponse = Pickle.intoBytes[Boolean](true)
      expectMsgAllOf(
        1.seconds,
        Notification(List("event")),
        CallResponse(2, pickledResponse),
      )
    }
  }

  "stop" - {
    "stops actor" in connectedActor { (actor, handler) =>
      handler.clients.size mustEqual 1
      actor ! ConnectedClient.Stop
      actor ! Ping
      expectNoMessage()
      handler.clients.size mustEqual 0
    }
  }
}
