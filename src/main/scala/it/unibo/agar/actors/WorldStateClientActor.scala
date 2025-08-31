package it.unibo.agar.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import it.unibo.agar.model.{Player, World}

object WorldStateClientActor {
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[WorldStateUpdate], name = "worldStateUpdate"),
    new JsonSubTypes.Type(value = classOf[EndGame], name = "endGame"),
  ))
  sealed trait Command
  case class WorldStateUpdate(world: World) extends Command
  case class EndGame(playerId: String) extends Command


  def apply(distributedManager: DistributedGameStateManager): Behavior[Command] = {
    Behaviors.receiveMessage {
      case WorldStateUpdate(world) =>
        // Update the local cache
        distributedManager.updateWorldState(world)
        // Trigger UI repaint could be done here too
        Behaviors.same
      case EndGame(playerId) =>
        distributedManager.handleGameEnd(playerId)
        Behaviors.same
    }
  }
}