package it.unibo.agar.view

import akka.actor.typed.ActorRef
import it.unibo.agar.model.MockGameStateManager
import it.unibo.agar.actors.DistributedGameStateManager

import java.awt.Graphics2D
import scala.swing.*

class LocalView(
                 manager: DistributedGameStateManager,
                 playerId: String) extends MainFrame:

  title = s"Agar.io - Local View ($playerId)"
  preferredSize = new Dimension(400, 400)

  // Override window closing behavior
  override def closeOperation(): Unit = {
    println(s"User closing window for player: $playerId")
    manager.removePlayer(playerId) // Remove this player from the world
    dispose()
    System.exit(0)
  }

  // Also handle window events
  peer.addWindowListener(new java.awt.event.WindowAdapter {
    override def windowClosing(e: java.awt.event.WindowEvent): Unit = {
      closeOperation()
    }
  })

  contents = new Panel:
    listenTo(keys, mouse.moves)
    focusable = true
    requestFocusInWindow()

    override def paintComponent(g: Graphics2D): Unit =
      super.paintComponent(g)
      val world = manager.getWorld
      val playerOpt = world.players.find(_.id == playerId)
      val (offsetX, offsetY) = playerOpt
        .map(p => (p.x - size.width / 2.0, p.y - size.height / 2.0))
        .getOrElse((0.0, 0.0))
      AgarViewUtils.drawWorld(g, world, offsetX, offsetY)

    reactions += { case e: event.MouseMoved =>
      val mousePos = e.point
      val playerOpt = manager.getWorld.players.find(_.id == playerId)
      playerOpt.foreach: player =>
        val dx = (mousePos.x - size.width / 2) * 0.01
        val dy = (mousePos.y - size.height / 2) * 0.01
        manager.movePlayerDirection(playerId, dx, dy)
      repaint()
    }