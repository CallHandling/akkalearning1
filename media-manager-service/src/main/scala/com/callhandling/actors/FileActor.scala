package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, FSM, Props, Stash}
import akka.util.ByteString
import com.callhandling.actors.FileActor.{Data, State}
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, StreamDetails}

object FileActor {
  def props(id: String): Props = Props(FileActor(id))

  sealed trait State
  case object Idle extends State
  case object Uploading extends State

  // events
  final case class SetDetails(filename: String, description: String)
  case object GetFileData

  // Streaming messages
  case object Ack
  case object StreamInitialized extends State
  case object StreamCompleted extends State
  final case class StreamFailure(ex: Throwable) extends State

  sealed trait Data
  case object Uninitialized extends Data
  final case class Details(filename: String, description: String) extends Data
  final case class FileData(fileContent: ByteString,
    details: Details,
    streams: List[StreamDetails],
    outputFormats: List[Format]) extends Data
}

case class FileActor(id: String) extends FSM[State, Data] with ActorLogging with Stash {
  import FileActor._

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(StreamInitialized, _) =>
      sender() ! Ack
      goto(Uploading).using(FileData(ByteString.empty, Details("", ""), Nil, Nil))
  }

  when(Uploading) {
    case Event(data: ByteString, fileData: FileData) =>
      log.info("Received element: {}", data)
      sender() ! Ack
      stay.using(fileData.copy(fileContent = fileData.fileContent ++ data))
    case Event(SetDetails(filename, description), fileData: FileData) =>
      stay.using(fileData.copy(details = Details(filename = filename, description = description)))
    case Event(StreamCompleted, fileData @ FileData(fileContent, _, _, _)) =>
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
    case Event(GetFileData, _) =>
      stash()
      stay
  }

  initialize()
}
