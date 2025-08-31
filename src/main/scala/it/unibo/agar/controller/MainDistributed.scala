package it.unibo.agar.controller

import akka.actor.CoordinatedShutdown
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.typed.{Cluster, ClusterSingleton, Join, SingletonActor}
import com.typesafe.config.ConfigFactory
import it.unibo.agar.actors.{DistributedGameStateManager, WorldStateActor, WorldStateClientActor}
import it.unibo.agar.model.{AIMovement, GameInitializer, MockGameStateManager, Player, World}
import it.unibo.agar.seeds
import it.unibo.agar.view.{GlobalView, LocalView}

import java.awt.Window
import java.util.{Timer, TimerTask}
import javax.swing.SwingUtilities
import scala.concurrent.ExecutionContext
import scala.swing.Swing.onEDT
import scala.swing.{Frame, SimpleSwingApplication}
import scala.util.Random

case class MainDistributed(port: Int) extends SimpleSwingApplication:

  private val width = 1000
  private val height = 1000
  private val numFoods = 100
  private val players = GameInitializer.initialPlayers(4, width, height)
  private val foods = GameInitializer.initialFoods(numFoods, width, height)
  private val manager = new MockGameStateManager(World(width, height, players, foods))

  override def top: Frame =
    //it.unibo.agar.startup(port = port)(Behaviors.empty)
    val config = ConfigFactory
      .parseString(s"""akka.remote.artery.canonical.port=$port""")
      .withFallback(ConfigFactory.load("agario.conf"))

    val system = ActorSystem(Behaviors.setup[Nothing] { context =>
      implicit val ec: ExecutionContext = context.executionContext

      val coordShutdown = CoordinatedShutdown(context.system)

      // Create world state (singleton)
      val singletonManager = ClusterSingleton(context.system)
      val worldState = singletonManager.init(
        SingletonActor(WorldStateActor(), "WorldState")
      )

      // Create player actor
      val playerId = "player-" + java.util.UUID.randomUUID().toString
      val distributedManager = new DistributedGameStateManager(worldState, playerId)

      val clientActor = context.spawn(
        WorldStateClientActor(distributedManager),
        "worldStateClient"
      )

      worldState ! WorldStateActor.RegisterClient(clientActor)

      val newPlayer = Player(playerId, Random.nextInt(width), Random.nextInt(height), 120) // Starting position/mass
      distributedManager.addPlayer(newPlayer)

      new GlobalView(distributedManager).open()
      new LocalView(distributedManager, playerId).open()

      distributedManager.setPlayerRemovedCallback(removedPlayerId => {
        if (removedPlayerId == playerId) {
          SwingUtilities.invokeLater(() => {
            coordShutdown.run(CoordinatedShutdown.UnknownReason)
          })
        }
      })

      // Add shutdown hook for graceful exit
      sys.addShutdownHook {
        println(s"Application shutting down - removing player: $playerId")
        distributedManager.removePlayer(playerId)
        Thread.sleep(500) // Give time for message to be sent
      }

      val timer = new Timer()
      val task: TimerTask = new TimerTask:
        override def run(): Unit =
          distributedManager.tick() // This goes to singleton
          onEDT(Window.getWindows.foreach(_.repaint()))
      timer.scheduleAtFixedRate(task, 0, 30)

      Behaviors.empty
    }, "AgarCluster", config)

    new Frame { visible = false }


@main
def openGame(port: Int) =
  MainDistributed(port).top