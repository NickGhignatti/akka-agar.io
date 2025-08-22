package it.unibo.agar.distributed.cluster

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import it.unibo.agar.distributed.actors.RegionManager
import it.unibo.agar.distributed.actors.RegionManager.RegionCommand

object ClusterSetup:
  val RegionManagerTypeKey: EntityTypeKey[RegionCommand] = EntityTypeKey("RegionManager")

  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      val sharding = ClusterSharding(context.system)

      sharding.init(
        Entity(RegionManagerTypeKey)(entityContext =>
          val (minX, maxX, minY, maxY) = regionBounds(entityContext.entityId)
          RegionManager(entityContext.entityId, minX, maxX, minY, maxY)
        )
      )

      Behaviors.empty
    }

  private def regionBounds(regionId: String): (Double, Double, Double, Double) = {
    val parts = regionId.split(",")
    val xIndex = parts(0).toInt
    val yIndex = parts(1).toInt

    val regionWidth = 100.0 // example size
    val regionHeight = 100.0

    val minX = xIndex * regionWidth
    val maxX = minX + regionWidth
    val minY = yIndex * regionHeight
    val maxY = minY + regionHeight

    (minX, maxX, minY, maxY)
  }

  def startCluster(): ActorSystem[Nothing] =
    ActorSystem[Nothing](apply(), "AgarCluster")
