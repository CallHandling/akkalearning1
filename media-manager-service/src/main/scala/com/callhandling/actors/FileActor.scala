package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.util.ByteString
import com.callhandling.media.Formats.Format
import com.callhandling.media.{Converter, EmptyMediaInformation, MediaInformation}

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

case class FileActor(id: String) extends Actor with ActorLogging with Stash {
  import FileActor._

  type State = (String, ByteString, String, MediaInformation, List[Format]) => Receive

  implicit def gatheringState: State = gathering

  def update(filename: String,
      fileContent: ByteString,
      description: String,
      mediaInfo: MediaInformation,
      outputFormats: List[Format])
      (implicit state: State): Unit =
    context.become(state(filename, fileContent, description, mediaInfo, outputFormats), discardOld = true)

  def gathering: State = (filename, fileContent, description, mediaInfo, outputFormats) => {
    case SetDetails(newFilename, newDescription) =>
      update(newFilename, fileContent, newDescription, mediaInfo, outputFormats)

    // We can't get the media information and output formats until we are done gathering them.
    // Let's stash this request for now.
    case GetMediaInformation | GetOutputFormats => stash()

    case StreamInitialized =>
      log.info("Stream initialized")
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      update(filename, fileContent ++ data, description, mediaInfo, outputFormats)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")

      val newMediaInfo = MediaInformation.extractFrom(id, fileContent)
      val newOutputFormats = Converter.getOutputFormats(fileContent.toArray)

      unstashAll()
      update(filename, fileContent, description, newMediaInfo, newOutputFormats)(completed)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  def completed: State = (_, _, _, mediaInfo, outputFormats) => {
    case GetMediaInformation => sender() ! mediaInfo
    case GetOutputFormats => sender() ! outputFormats
  }

  override def receive =
    gathering("", ByteString.empty, "", EmptyMediaInformation, Nil)
}
