package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString
import com.callhandling.MediaInformation

object FileActor {
  def props(id: String): Props = Props(FileActor(id))

  final case class SetDetails(filename: String, description: String)

  // Streaming messages
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}

case class FileActor(id: String) extends Actor with ActorLogging {
  import FileActor._

  def update(filename: String, fileContent: ByteString, description: String): Unit =
    context.become(receive(filename, fileContent, description), discardOld = true)

  def receive(filename: String, fileContent: ByteString, description: String): Receive = {
    case SetDetails(newFilename, newDescription) => update(newFilename, fileContent, newDescription)

    case StreamInitialized =>
      log.info("Stream initialized")
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      update(filename, fileContent ++ data, description)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")
      log.info("ID: {}, Filename: {}, Description: {}, Content: {}",
        id, filename, description, fileContent)
      MediaInformation.extractFrom(id, fileContent)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  override def receive = receive("", ByteString.empty, "")
}
