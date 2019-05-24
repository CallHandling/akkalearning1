package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, Props, Stash}
import akka.util.ByteString
import com.callhandling.{EmptyMediaInformation, MediaInformation}

object FileActor {
  def props(id: String): Props = Props(FileActor(id))

  final case class SetDetails(filename: String, description: String)
  case object GetMediaInformation

  // Streaming messages
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}

case class FileActor(id: String) extends Actor with ActorLogging with Stash {
  import FileActor._

  type State = (String, ByteString, String, MediaInformation) => Receive

  implicit def gatheringState: State = gathering

  def update(filename: String,
      fileContent: ByteString,
      description: String,
      mediaInfo: MediaInformation)
      (implicit state: State): Unit =
    context.become(state(filename, fileContent, description, mediaInfo), discardOld = true)

  def gathering: State = (filename, fileContent, description, mediaInfo) => {
    case SetDetails(newFilename, newDescription) =>
      update(newFilename, fileContent, newDescription, mediaInfo)

    // We can't get the media information until we are done gathering it. Stashing if for now.
    case GetMediaInformation => stash()

    case StreamInitialized =>
      log.info("Stream initialized")
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      update(filename, fileContent ++ data, description, mediaInfo)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")
      val newMediaInfo = MediaInformation.extractFrom(id, fileContent)
      unstashAll()
      update(filename, fileContent, description, newMediaInfo)(completed)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  def completed: State = (filename, fileContent, description, mediaInfo) => {
    case GetMediaInformation => sender() ! mediaInfo
  }

  override def receive = gathering("", ByteString.empty, "", EmptyMediaInformation)
}
