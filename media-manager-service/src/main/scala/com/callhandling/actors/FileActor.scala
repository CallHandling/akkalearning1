package com.callhandling.actors

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.util.ByteString
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, StreamDetails}

object FileActor {
  def props: Props = Props[FileActor]

  sealed trait State
  case object Idle extends State
  case object Uploading extends State

  // events
  final case class SetDetails(id: String, details: Details)
  case object GetFileData
  case object ConvertFile
  final case class EntityMessage(id: String, message: Any)

  // Streaming messages
  case object Ack
  case object StreamInitialized extends State
  case object StreamCompleted extends State
  final case class StreamFailure(ex: Throwable) extends State

  sealed trait Data
  case object EmptyData extends Data
  final case class Details(filename: String, description: String) extends Data
  final case class FileData(
      fileId: String,
      fileContent: ByteString,
      details: Details,
      streams: List[StreamDetails],
      outputFormats: List[Format]) extends Data

  val NumberOfShards = 50

  def startFileSharding(system: ActorSystem): ActorRef = ClusterSharding(system).start(typeName = "FileManager",
    entityProps = props,
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId
  )

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityMessage(id, message) => (id, message)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityMessage(id, _) => (id.hashCode % NumberOfShards).toString
  }
}

class FileActor extends FSM[State, Data] with Stash {
  import FileActor._

  startWith(Idle, EmptyData)

  when(Idle) {
    case Event(StreamInitialized, _) =>
      sender() ! Ack
      goto(Uploading)
  }

  when(Uploading) {
    case Event(data: ByteString, fileData: FileData) =>
      log.info("Received element: {}", data)
      sender() ! Ack
      stay.using(fileData.copy(fileContent = fileData.fileContent ++ data))
    case Event(StreamCompleted, fileData @ FileData(id, fileContent, _, _, _)) =>
      log.info("Stream completed.")

      val streams = StreamDetails.extractFrom(id, fileContent)
      val outputFormats = Converter.getOutputFormats(fileContent.toArray)

      unstashAll()
      goto(StreamCompleted).using(fileData.copy(streams = streams, outputFormats = outputFormats))
    case Event(StreamFailure(ex), _) =>
      log.error(ex, "Stream failed.")
      goto(Idle)
  }

  when(StreamCompleted) {
    case Event(GetFileData, fileData: FileData) =>
      sender() ! fileData
      goto(Idle)
  }

  whenUnhandled {
    case Event(SetDetails(_, details), fileData: FileData) =>
      stay.using(fileData.copy(details = details))
    case Event(SetDetails(id, details), EmptyData) =>
      stay.using(FileData(id, ByteString.empty, details, Nil, Nil))
    case Event(GetFileData, _) =>
      stash()
      stay
  }

  initialize()
}
