package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, Props}
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

case class FileActor(id: String) extends Actor with ActorLogging {
  import FileActor._

  def update(filename: String, fileContent: ByteString, description: String, mediaInfo: MediaInformation): Unit =
    context.become(receive(filename, fileContent, description, mediaInfo), discardOld = true)

  def receive(filename: String, fileContent: ByteString, description: String, mediaInfo: MediaInformation): Receive = {
    case SetDetails(newFilename, newDescription) =>
      update(newFilename, fileContent, newDescription, mediaInfo)
    case GetMediaInformation =>
      log.info("Media Information: {}", mediaInfo)
      sender() ! mediaInfo

    case StreamInitialized =>
      log.info("Stream initialized")
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      update(filename, fileContent ++ data, description, mediaInfo)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")
      log.info("ID: {}, Filename: {}, Description: {}, Content: {}",
        id, filename, description, fileContent)

      val newMediaInfo = MediaInformation.extractFrom(id, fileContent)
      log.info("Media Information: {}", newMediaInfo)

      update(filename, fileContent, description, newMediaInfo)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  override def receive = receive("", ByteString.empty, "", EmptyMediaInformation)
}
