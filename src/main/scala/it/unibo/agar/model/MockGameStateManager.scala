package it.unibo.agar.model

import it.unibo.agar.actors.DistributedGameStateManager

trait GameStateManager:

  def getWorld: World
  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit

class MockGameStateManager(
    var world: World,
    val speed: Double = 10.0
) extends GameStateManager:

  def addPlayer(player: Player): Unit =
    world = world.addPlayer(player)

  def removePlayer(playerId: String): Unit =
    world = world.removePlayer(playerId)

  private var directions: Map[String, (Double, Double)] = Map.empty
  def getWorld: World = world

  // Move a player in a given direction (dx, dy)
  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit =
    directions = directions.updated(id, (dx, dy))

  def tick(distributedGameManager: DistributedGameStateManager): Unit =
    directions.foreach:
      case (id, (dx, dy)) =>
        world.playerById(id) match
          case Some(player) =>
            world = updateWorldAfterMovement(updatePlayerPosition(player, dx, dy), distributedGameManager)
          case None =>
          // Player not found, ignore movement

  private def updatePlayerPosition(player: Player, dx: Double, dy: Double): Player =
    val newX = (player.x + dx * speed).max(0).min(world.width)
    val newY = (player.y + dy * speed).max(0).min(world.height)
    player.copy(x = newX, y = newY)

  private def updateWorldAfterMovement(
                                        player: Player,
                                        distributedGameStateManager: DistributedGameStateManager
                                      ): World =
    val foodEaten = world.foods.filter(food => EatingManager.canEatFood(player, food))
    val playerEatsFood = foodEaten.foldLeft(player)((p, food) => p.grow(food))
    val playersEaten = world
      .playersExcludingSelf(player)
      .filter(player => EatingManager.canEatPlayer(playerEatsFood, player))
    val playerEatPlayers = playersEaten.foldLeft(playerEatsFood)((p, other) => p.grow(other))
    playersEaten.foreach(p =>
      distributedGameStateManager.removePlayer(p.id)
    )
    world
      .updatePlayer(playerEatPlayers)
      .removePlayers(playersEaten)
      .removeFoods(foodEaten)

