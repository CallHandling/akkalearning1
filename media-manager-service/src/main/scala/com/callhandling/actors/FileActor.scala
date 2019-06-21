package com.callhandling.actors

import akka.actor.{ActorLogging, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.ClusterSharding
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.media.OutputFormat
import com.callhandling.media.converters.{NoProgress, OutputArgs}
import com.callhandling.media.processor.AudioProcessor
import com.callhandling.media.processor.AudioProcessor._

object FileActor {
  val RegionName = "FileManager"

  def props(converterSystem: ActorSystem): Props = Props(new FileActor(converterSystem))

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
  final case class GetConversionStatus(format: String)

  // Data
  sealed trait Data
  final case class Details(
      id: String, filename: String, description: String) extends Data
  final case class Conversion(
      details: Details,
      progresses: Vector[FormatProgress]) extends Data
}

class FileActor(converterSystem: ActorSystem) extends FSM[State, Data] with Stash with ActorLogging {
  import FileActor._

  startWith(Idle, Details("", "", ""))

  when(Idle) {
    case Event(UploadStarted, _) =>
      log.info("Upload started.")
      goto(Uploading)
  }

  when(Uploading) {
    case Event(UploadCompleted, _) =>
      log.info("Upload completed.")
      goto(Idle)
  }

  when(Converting) {
    case Event(formatProgress @ FormatProgress(format, _), conversion @ Conversion(_, progresses)) =>
      // Update the progress of a format, or add a new one if it does not exist.
      val i = progresses.indexWhere(progressHasFormat(format))
      stay.using(conversion.copy(progresses =
        if (i == -1) progresses :+ formatProgress
        else progresses.updated(i, formatProgress)))
    case Event(ConversionResults(mediaId, _), Conversion(details, _)) =>
      if (mediaId == details.id)
        goto(Idle).using(details)

      // Ignore results if it's meant for a different file
      else stay
  }

  whenUnhandled {
    def setData: StateFunction = {
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

    def conversionRequest: StateFunction = {
      case Event(RequestForConversion(outputArgsSet), details @ Details(id, _, _)) =>
        convert(id, outputArgsSet, Conversion(details, Vector.empty))
      case Event(RequestForConversion(outputArgsSet), conversion @ Conversion(details, progresses)) =>
        // Only the formats that have not been already added to the set should be
        // included in the conversion
        val newOutputArgsSet = outputArgsSet.filter { case OutputArgs(format, _, _, _) =>
          !progresses.exists(progressHasFormat(format))
        }

        convert(details.id, newOutputArgsSet, conversion)
    }

    def conversionStatus: StateFunction = {
      case Event(GetConversionStatus(_), _: Details) =>
        sender() ! NoProgress
        stay
      case Event(GetConversionStatus(format), Conversion(_, progresses)) =>
        sender() ! progresses.find(progressHasFormat(format)).getOrElse(NoProgress)
        stay
    }

    def convert(id: String, outputArgsSet: Vector[OutputArgs], data: Data): State = {
      val converter = ClusterSharding(converterSystem).shardRegion(AudioProcessor.RegionName)

      def processMedia(command: Any): Unit = converter ! SendToEntity(id, command)

      processMedia(SetMediaId(id))
      processMedia(SetOutputArgsSet(outputArgsSet))
      processMedia(SetAckActorRef(self))
      processMedia(StartConversion(true))

      goto(Converting).using(data)
    }

    setData orElse conversionRequest orElse conversionStatus
  }

  def progressHasFormat(format: String): FormatProgress => Boolean = _.format == format

  initialize()
}
