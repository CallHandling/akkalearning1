package com.callhandling.actors

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.callhandling.Forms.UploadFileForm
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.actors.StreamActor.StreamInitialized
import com.callhandling.media.Converter.{OutputDetails, ProgressDetails}
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails

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
  case object Converting extends State

  // Events
  final case class SetDetails(id: String, details: Details)
  final case class SetFormDetails(id: String, uploadFileForm: UploadFileForm)
  final case class SetUpStream(system: ActorSystem)
  final case class SetStreamInfo(streams: List[StreamDetails], outputFormats: List[Format])
  case object GetFileData
  case object Play

  // Conversion Messages/Events
  final case class RequestForConversion(outputDetails: OutputDetails)
  final case class Convert(outputDetails: OutputDetails, timeDuration: Float)
  case object CompleteConversion

  // Non-command messages
  final case class ConversionStarted(either: Either[String, String])

  /**
    * Send this message to the shard region as opposed to the entity itself.
    * The shard region will the entity, or create one if it doesn't exist,
    * and forward the message to it.
    * @param id The ID of the entity.
    * @param message The message the shard region will send to the entity.
    */
  final case class SendToEntity(id: String, message: Any)

  // Data
  sealed trait Data
  final case class Details(filename: String, description: String) extends Data
  final case class FileData(
      id: String,
      details: Details,
      streams: List[StreamDetails],
      outputFormats: List[Format],
      streamRef: ActorRef) extends Data
  final case class ConversionData(fileData: FileData, progress: ProgressDetails) extends Data

  def shardRegion(system: ActorSystem): ActorRef = ClusterSharding(system).start(
    typeName = RegionName,
    entityProps = Props[FileActor],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  val extractEntityId: ShardRegion.ExtractEntityId = {
    case SendToEntity(id, message) => (id, message)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case SendToEntity(id, _) => (id.hashCode % NumberOfShards).toString
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
    case Event(msg @ RequestForConversion(OutputDetails(filename, _)), fileData: FileData) =>
      log.info("Preparing for conversion...")

      // The details to be sent should be updated according to the output details
      // for the converted file.
      val newDetails = Details(
        filename = filename,
        description = fileData.details.description)

      fileData.streamRef forward (msg, fileData.copy(details = newDetails))
      stay
    case Event(msg: Convert, fileData: FileData) =>
      log.info("Converting...")
      fileData.streamRef forward msg
      goto(Converting)
    case Event(Play, fileData: FileData) =>
      fileData.streamRef forward Play
      stay
  }

  when(Converting) {
    case Event(CompleteConversion, ConversionData(fileData, _)) =>
      log.info("Conversion Completed.")
      goto(Ready).using(fileData)
    case Event(progressDetails: ProgressDetails, fileData: FileData) =>
      logProgressAndStay(ConversionData(fileData, progressDetails))
    case Event(progressDetails: ProgressDetails, conversionData: ConversionData) =>
      logProgressAndStay(conversionData.copy(progress = progressDetails))
  }

  whenUnhandled {
    case Event(SetFormDetails(id, form), fileData: FileData) =>
      val updated = fileData.copy(id = id, details = Details(fileData.details.filename, form.description))
      sender() ! updated
      stay.using(updated)
    case Event(SetDetails(id, details), fileData: FileData) =>
      stay.using(fileData.copy(details = details))
    case Event(GetFileData, _) =>
      stashAndStay("retrieval")
    case Event(_: Convert, _) | Event(RequestForConversion(_), _) =>
      stashAndStay("conversion")
  }

  private def stashAndStay(action: String) = {
    log.info(s"Data not ready for $action yet. Stashing the request for now.")
    stash()
    stay
  }

  private def logProgressAndStay(conversionData: ConversionData) = {
    log.info("Progress Details: {}", conversionData.progress)
    stay.using(conversionData)
  }

  initialize()
}
