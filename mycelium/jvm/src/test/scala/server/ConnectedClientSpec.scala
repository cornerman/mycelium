package mycelium.server

import java.nio.ByteBuffer

import akka.actor._
import akka.testkit.{ ImplicitSender, TestActorRef, TestKit }
import boopickle.Default._
import org.scalatest._
import mycelium.core.message._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.mutable

class TestRequestHandler extends FullRequestHandler[ByteBuffer, String, String, String, Option[String]] {
  val clients = mutable.HashSet.empty[NotifiableClient[String]]
  val events = mutable.ArrayBuffer.empty[String]

  override val initialReaction = Reaction(Future.successful(None))

  override def onRequest(client: NotifiableClient[String], state: Future[Option[String]], path: List[String], args: ByteBuffer) = {
    def read[S : Pickler](ts: ByteBuffer) = Unpickle[S].fromBytes(ts)
    def write[S : Pickler](ts: S) = Right(Pickle.intoBytes[S](ts))
    def value[S : Pickler](ts: S) = Future.successful(write(ts))
    def valueFut[S : Pickler](ts: Future[S]) = ts.map(ts => write(ts))
    def error(ts: String) = Future.successful(Left(ts))

    val result = Some(path) collect {
      case "true" :: Nil =>
        Response(value(true), Reaction(state))
      case "api" :: Nil =>
        val str = read[String](args)
        Response(value(str.reverse), Reaction(state))
      case "event" :: Nil =>
        val events = Future.successful(Seq("event"))
        Response(value(true), Reaction(state, events))
      case "state" :: Nil =>
        Response(valueFut(state), Reaction(state))
      case "state" :: "change" :: Nil =>
        val otherUser = Future.successful(Option("anon"))
        Response(value(true), Reaction(otherUser))
      case "state" :: "fail" :: Nil =>
        val failure = Future.failed(new Exception("minus"))
        Response(value(true), Reaction(failure))
      case "broken" :: Nil =>
        Response(error("an error"), Reaction(state))
    }

    result getOrElse Response(error("path not found"), Reaction(state))
  }

  override def onEvent(client: NotifiableClient[String], state: Future[Option[String]], event: String) = {
    events += event
    val downstreamEvents = Seq(s"${event}-ok")
    Reaction(state, Future.successful(downstreamEvents))
  }

  override def onClientConnect(client: NotifiableClient[String], state: Future[Option[String]]): Unit = {
    client.notify("started")
    clients += client
    ()
  }
  override def onClientDisconnect(client: NotifiableClient[String], state: Future[Option[String]]): Unit = {
    clients -= client
    ()
  }
}

class ConnectedClientSpec extends TestKit(ActorSystem("ConnectedClientSpec")) with ImplicitSender with FreeSpecLike with MustMatchers {

  def requestHandler = new TestRequestHandler
  def newActor(handler: TestRequestHandler = requestHandler): ActorRef = TestActorRef(new ConnectedClient(handler))
  def connectActor(actor: ActorRef, shouldConnect: Boolean = true) = {
    actor ! ConnectedClient.Connect(self)
    if (shouldConnect) expectMsg(Notification(List("started-ok")))
    else expectNoMessage
  }
  def connectedActor(handler: TestRequestHandler = requestHandler): ActorRef = {
    val actor = newActor(handler)
    connectActor(actor)
    actor
  }

  def newActor[T](f: ActorRef => T): T = f(newActor())
  def connectedActor[T](f: ActorRef => T): T = f(connectedActor())
  def connectedActor[T](f: (ActorRef, TestRequestHandler) => T): T = {
    val handler = requestHandler
    f(connectedActor(handler), handler)
  }

  val noArg = ByteBuffer.wrap(Array.empty)
  def expectNoMessage: Unit = expectNoMessage(1 seconds)

  "unconnected" - {
    val actor = newActor()

    "no pong" in {
      actor ! Ping()
      expectNoMessage
    }

    "no call request" in {
      actor ! CallRequest(2, List("invalid", "path"), noArg)
      expectNoMessage
    }

    "stop" in {
      actor ! ConnectedClient.Stop
      connectActor(actor, shouldConnect = false)
      actor ! Ping()
      expectNoMessage
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
      expectMsg(CallResponse(2, Left("path not found")))
    }

    "exception in api" in connectedActor { actor =>
      actor ! CallRequest(2, List("broken"), noArg)
      expectMsg(CallResponse(2, Left("an error")))
    }

    "call api" in connectedActor { actor =>
      val arg = Pickle.intoBytes[String]("hans")
      actor ! CallRequest(2, List("api"), arg)

      val pickledResponse = Pickle.intoBytes[String]("snah")
      expectMsg(CallResponse(2, Right(pickledResponse)))
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
        CallResponse(1, Right(pickledResponse1)),
        CallResponse(2, Right(pickledResponse2)),
        CallResponse(3, Right(pickledResponse3)))
    }

    "failed state stops actor" in connectedActor { (actor, handler) =>
      handler.clients.size mustEqual 1

      actor ! CallRequest(1, List("state"), noArg)
      actor ! CallRequest(2, List("state", "fail"), noArg)
      actor ! CallRequest(3, List("true"), noArg)
      actor ! CallRequest(4, List("state"), noArg)

      val pickledResponse1 = Pickle.intoBytes[Option[String]](None)
      val pickledResponse2 = Pickle.intoBytes[Boolean](true)
      val pickledResponse3 = Pickle.intoBytes[Boolean](true)
      val pickledResponse4 = Pickle.intoBytes[Option[String]](Option("anon"))
      expectMsgAllOf(
        1 seconds,
        CallResponse(1, Right(pickledResponse1)),
        CallResponse(2, Right(pickledResponse2)))

      handler.clients.size mustEqual 0
    }


    "send event" in connectedActor { actor =>
      actor ! CallRequest(2, List("event"), noArg)

      val pickledResponse = Pickle.intoBytes[Boolean](true)
      expectMsgAllOf(
        1 seconds,
        Notification(List("event")),
        CallResponse(2, Right(pickledResponse)))
    }
  }

  "stop" - {
    "stops actor" in connectedActor { (actor, handler) =>
      handler.clients.size mustEqual 1
      actor ! ConnectedClient.Stop
      actor ! Ping()
      expectNoMessage
      handler.clients.size mustEqual 0
    }
  }
}
