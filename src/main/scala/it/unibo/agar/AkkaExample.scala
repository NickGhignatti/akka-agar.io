package it.unibo.agar

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object PingPong:

  sealed trait PingPongMessage extends Message
  case class Ping(ref: ActorRef[Pong]) extends PingPongMessage
  case class Pong(ref: ActorRef[Ping]) extends PingPongMessage

  def ping(): Behavior[PingPongMessage] =
    Behaviors.setup: context =>
      Behaviors.receiveMessage:
        case Ping(ref) =>
          context.log.info("Received Ping, sending Pong" + context.self)
          ref ! Pong(context.self)
          ping()
        case Pong(_) =>
          context.log.info("Received Pong while in Ping state, ignoring")
          Behaviors.same

  def pong(): Behavior[PingPongMessage] =
    Behaviors.setup: context =>
      Behaviors.receiveMessage:
        case Pong(ref) =>
          context.log.info("Received Pong, sending Ping" + context.self)
          ref ! Ping(context.self)
          pong()
        case Ping(_) =>
          context.log.info("Received Ping while in Pong state, ignoring")
          Behaviors.same

def multipleRuns(): Unit =
  val pinger = startup("agario", 25251)(PingPong.ping())
  val ponger = startup("agario", 25252)(PingPong.pong())
  pinger ! PingPong.Ping(ponger)
