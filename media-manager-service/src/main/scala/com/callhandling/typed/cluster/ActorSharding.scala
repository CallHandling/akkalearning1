package com.callhandling.typed.cluster

import java.io.File
import java.util.UUID

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.persistence.cassandra.testkit.CassandraLauncher
import akka.persistence.typed.scaladsl.Effect
import com.typesafe.config.{Config, ConfigFactory}


trait ActorSharding[T] {
  def shardingCluster(clusterName: String, config: Config): ClusterSharding
  def shardingBehavior(shard: ActorRef[ClusterSharding.ShardCommand], entityId: String): Behavior[T]
}

object ActorSharding {
  def apply(actorSharding: ActorSharding[_], nPorts: Int): ClusterSharding = getClusterSharding(actorSharding, nPorts)

  def generateEntityId: String = UUID.randomUUID().toString
  def passivateCluster[E, S](context: ActorContext[_], shard: ActorRef[ClusterSharding.ShardCommand]): Effect[E, S] = {
    shard ! ClusterSharding.Passivate(context.self)
    Effect.none[E, S]
  }
  def passivateActor[E, S]: Effect[E, S] = Effect.stop[E, S]

  private val clusterSeedNodeRootPort: Int = 2552
  private var portSeq: Seq[Int] = Seq.empty
  private def getPortSeq(nPorts: Int): Seq[Int] = {
    if(nPorts > 0 && nPorts <= 100) {
      if (portSeq.isEmpty) {
        portSeq = clusterSeedNodeRootPort.to(clusterSeedNodeRootPort + (nPorts - 1))
        portSeq
      } else {
        portSeq = portSeq ++ (portSeq.last + 1).to(portSeq.last + nPorts)
        portSeq
      }
    }else Seq.empty
  }

  private def getClusterSharding(actorSharding: ActorSharding[_], nPorts: Int): ClusterSharding = {
    require(nPorts > 0)
    require(nPorts <= 100)

    val shardRegion = "ClusterSystem"

    def startClusterInSameJvm: ClusterSharding = {
      generateNewPorts.map(port => startNode(port)).head
    }

    def generateNewPorts: Seq[Int] ={
      getPortSeq(nPorts).takeRight(nPorts)
    }

    def startNode(port: Int): ClusterSharding = actorSharding.shardingCluster(shardRegion, config(port))

    def config(port: Int): Config =
      ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
    """).withFallback(ConfigFactory.load("applicationTyped.conf"))

    /**
      * To make the sample easier to run we kickstart a Cassandra instance to
      * act as the journal. Cassandra is a great choice of backend for Akka Persistence but
      * in a real application a pre-existing Cassandra cluster should be used.
      */
    def startCassandraDatabase(): Unit = {
      val databaseDirectory = new File("target/cassandra-db")
      CassandraLauncher.start(
        databaseDirectory,
        CassandraLauncher.DefaultTestConfigResource,
        clean = true,
        port = 9043)

      // shut the cassandra instance down when the JVM stops
      sys.addShutdownHook {
        CassandraLauncher.stop()
      }
    }

    startCassandraDatabase()
    startClusterInSameJvm
  }
}


