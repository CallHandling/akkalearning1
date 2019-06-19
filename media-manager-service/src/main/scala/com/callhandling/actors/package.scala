package com.callhandling

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.callhandling.actors.FileActor.RegionName
import com.callhandling.media.processor

package object actors {
  val NumberOfShards = 50

  /**
    * Send this message to the shard region as opposed to the entity itself.
    * The shard region will find the entity, or create one if it doesn't exist,
    * and forward the message to it.
    * @param id The ID of the entity.
    * @param message The message the shard region will send to the entity.
    */
  final case class SendToEntity(id: String, message: Any)

  def shardRegion(system: ActorSystem, props: Props): ActorRef = ClusterSharding(system).start(
    typeName = RegionName,
    entityProps = props,
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case SendToEntity(id, message) => (id, message)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case SendToEntity(id, _) => (id.hashCode % NumberOfShards).toString
  }

  type AudioProcessor[I, O, SM] = processor.AudioProcessor[I, O, SM]
  type Worker[O, SM] = processor.Worker[O, SM]
}
