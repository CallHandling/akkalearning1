package com.callhandling.actors

import java.nio.file.Path

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.callhandling.Forms.UploadFileForm
import akka.util.ByteString
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.actors.StreamActor.{Ack, StreamInitialized}
import com.callhandling.media.Converter.OutputDetails
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, StreamDetails}

object FileActor {
  val RegionName = "FileManager"
  val NumberOfShards = 50

  def props: Props = Props[FileActor]

  def generateId: String = java.util.UUID.randomUUID().toString

  // States
  sealed trait State
  case object Idle extends State
  case object Uploading extends State
  case object Ready extends State
  case object PreparingConversion extends State
  case object Converting extends State

  // Events
  final case class SetFormDetails(id: String, uploadFileForm: UploadFileForm)
  final case class SetUpStream(system: ActorSystem)
  final case class SetStreamInfo(streams: List[StreamDetails], outputFormats: List[Format])
  case object GetFileData
  final case class EntityMessage(id: String, message: Any)

  // Conversion Messages
  final case class ConvertFile(outputDetails: OutputDetails)
  final case class ConversionStarted(either: Either[String, String])
  case object ConversionCompleted

  // Data
  sealed trait Data
  final case class Details(filename: String, description: String) extends Data
  final case class FileData(
      id: String,
      details: Details,
      streams: List[StreamDetails],
      outputFormats: List[Format],
      streamRef: ActorRef) extends Data

  def shardRegion(system: ActorSystem): ActorRef = ClusterSharding(system).start(
    typeName = RegionName,
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

  startWith(Idle, FileData("", Details("", ""), Nil, Nil, ActorRef.noSender))

  when(Idle) {
    case Event(SetUpStream(system), fileData: FileData) =>
      val streamRef = context.actorOf(StreamActor.props(system))
      sender() ! streamRef
      stay.using(fileData.copy(streamRef = streamRef))
    case Event(StreamInitialized(filename), fileData: FileData) =>
      goto(Uploading).using(fileData.copy(details = Details(filename, fileData.details.description)))
  }

  when(Uploading) {
    // We assume that uploading is done when the stream information is extracted or available.
    case Event(SetStreamInfo(streams, outputFormats), fileData: FileData) =>
      unstashAll()
      goto(Ready).using(fileData.copy(streams = streams, outputFormats = outputFormats))
  }

  when(Ready) {
    case Event(GetFileData, fileData: FileData) =>
      sender() ! fileData
      stay
    case Event(msg @ ConvertFile(OutputDetails(filename, _)), fileData: FileData) =>
      log.info("Preparing for conversion...")

      // The details to be sent should be updated according to the output details
      // for the converted file.
      val newDetails = Details(
        filename = filename,
        description = fileData.details.description)

      fileData.streamRef forward (msg, fileData.copy(details = newDetails))
      goto(PreparingConversion)
  }

  when(PreparingConversion) {
    case Event(msg: ConversionStarted, _) =>
      log.info("Converting...")
      sender() ! msg
      goto(Converting)
  }

  when(Converting) {
    case Event(ConversionCompleted, _) =>
      log.info("Conversion Completed.")
      goto(Ready)
  }

  whenUnhandled {
    case Event(SetFormDetails(id, form), fileData: FileData) =>
      val updated = fileData.copy(id = id, details = Details(fileData.details.filename, form.description))
      sender() ! updated
      stay.using(updated)
    case Event(GetFileData, _) =>
      log.info("Data not ready for retrieval. Stashing the request for now.")
      stash()
      stay
  }

  initialize()
}
