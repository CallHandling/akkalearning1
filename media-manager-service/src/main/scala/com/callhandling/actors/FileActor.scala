package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString

object FileActor {
  def props(id: String): Props = Props(FileActor(id))

  final case class SetFilename(filename: String)
  final case class SetDescription(description: String)
  case object InfoUpdated
  case object Display

  // Streaming messages
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}

case class FileActor(id: String) extends Actor with ActorLogging {
  import FileActor._

  def update(filename: String, counter: Int, fileContent: ByteString, description: String): Unit =
    context.become(receive(filename, counter, fileContent, description), discardOld = true)

  def receive(filename: String, counter: Int, fileContent: ByteString, description: String): Receive = {
    case SetFilename(newFilename) =>
      update(newFilename, counter, fileContent, description)
      sender() ! InfoUpdated
    case SetDescription(newDescription) => update(filename, counter, fileContent, newDescription)
    case StreamInitialized =>
      log.info("Stream initialized")
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      update(filename, counter, fileContent ++ data, description)
      sender() ! Ack
    case StreamCompleted => log.info("Stream completed.")
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
    case Display =>
      println(s"ID: $id, Filename: $filename, Description: $description, Content: $fileContent Counter: $counter")
  }

  override def receive = receive("", 0, ByteString.empty, "")
}
