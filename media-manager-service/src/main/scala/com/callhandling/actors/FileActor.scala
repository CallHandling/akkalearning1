package com.callhandling.actors

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.callhandling.Forms.UploadFileForm
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.MediaStream
import com.callhandling.media.converters.Converter.OutputArgs
import com.callhandling.media.converters._

object FileActor {
  val RegionName = "FileManager"

  def props: Props = Props[FileActor]

  def generateId: String = java.util.UUID.randomUUID().toString

  // States
  sealed trait State
  case object Idle extends State
  case object Uploading extends State
  case object Ready extends State
  case object Converting extends State

  // Upload commands
  case object UploadStarted
  case object UploadCompleted
  final case class UploadFailed(ex: Throwable)

  // Events
  final case class SetFilename(filename: String)
  final case class SetDescription(description: String)
  final case class SetUpStream(system: ActorSystem)
  case object GetDetails
  case object Play

  // Conversion Messages/Events
  final case class RequestForConversion(outputArgs: OutputArgs)
  final case class Convert(outputArgs: OutputArgs, timeDuration: Float)
  case object CompleteConversion
  case object GetConversionStatus

  // Non-command messages
  final case class ConversionStarted(either: Either[String, String])

  // Data
  sealed trait Data
  final case class Details(filename: String, description: String) extends Data
}

class FileActor extends FSM[State, Data] with Stash with ActorLogging {
  import FileActor._

  startWith(Idle, Details("", ""))

  when(Idle) {
    case Event(UploadStarted, _) => goto(Uploading)
  }

  when(Uploading) {
    case Event(UploadCompleted, _) => goto(Ready)
  }

  whenUnhandled {
    case Event(SetDescription(description), details: Details) =>
      stay.using(details.copy(description = description))
    case Event(SetFilename(filename), details: Details) =>
      stay.using(details.copy(filename = filename))
    case Event(GetDetails, details: Details) =>
      sender() ! details
      stay
  }

  @deprecated
  private def stashAndStay(action: String) = {
    log.info(s"Data not ready for $action yet. Stashing the request for now.")
    stash()
    stay
  }

  initialize()
}
