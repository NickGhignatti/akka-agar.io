package it.unibo.agar.distributed.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{Behaviors, ActorContext, TimerScheduler}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import it.unibo.agar.distributed.actors.RegionManager.{RegionCommand, Subscribe, Unsubscribe, PlayerMoved, RegionEvent}
import it.unibo.agar.distributed.cluster.ClusterSetup.RegionManagerTypeKey

import scala.concurrent.duration._

object PlayerActor:

  // Messages PlayerActor can receive
  sealed trait PlayerCommand

  final case class Move(newX: Double, newY: Double) extends PlayerCommand
  final case class RegionUpdate(event: RegionEvent) extends PlayerCommand
  case object Tick extends PlayerCommand

  val TypeKey: EntityTypeKey[PlayerCommand] = EntityTypeKey("PlayerActor")

  def apply(playerId: String, initialX: Double, initialY: Double, viewRadius: Double = 30.0): Behavior[PlayerCommand] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new PlayerActor(context, timers, playerId, initialX, initialY, viewRadius).start()
      }
    }

class PlayerActor(
                   context: ActorContext[PlayerActor.PlayerCommand],
                   timers: TimerScheduler[PlayerActor.PlayerCommand],
                   playerId: String,
                   var posX: Double,
                   var posY: Double,
                   val viewRadius: Double
                 ):

  import PlayerActor.*

  private val sharding = akka.cluster.sharding.typed.scaladsl.ClusterSharding(context.system)

  // Keep track of current subscribed regions (region IDs)
  private var subscribedRegions = Set.empty[String]

  // Keep track of entities visible in LocalView: entityId -> (posX, posY)
  private var visibleEntities = Map.empty[String, (Double, Double)]

  def start(): Behavior[PlayerCommand] =
    // Schedule periodic ticks for demonstration (e.g., to update or move player)
    timers.startTimerAtFixedRate(Tick, 100.millis)

    // On start, subscribe to regions covering initial position
    subscribeToRegions()

    active()

  private def active(): Behavior[PlayerCommand] =
    Behaviors.receiveMessage {
      case Move(newX, newY) =>
        posX = newX
        posY = newY
        updateRegions()
        notifyRegionsOfMovement()
        Behaviors.same

      case RegionUpdate(event) =>
        event match
          case RegionManager.EntityEntered(id, x, y) =>
            visibleEntities += (id -> (x, y))
            // Here you can notify UI or client of new visible entity
            context.log.info(s"Player $playerId sees entity entered: $id at ($x,$y)")

          case RegionManager.EntityMoved(id, x, y) =>
            visibleEntities += (id -> (x, y))
            // Notify UI/client of entity position update
            context.log.info(s"Player $playerId sees entity moved: $id at ($x,$y)")

          case RegionManager.EntityLeft(id) =>
            visibleEntities -= id
            // Notify UI/client of entity leaving view
            context.log.info(s"Player $playerId sees entity left: $id")

        Behaviors.same

      case Tick =>
        // Example periodic behavior: could be AI movement, etc.
        Behaviors.same
    }

  // Send PlayerMoved to all relevant RegionManagers
  private def notifyRegionsOfMovement(): Unit =
    subscribedRegions.foreach { regionId =>
      val regionRef = sharding.entityRefFor(RegionManagerTypeKey, regionId)
      regionRef ! PlayerMoved(playerId, posX, posY)
    }

  private val regionEventAdapter: ActorRef[RegionEvent] = context.messageAdapter { event =>
    PlayerActor.RegionUpdate(event)
  }

  // Calculate regions overlapping local view circle and adjust subscriptions accordingly
  private def updateRegions(): Unit =
    val newRegions = overlappingRegions(posX, posY, viewRadius)
    val toUnsubscribe = subscribedRegions.diff(newRegions)
    val toSubscribe = newRegions.diff(subscribedRegions)

    toUnsubscribe.foreach { regionId =>
      val regionRef = sharding.entityRefFor(RegionManagerTypeKey, regionId)
      regionRef ! Unsubscribe(regionEventAdapter)
      subscribedRegions -= regionId
    }

    toSubscribe.foreach { regionId =>
      val regionRef = sharding.entityRefFor(RegionManagerTypeKey, regionId)
      regionRef ! Subscribe(regionEventAdapter)
      subscribedRegions += regionId
    }

  // Initial subscription on start
  private def subscribeToRegions(): Unit =
    val regions = overlappingRegions(posX, posY, viewRadius)
    regions.foreach { regionId =>
      val regionRef = sharding.entityRefFor(RegionManagerTypeKey, regionId)
      regionRef ! Subscribe(regionEventAdapter)
      subscribedRegions += regionId
    }
    notifyRegionsOfMovement() // Notify regions about initial position

  // Utility: compute regions overlapping circle
  private def overlappingRegions(x: Double, y: Double, radius: Double): Set[String] =
    // Assuming regions are 100x100 as defined in ClusterSetup
    val regionSize = 100.0
    val minX = ((x - radius) / regionSize).toInt.max(0)
    val maxX = ((x + radius) / regionSize).toInt
    val minY = ((y - radius) / regionSize).toInt.max(0)
    val maxY = ((y + radius) / regionSize).toInt

    (for
      rx <- minX to maxX
      ry <- minY to maxY
    yield s"$rx,$ry").toSet


