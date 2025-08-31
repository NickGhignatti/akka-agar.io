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
  @volatile private var cachedWorld: World = World(1000, 1000, List.empty, List.empty)
  private var onPlayerRemovedCallback: (String) => Unit = _ => {}
  // Interface methods that your views will call
  def getWorld: World = cachedWorld  // Fast local access

  def setPlayerRemovedCallback(callback: (String) => Unit): Unit = {
    onPlayerRemovedCallback = callback
  }

  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit = {
    // Send command to singleton - it will process and broadcast back
    worldStateActor ! WorldStateActor.PlayerMoveAction(id, dx, dy)
  }

  def handlePlayerRemoved(playerId: String): Unit = {
    onPlayerRemovedCallback(playerId)
  }

  def removePlayer(playerId: String): Unit = {
    worldStateActor ! WorldStateActor.RemovePlayer(playerId)
  }

  def tick(): Unit = {
    worldStateActor ! WorldStateActor.TickWorld(this)
  }

  def addPlayer(player: Player): Unit = {
    worldStateActor ! WorldStateActor.AddPlayer(player.id, player)
  }

  // Called by WorldStateClientActor when updates arrive
  def updateWorldState(newWorld: World): Unit = {
    cachedWorld = newWorld
    // You could add validation or filtering here if needed
    SwingUtilities.invokeLater(() => {
      Window.getWindows.foreach { window =>
        window.repaint()
        window.revalidate() // Also revalidate
      }
    })
  }
}
