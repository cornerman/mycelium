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
  val clients = mutable.HashMap.empty[ClientIdentity, NotifiableClient[String]]
  val events = mutable.ArrayBuffer.empty[String]

  override def onRequest(client: ClientIdentity, state: Future[Option[String]], path: List[String], args: ByteBuffer) = {
    def response[S : Pickler](reaction: Reaction, ts: S): Response = {
      val r = Future.successful(Right(Pickle.intoBytes[S](ts)))
      Response(reaction, r)
    }

    def error(reaction: Reaction, ts: String): Response = {
      val r = Future.successful(Left(ts))
      Response(reaction, r)
    }

    val result = Some(path) collect {
      case "api" :: Nil =>
        val str = Unpickle[String].fromBytes(args)
        response(Reaction(state, Future.successful(Seq.empty)), str.reverse)
      case "event" :: Nil =>
        val events = Future.successful(Seq("event"))
        response(Reaction(state, events), true)
      case "state" :: Nil =>
        val pickledState = state.map(s => Right(Pickle.intoBytes(s)))
        Response(Reaction(state, Future.successful(Seq.empty)), pickledState)
      case "state" :: "change" :: Nil =>
        val otherUser = Future.successful(Option("anon"))
        response(Reaction(otherUser, Future.successful(Seq.empty)), true)
      case "broken" :: Nil =>
        error(Reaction(state, Future.successful(Seq.empty)), "an error")
    }

    result getOrElse Response(Reaction(state, Future.successful(Seq.empty)), Future.successful(Left("path not found")))
  }

  override def onEvent(client: ClientIdentity, state: Future[Option[String]], event: String) = {
    events += event
    val downstreamEvents = Seq(s"${event}-ok")
    Reaction(state, Future.successful(downstreamEvents))
  }

  override def onClientConnect(client: NotifiableClient[String]): Reaction = {
    client.notify("started")
    clients += client.id -> client
    Reaction(Future.successful(None), Future.successful(Seq.empty))
  }
  override def onClientDisconnect(client: ClientIdentity, state: Future[Option[String]]) = {
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
    else expectNoMsg
  }
  def connectedActor(handler: TestRequestHandler = requestHandler): ActorRef = {
    val actor = newActor(handler)
    connectActor(actor)
    actor
  }

  def newActor[T](f: ActorRef => T): T = f(newActor())
  def connectedActor[T](f: ActorRef => T): T = f(connectedActor())

  val noArg = ByteBuffer.wrap(Array.empty)

  "unconnected" - {
    val actor = newActor()

    "no pong" in {
      actor ! Ping()
      expectNoMsg
    }

    "no call request" in {
      actor ! CallRequest(2, List("invalid", "path"), noArg)
      expectNoMsg
    }

    "stop" in {
      actor ! ConnectedClient.Stop
      connectActor(actor, shouldConnect = false)
      actor ! Ping()
      expectNoMsg
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
      expectNoMsg

    }
  }
}
