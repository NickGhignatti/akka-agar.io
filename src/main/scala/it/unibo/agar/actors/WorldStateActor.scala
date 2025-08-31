package it.unibo.agar.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import it.unibo.agar.model.{Food, MockGameStateManager, Player, World}
import scala.concurrent.duration.DurationInt
import scala.util.Random

object WorldStateActor {
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[AddPlayer], name = "addPlayer"),
    new JsonSubTypes.Type(value = classOf[PlayerMoveAction], name = "playerMove"),
    new JsonSubTypes.Type(value = classOf[RegisterClient], name = "registerClient"),
    new JsonSubTypes.Type(value = classOf[RemovePlayer], name = "removePlayer"),
    new JsonSubTypes.Type(value = classOf[EndGame], name = "endGame"),
    new JsonSubTypes.Type(value = classOf[TickWorld.type], name = "tickWorld")
  ))
  sealed trait Command
  case class AddPlayer(playerId: String, playerData: Player) extends Command
  case class PlayerMoveAction(playerId: String, dx: Double, dy: Double) extends Command
  case class RegisterClient(replyTo: ActorRef[WorldStateClientActor.Command]) extends Command
  case class RemovePlayer(playerId: String) extends Command
  case class EndGame(playerId: String) extends Command
  case object TickWorld extends Command
  case object GenerateFood extends Command

  case class WorldStateUpdate(world: World)

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      // Initialize the authoritative world state
      val width = 800
      val height = 800
      val maxFoods = 100
      val foodsPerTick = 10 // Foods to generate per tick (if below threshold)
      var gameEnded = false
      val gameManager = new MockGameStateManager(World(width, height, List.empty, List.empty))

      def generateRandomFoods(count: Int, worldWidth: Int, worldHeight: Int): List[Food] = {
        (1 to count).map { _ =>
          Food(
            id = "food-" + java.util.UUID.randomUUID().toString,
            x = Random.nextDouble() * worldWidth,
            y = Random.nextDouble() * worldHeight,
          )
        }.toList
      }

      context.system.scheduler.scheduleAtFixedRate(
        initialDelay = 3.seconds,
        interval = 3.seconds
      )(() => context.self ! GenerateFood)(context.executionContext)


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

      def broadcastEndGame(playerId: String): Unit = {
        val endGameMessage = WorldStateClientActor.EndGame(playerId)
        registeredClients.foreach(_ ! endGameMessage)
      }

      Behaviors.receiveMessage {
        case AddPlayer(playerId, playerData) =>
          gameManager.addPlayer(playerData)  // Add to authoritative state
          broadcastWorldState()  // Notify all nodes immediately
          Behaviors.same

        case RemovePlayer(playerId) =>
          gameManager.removePlayer(playerId)
          broadcastWorldState()
          Behaviors.same

        case EndGame(playerId) if !gameEnded =>
          context.log.info(s"Game ended! Winner: $playerId")
          gameEnded = true
          broadcastEndGame(playerId)
          Behaviors.same

        case EndGame(_) if gameEnded =>
          // Game already ended, ignore
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

        case GenerateFood if !gameEnded =>
          val currentWorld = gameManager.getWorld
          val currentFoodCount = currentWorld.foods.size

          if (currentFoodCount < maxFoods) {

            val actualToGenerate = math.min(foodsPerTick, maxFoods - currentFoodCount)
            val newFoods = generateRandomFoods(actualToGenerate, currentWorld.width, currentWorld.height)

            newFoods.foreach(gameManager.addFood)
            broadcastWorldState()

          }
          Behaviors.same

        case TickWorld if !gameEnded =>
          gameManager.tick()  // Update physics, AI, etc.
          broadcastWorldState()  // Send updated world to all nodes
          Behaviors.same

        case _ =>
          Behaviors.same
      }
    }
  }
}
