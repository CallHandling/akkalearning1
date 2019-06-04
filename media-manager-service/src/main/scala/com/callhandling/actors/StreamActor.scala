package com.callhandling.actors

import java.io.{ByteArrayInputStream, File}
import java.nio.file.Files

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import com.callhandling.actors.FileActor.{ConversionStarted, ConvertFile, SetStreamInfo}
import com.callhandling.media.{Converter, FFmpegConf, StreamDetails}
import com.callhandling.util.FileUtil

object StreamActor {
  // Streaming messages
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}

class StreamActor extends Actor with ActorLogging {
  import StreamActor._

  def receive(bytes: ByteString): Receive = {
    case StreamInitialized =>
      log.info("Stream Initialized")
      context.parent ! StreamInitialized
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      context.become(receive(bytes ++ data), discardOld = true)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")

      val streams = StreamDetails.extractFrom(bytes)
      val outputFormats = Converter.getOutputFormats(bytes.toArray)

      context.parent ! SetStreamInfo(streams, outputFormats)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")

    case ConvertFile(fileId, outputDetails) =>
      log.info("Converting file...")
      sender() ! ConversionStarted(FileActor.generateId)
      Converter.convertFile(fileId, FileUtil.writeToTempAndGetPath(bytes))(outputDetails)
  }

  def receive: Receive = receive(ByteString.empty)
}
