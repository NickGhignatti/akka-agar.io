package it.unibo.agar.view

import it.unibo.agar.actors.DistributedGameStateManager
import it.unibo.agar.model.MockGameStateManager

import java.awt.Color
import java.awt.Graphics2D
import scala.swing.*

class GlobalView(manager: DistributedGameStateManager) extends MainFrame:

  title = "Agar.io - Global View"
  preferredSize = new Dimension(800, 800)

  contents = new Panel:
    override def paintComponent(g: Graphics2D): Unit =
      super.paintComponent(g)
      val world = manager.getWorld
      AgarViewUtils.drawWorld(g, world)
