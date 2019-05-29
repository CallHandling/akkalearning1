package com.callhandling.typed.cluster

import java.io.File

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity}
import akka.cluster.typed.{Cluster, Join}
import akka.persistence.cassandra.testkit.CassandraLauncher
import com.callhandling.typed.persistence.{FileActor, PassivateCommand}
import com.typesafe.config.{Config, ConfigFactory}


object FileActorSharding {

  val shardRegion = "ClusterSystem"

  def startClusterInSameJvm: ClusterSharding = {
    startCassandraDatabase()
    startNode(2551)
    startNode(2552)
    startNode(2553)
  }

  def startNode(port: Int): ClusterSharding = {
    val system = akka.actor.ActorSystem(shardRegion, config(port))
    val systemTyped = ActorSystem.wrap(system)
    val sharding = ClusterSharding(systemTyped)
    Cluster(systemTyped).manager ! Join(Cluster(systemTyped).selfMember.address)
    sharding.init(
      Entity(
        typeKey = FileActor.entityTypeKey,
        createBehavior = entityContext => FileActor.shardingBehavior(entityContext.shard, entityContext.entityId))
      .withStopMessage(PassivateCommand))
    sharding
  }

  def config(port: Int): Config =
    ConfigFactory.parseString(s"""
      akka.remote.artery.canonical.port=$port
    """).withFallback(ConfigFactory.load("cluster.conf"))

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
      clean = false,
      port = 9042)

    // shut the cassandra instance down when the JVM stops
    sys.addShutdownHook {
      CassandraLauncher.stop()
    }
  }

}
