package com.callhandling.actors

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.actors.StreamActor.StreamInitialized
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails

object FileActor {
  def props: Props = Props[FileActor]

  // States
  sealed trait State
  case object Idle extends State
  case object Uploading extends State
  case object UploadDone extends State

  // Events
  case object SetUpStream
  final case class SetDetails(id: String, details: Details)
  final case class SetStreamInfo(streams: List[StreamDetails], outputFormats: List[Format])
  case object GetFileData
  case object ConvertFile
  final case class EntityMessage(id: String, message: Any)

  sealed trait Data
  case object EmptyData extends Data
  final case class Details(filename: String, description: String) extends Data

  final case class FileData(
       fileId: String,
       details: Details,
       streams: List[StreamDetails],
       outputFormats: List[Format],
       streamActor: ActorRef) extends Data

  val NumberOfShards = 50

  def shardRegion(system: ActorSystem): ActorRef = ClusterSharding(system).start(
    typeName = "FileManager",
    entityProps = Props[FileActor],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityMessage(id, message) => (id, message)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityMessage(id, _) => (id.hashCode % NumberOfShards).toString
  }
}

class FileActor extends FSM[State, Data] with Stash with ActorLogging {
  import FileActor._

  startWith(Idle, EmptyData)

  when(Idle) {
    case Event(SetUpStream, _) =>
      val actorRef = context.actorOf(Props[StreamActor])
      sender() ! actorRef
      stay
    case Event(StreamInitialized, _) =>
      goto(Uploading)
  }

  when(Uploading) {
    // Uploading is done only when the stream information is extracted
    case Event(SetStreamInfo(streams, outputFormats), fileData: FileData) =>
      unstashAll()
      goto(UploadDone).using(fileData.copy(streams = streams, outputFormats = outputFormats))
  }

  when(UploadDone) {
    case Event(GetFileData, fileData: FileData) =>
      sender() ! fileData
      goto(Idle)
  }

  whenUnhandled {
    case Event(SetDetails(_, details), fileData: FileData) =>
      stay.using(fileData.copy(details = details))
    case Event(SetDetails(id, details), EmptyData) =>
      stay.using(FileData(id, details, Nil, Nil, ActorRef.noSender))
    case Event(GetFileData, _) =>
      log.info("Data not ready for retrieval. Stashing the request for now.")
      stash()
      stay
  }

  initialize()
}
