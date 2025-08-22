package it.unibo.agar

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}

object MainCluster extends App {
  val system = ActorSystem[Nothing](Behaviors.setup { context =>
    val sharding = ClusterSharding(context.system)

    val TypeKey = EntityTypeKey[String]("MyEntity")

    sharding.init(Entity(TypeKey) { entityContext =>
      // Your entity actor behavior here
      Behaviors.empty
    })

    Behaviors.empty
  }, "MyCluster")
}