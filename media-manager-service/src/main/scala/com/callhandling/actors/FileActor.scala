package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, FSM, Props, Stash}
import akka.util.ByteString
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, StreamDetails}

object FileActor {
  def props(id: String): Props = Props(FileActor(id))

  final case class SetDetails(filename: String, description: String)
  case object GetMediaInformation
  case object GetOutputFormats

  // Streaming messages
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}

case class FileActor(id: String) extends FSM[] with ActorLogging with Stash {
  import FileActor._

  type State = (String, ByteString, String, List[StreamDetails], List[Format]) => Receive

  implicit val gatheringState: State = gathering

  def update(filename: String,
      fileContent: ByteString,
      description: String,
      streams: List[StreamDetails],
      outputFormats: List[Format])
      (implicit state: State): Unit =
    context.become(state(filename, fileContent, description, streams, outputFormats), discardOld = true)

  def gathering: State = (filename, fileContent, description, streams, outputFormats) => {
    case SetDetails(newFilename, newDescription) =>
      update(newFilename, fileContent, newDescription, streams, outputFormats)

    // We can't get the media information and output formats until we are done gathering them.
    // Let's stash this request for now.
    case GetMediaInformation | GetOutputFormats => stash()

    case StreamInitialized =>
      log.info("Stream initialized")
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      update(filename, fileContent ++ data, description, streams, outputFormats)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")

      val streams = StreamDetails.extractFrom(id, fileContent)
      val newOutputFormats = Converter.getOutputFormats(fileContent.toArray)

      unstashAll()
      update(filename, fileContent, description, streams, newOutputFormats)(completed)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  def completed: State = (filename, fileContent, description, streams, outputFormats) => {
    case GetMediaInformation =>
      sender() ! streams
      update(filename, fileContent, description, streams, outputFormats)
    case GetOutputFormats =>
      sender() ! outputFormats
      update(filename, fileContent, description, streams, outputFormats)
  }

  override def receive = gathering("", ByteString.empty, "", Nil, Nil)
}
