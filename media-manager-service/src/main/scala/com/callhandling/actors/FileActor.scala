package com.callhandling.actors

import java.io.{ByteArrayInputStream, File, PipedInputStream}
import java.nio.file.{Files, Paths}

import akka.actor.{Actor, ActorLogging, Props}
import akka.util.ByteString
import com.github.kokorin.jaffree.ffmpeg.{FFmpeg, PipeInput}
import com.github.kokorin.jaffree.ffprobe.FFprobe

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
      extractMediaInformation(id, fileContent)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  def extractMediaInformation(uuid: String, data: ByteString) = {
    // TODO: Consider making these two values configurable (as opposed to being hardcoded values)
    val bin = Paths.get("/usr/bin/")
    val tempDir = "/tmp/akkalearning"

    val inputStream = new ByteArrayInputStream(data.toArray)

    val path = new File(s"$tempDir/$uuid").toPath
    Files.write(path, data.toArray)

    val result = FFprobe.atPath(bin)
        .setInput(path)
        .setShowStreams(true)
        .execute()

    val details = result.getStreams.get(0)
    log.info("Details: {} {} {}", details.getBitRate, details.getCodecName, details.getCodecTagString)
  }

  override def receive = receive("", ByteString.empty, "")
}
