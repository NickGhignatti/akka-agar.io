package it.unibo.agar.actors

import akka.actor.typed.ActorRef
import it.unibo.agar.model.{Player, World}

import java.awt.Window
import javax.swing.SwingUtilities
import scala.concurrent.ExecutionContext

class DistributedGameStateManager(
                                   worldStateActor: ActorRef[WorldStateActor.Command],
                                   localPlayerId: String
                                 )(implicit ec: ExecutionContext) {

  // Local cached copy of the world state
  @volatile private var cachedWorld: World = World(800, 800, List.empty, List.empty)
  // Interface methods that your views will call
  def getWorld: World = cachedWorld  // Fast local access

  @volatile private var gameEnded = false
  private var winner = ""
  def getWinner: String = winner

  // Add callback for game end events
  private var onGameEndCallback: () => Unit = () => {}
  def setGameEndCallback(callback: () => Unit): Unit = {
    onGameEndCallback = callback
  }

  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit = {
    // Send command to singleton - it will process and broadcast back
    worldStateActor ! WorldStateActor.PlayerMoveAction(id, dx, dy)
  }

  def tick(): Unit = {
    worldStateActor ! WorldStateActor.TickWorld
  }

  def addPlayer(player: Player): Unit = {
    worldStateActor ! WorldStateActor.AddPlayer(player.id, player)
  }

  def removePlayer(playerId: String): Unit =
    worldStateActor ! WorldStateActor.RemovePlayer(playerId)

  def handleGameEnd(winnerPlayerId: String): Unit = {
    gameEnded = true
    winner = winnerPlayerId
    onGameEndCallback()
  }
  def isGameEnded: Boolean = gameEnded

  // Called by WorldStateClientActor when updates arrive
  def updateWorldState(newWorld: World): Unit = {
    if (!gameEnded) {
      cachedWorld = newWorld

      // Check for winner
      newWorld.players.find(_.mass >= 1000) match {
        case Some(winner) =>
          gameEnded = true
          worldStateActor ! WorldStateActor.EndGame(winner.id)
        case None =>
        // Continue game
      }

      // You could add validation or filtering here if needed
      SwingUtilities.invokeLater(() => {
        Window.getWindows.foreach { window =>
          window.repaint()
          window.revalidate() // Also revalidate
        }
      })
    }
  }
}
