package it.unibo.agar.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import it.unibo.agar.model.{GameInitializer, MockGameStateManager, Player, World}

object WorldStateActor {
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[AddPlayer], name = "addPlayer"),
    new JsonSubTypes.Type(value = classOf[PlayerMoveAction], name = "playerMove"),
    new JsonSubTypes.Type(value = classOf[RegisterClient], name = "registerClient"),
    new JsonSubTypes.Type(value = classOf[RemovePlayer], name = "removePlayer"),
    new JsonSubTypes.Type(value = classOf[TickWorld.type], name = "tickWorld")
  ))
  sealed trait Command
  case class AddPlayer(playerId: String, playerData: Player) extends Command
  case class PlayerMoveAction(playerId: String, dx: Double, dy: Double) extends Command
  case class RegisterClient(replyTo: ActorRef[WorldStateClientActor.Command]) extends Command
  case class RemovePlayer(playerId: String) extends Command
  case class TickWorld(distributedGameManager: DistributedGameStateManager) extends Command

  case class WorldStateUpdate(world: World)

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      // Initialize the authoritative world state
      val width = 800
      val height = 800
      val numFoods = 100
      val foods = GameInitializer.initialFoods(numFoods, width, height)
      val gameManager = new MockGameStateManager(World(width, height, List.empty, foods))

      // Track all connected clients (one per node)
      var registeredClients = Set.empty[ActorRef[WorldStateClientActor.Command]]

      // Function to broadcast world state to all nodes
      def broadcastWorldState(): Unit = {
        val currentWorld = gameManager.getWorld
        val update = WorldStateClientActor.WorldStateUpdate(currentWorld)
        registeredClients.foreach { client =>
          client ! update  // Send to every connected node
        }
      }

      Behaviors.receiveMessage {
        case AddPlayer(playerId, playerData) =>
          gameManager.addPlayer(playerData)  // Add to authoritative state
          broadcastWorldState()  // Notify all nodes immediately
          Behaviors.same

        case RemovePlayer(playerId) =>

          // Notify all clients about removal
          val removal = WorldStateClientActor.PlayerRemoved(playerId)
          registeredClients.foreach(_ ! removal)
          gameManager.removePlayer(playerId)

          broadcastWorldState()
          Behaviors.same

        case PlayerMoveAction(playerId, dx, dy) =>
          gameManager.movePlayerDirection(playerId, dx, dy)
          broadcastWorldState()  // Broadcast movement to all nodes
          Behaviors.same

        case RegisterClient(replyTo) =>
          registeredClients = registeredClients + replyTo
          // Send current state to new client immediately
          replyTo ! WorldStateClientActor.WorldStateUpdate(gameManager.getWorld)
          Behaviors.same

        case TickWorld(distributedGameManager) =>
          gameManager.tick(distributedGameManager)  // Update physics, AI, etc.
          broadcastWorldState()  // Send updated world to all nodes
          Behaviors.same
      }
    }
  }
}
