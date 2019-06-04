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

      // Inform the parent that the stream has successfully
      // initialized so it can update its state.
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

    case (ConvertFile(fileId, outputDetails), streams: List[StreamDetails]) =>
      log.info("Retrieving media streams...")

      def error(message: String) = {
        log.error(s"Conversion Failed: $message")
        Left(message)
      }

      val result = streams.headOption.map { stream =>
        stream.time.duration.map { timeDuration =>
          val inputPath = FileUtil.writeToTempAndGetPath(bytes)
          Converter.convertFile(fileId, inputPath, timeDuration)(outputDetails)
          Right(FileActor.generateId)
        } getOrElse error("Could not extract time duration.")
      } getOrElse error("No media stream available.")

      sender() ! ConversionStarted(result)
  }

  def receive: Receive = receive(ByteString.empty)
}
