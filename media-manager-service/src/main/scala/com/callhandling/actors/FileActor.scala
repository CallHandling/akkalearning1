package com.callhandling.actors

import akka.actor.{ActorLogging, ActorRef, ActorSystem, FSM, Props, Stash}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import com.callhandling.Forms.UploadFileForm
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails
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

  // Streaming messages
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)

  // Events
  final case class SetDetails(filename: String, description: String)
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

class FileActor(id: String) extends FSM[State, Data] with Stash with ActorLogging {
  import FileActor._

  startWith(Idle, Details("", ""))

  when(Idle) {
    case Event(StreamInitialized, _) => goto(Uploading)
  }

  when(Uploading) {
    case Event(StreamCompleted, _) => goto(Ready)
  }

  when(Ready) {
    case Event(GetDetails, details: Details) =>
      sender() ! details
      stay
  }

  whenUnhandled {
    case Event(SetDescription(description), details: Details) =>
      stay.using(details.copy(description = description))
    case Event(SetDetails(filename, description), _) =>
      stay.using(Details(filename, description))
    case Event(GetDetails, _) =>
      stashAndStay("retrieval")
  }

  private def stashAndStay(action: String) = {
    log.info(s"Data not ready for $action yet. Stashing the request for now.")
    stash()
    stay
  }

  initialize()
}
