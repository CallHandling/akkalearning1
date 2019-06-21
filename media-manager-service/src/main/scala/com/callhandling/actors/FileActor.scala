package com.callhandling.actors

import akka.actor.{ActorLogging, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.ClusterSharding
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.media.OutputFormat
import com.callhandling.media.converters.Converter.{OutputArgs, Progress}
import com.callhandling.media.processor.AudioProcessor
import com.callhandling.media.processor.AudioProcessor.{ConversionStatus, FormatProgress, ConversionResults, SetAckActorRef, SetMediaId, SetOutputArgsSet, StartConversion}

object FileActor {
  val RegionName = "FileManager"

  def props(system: ActorSystem): Props = Props(new FileActor(system))

  def generateId: String = java.util.UUID.randomUUID().toString

  // States
  sealed trait State
  case object Idle extends State
  case object Uploading extends State
  case object Converting extends State

  // Upload commands
  case object UploadStarted
  case object UploadCompleted
  final case class UploadFailed(ex: Throwable)

  // Events
  final case class SetId(id: String)
  final case class SetFilename(filename: String)
  final case class SetDescription(description: String)
  case object GetDetails
  case object Play

  // Conversion Messages/Events
  final case class RequestForConversion(outputArgsSet: Vector[OutputArgs])

  // Data
  sealed trait Data
  final case class Details(
      id: String, filename: String, description: String) extends Data
  final case class Conversion(
      details: Details,
      conversions: Vector[FormatProgress]) extends Data
}

class FileActor(system: ActorSystem) extends FSM[State, Data] with Stash with ActorLogging {
  import FileActor._

  startWith(Idle, Details("", "", ""))

  when(Idle) {
    case Event(UploadStarted, _) =>
      log.info("Upload started.")
      goto(Uploading)
    case Event(RequestForConversion(outputArgsSet), details @ Details(id, _, _)) =>
      val converter = ClusterSharding(system).shardRegion(AudioProcessor.RegionName)

      def processMedia(command: Any): Unit = converter ! SendToEntity(id, command)

      processMedia(SetMediaId(id))
      processMedia(SetOutputArgsSet(outputArgsSet))
      processMedia(SetAckActorRef(self))
      processMedia(StartConversion(true))

      goto(Converting).using(Conversion(details, Vector.empty))
  }

  when(Uploading) {
    case Event(UploadCompleted, _) =>
      log.info("Upload completed.")
      goto(Idle)
  }

  when(Converting) {
    case Event(FormatProgress(format, progress), conversion @ Conversion(_, conversions)) =>
      stay.using(conversion.copy(conversions = conversions.collect {
        case formatProgress @ FormatProgress(`format`, _) =>
          formatProgress.copy(progress = progress)
        case formatProgress => formatProgress
      }))
    case Event(ConversionResults(mediaId, _), Conversion(details, _)) =>
      if (mediaId == details.id)
        goto(Idle).using(details)

      // Ignore results if it's meant for a different file
      else stay
  }

  whenUnhandled {
    case Event(SetId(id), details: Details) =>
      stay.using(details.copy(id = id))
    case Event(SetDescription(description), details: Details) =>
      stay.using(details.copy(description = description))
    case Event(SetFilename(filename), details: Details) =>
      stay.using(details.copy(filename = filename))
    case Event(GetDetails, details: Details) =>
      sender() ! details
      stay
  }

  initialize()
}
