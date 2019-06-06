package com.callhandling.actors

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import com.callhandling.actors.FileActor.{ConversionStarted, ConvertFile, EntityMessage, PrepareConversion, SetStreamInfo}
import com.callhandling.media.{Converter, StreamDetails}
import java.io.{ByteArrayInputStream, File}
import java.nio.file.Files

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import akka.stream.scaladsl.{Sink, Source}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import com.callhandling.Forms.UploadFileFormConstant
import com.callhandling.actors.FileActor.{ConversionCompleted, ConversionStarted, ConvertFile, Details, EntityMessage, FileData, SetDetails, SetStreamInfo, SetUpStream}
import com.callhandling.media.{Converter, FFmpegConf, StreamDetails}
import com.callhandling.util.FileUtil

import scala.concurrent.Await
import scala.concurrent.duration._

object StreamActor {
  def props(system: ActorSystem): Props = Props(StreamActor(system))

  // Streaming messages
  case object Ack
  case class StreamInitialized(filename: String)
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)

  def createSink(
      system: ActorSystem,
      fileManagerRegion: ActorRef,
      fileId: String)
      (implicit timeout: Timeout) = {
    val streamActorF = fileManagerRegion ? EntityMessage(fileId, SetUpStream(system))
    val streamActor = Await.result(streamActorF, timeout.duration).asInstanceOf[ActorRef]

    Sink.actorRefWithAck(
      streamActor,
      onInitMessage = StreamInitialized,
      ackMessage = Ack,
      onCompleteMessage = StreamCompleted,
      onFailureMessage = StreamFailure)
  }
}

case class StreamActor(system: ActorSystem) extends Actor with ActorLogging {
  import StreamActor._

  def receive(bytes: ByteString): Receive = {
    case cmd @ StreamInitialized(_) =>
      log.info("Stream Initialized")

      // Inform the parent that the stream has successfully
      // initialized so it can update its state.
      context.parent ! cmd

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

    case (ConvertFile(outputDetails), fileData: FileData) =>
      log.info("Retrieving media streams...")

      val streams = fileData.streams

      def error(message: String) = {
        log.error(s"Conversion Failed: $message")
        Left(message)
      }

      val result = streams.headOption.map { stream =>
        stream.time.duration.map { timeDuration =>
          val newFileId = FileActor.generateId
          val result = Right(newFileId)

          context.parent forward ConversionStarted(result)

          val region = ClusterSharding(system).shardRegion(FileActor.RegionName)
          implicit val timeout: Timeout = 2.seconds

          val fileSink = createSink(system, region, newFileId)
          val convertedBytes = Converter(newFileId, bytes, timeDuration)(outputDetails)
          val convertedStream = Source.single(convertedBytes)
          convertedStream.runWith(fileSink)(ActorMaterializer())

          region ! EntityMessage(
            newFileId, SetDetails(id = newFileId, details = fileData.details))

          context.parent ! ConversionCompleted

          result
        } getOrElse error("Could not extract time duration.")
      } getOrElse error("No media stream available.")

      sender() ! ConversionStarted(result)
  }

  def receive: Receive = receive(ByteString.empty)
}
