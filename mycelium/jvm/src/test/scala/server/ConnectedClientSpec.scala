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

class TestRequestHandler extends RequestHandler[ByteBuffer, String, String, String, Option[String]] {
  val clients = mutable.HashSet.empty[NotifiableClient[String]]
  val events = mutable.ArrayBuffer.empty[String]

  override def onRequest(client: NotifiableClient[String], state: Future[Option[String]], path: List[String], args: ByteBuffer) = {
    def response[S : Pickler](ts: S, reaction: Reaction): Response = {
      val r = Future.successful(Right(Pickle.intoBytes[S](ts)))
      Response(r, reaction)
    }

    def error(ts: String, reaction: Reaction): Response = {
      val r = Future.successful(Left(ts))
      Response(r, reaction)
    }

    val result = Some(path) collect {
      case "api" :: Nil =>
        val str = Unpickle[String].fromBytes(args)
        response(str.reverse, Reaction(state, Future.successful(Seq.empty)))
      case "event" :: Nil =>
        val events = Future.successful(Seq("event"))
        response(true, Reaction(state, events))
      case "state" :: Nil =>
        val pickledState = state.map(s => Right(Pickle.intoBytes(s)))
        Response(pickledState, Reaction(state, Future.successful(Seq.empty)))
      case "state" :: "change" :: Nil =>
        val otherUser = Future.successful(Option("anon"))
        response(true, Reaction(otherUser, Future.successful(Seq.empty)))
      case "broken" :: Nil =>
        error("an error", Reaction(state, Future.successful(Seq.empty)))
    }

    result getOrElse Response(Future.successful(Left("path not found")), Reaction(state, Future.successful(Seq.empty)))
  }

  override def onEvent(client: NotifiableClient[String], state: Future[Option[String]], event: String) = {
    events += event
    val downstreamEvents = Seq(s"${event}-ok")
    Reaction(state, Future.successful(downstreamEvents))
  }

  override def onClientConnect(client: NotifiableClient[String]): Reaction = {
    client.notify("started")
    clients += client
    Reaction(Future.successful(None), Future.successful(Seq.empty))
  }
  override def onClientDisconnect(client: NotifiableClient[String], state: Future[Option[String]]) = {
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
    "stops actor" in connectedActor { actor =>
      actor ! ConnectedClient.Stop
      actor ! Ping()
      expectNoMessage
    }
  }
}
