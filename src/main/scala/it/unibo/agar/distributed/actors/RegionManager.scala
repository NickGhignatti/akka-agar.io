package it.unibo.agar.distributed.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object RegionManager {

  // Messages the RegionManager can handle
  sealed trait RegionCommand

  // Subscribe to region updates
  final case class Subscribe(subscriber: ActorRef[RegionEvent]) extends RegionCommand

  // Unsubscribe from region updates
  final case class Unsubscribe(subscriber: ActorRef[RegionEvent]) extends RegionCommand

  // Notification that a player has moved to a new position (inside or outside this region)
  final case class PlayerMoved(playerId: String, posX: Double, posY: Double) extends RegionCommand

  // Messages sent from RegionManager to subscribed PlayerActors
  sealed trait RegionEvent
  final case class EntityEntered(entityId: String, posX: Double, posY: Double) extends RegionEvent
  final case class EntityMoved(entityId: String, posX: Double, posY: Double) extends RegionEvent
  final case class EntityLeft(entityId: String) extends RegionEvent

  def apply(regionId: String, minX: Double, maxX: Double, minY: Double, maxY: Double): Behavior[RegionCommand] = {
    Behaviors.setup { context =>

      // State: subscribed players and tracked entities in this region
      var subscribers = Set.empty[ActorRef[RegionEvent]]
      // Map of playerId -> (posX, posY) only for players currently inside this region
      var playersInRegion = Map.empty[String, (Double, Double)]

      // Helper to check if a position is inside this region boundaries
      def insideRegion(x: Double, y: Double): Boolean =
        x >= minX && x <= maxX && y >= minY && y <= maxY

      Behaviors.receiveMessage {
        case Subscribe(subscriber) =>
          subscribers += subscriber
          // Send current known entities to new subscriber
          playersInRegion.foreach{ case (id, (x, y)) =>
            subscriber ! EntityEntered(id, x, y)
          }
          Behaviors.same

        case Unsubscribe(subscriber) =>
          subscribers -= subscriber
          Behaviors.same

        case PlayerMoved(playerId, x, y) =>
          val isInside = insideRegion(x, y)
          val wasInside = playersInRegion.contains(playerId)

          if (isInside && !wasInside) {
            // Player entered the region
            playersInRegion += (playerId -> (x, y))
            subscribers.foreach(_ ! EntityEntered(playerId, x, y))
          } else if (isInside && wasInside) {
            // Player moved inside region
            playersInRegion += (playerId -> (x, y))
            subscribers.foreach(_ ! EntityMoved(playerId, x, y))
          } else if (!isInside && wasInside) {
            // Player left the region
            playersInRegion -= playerId
            subscribers.foreach(_ ! EntityLeft(playerId))
          }
          // If player outside & was not inside, ignore
          Behaviors.same
      }
    }
  }
}
