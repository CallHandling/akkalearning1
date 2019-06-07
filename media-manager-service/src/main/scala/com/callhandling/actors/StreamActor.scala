package com.callhandling.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import com.callhandling.actors.FileActor.{ConversionStarted, Convert, EntityMessage, SetStreamInfo, _}
import com.callhandling.media.{Converter, StreamDetails}

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
      fileId: String,
      filename: String)
      (implicit timeout: Timeout) = {
    val streamActorF = fileManagerRegion ? EntityMessage(fileId, SetUpStream(system))
    val streamActor = Await.result(streamActorF, timeout.duration).asInstanceOf[ActorRef]

    Sink.actorRefWithAck(
      streamActor,
      onInitMessage = StreamInitialized(filename),
      ackMessage = Ack,
      onCompleteMessage = StreamCompleted,
      onFailureMessage = StreamFailure)
  }
}

case class StreamActor(system: ActorSystem) extends Actor with ActorLogging {
  import StreamActor._

  def receive(bytes: ByteString): Receive = {
    case cmd: StreamInitialized =>
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

    case (RequestForConversion(outputDetails), fileData: FileData) =>
      log.info("Retrieving media streams...")

      val streams = fileData.streams

      def error(message: String) = {
        log.error(s"Conversion Failed: $message")
        Left(message)
      }

      val result = streams.headOption.map { stream =>
        stream.time.duration.map { timeDuration =>
          val newFileId = FileActor.generateId

          val region = ClusterSharding(system).shardRegion(FileActor.RegionName)
          implicit val timeout: Timeout = 2.seconds

          val fileSink = createSink(system, region, newFileId, fileData.details.filename)
          Source.single(bytes).runWith(fileSink)(ActorMaterializer())

          region ! EntityMessage(
            newFileId, SetDetails(id = newFileId, details = fileData.details))

          region ! EntityMessage(
            newFileId, Convert(outputDetails, timeDuration))

          Right(newFileId)
        } getOrElse error("Could not extract time duration.")
      } getOrElse error("No media stream available.")

      sender() ! ConversionStarted(result)

    case Convert(outputDetails, timeDuration) =>
      val convertedBytes = Converter.convert(bytes, timeDuration, outputDetails) { progress =>
        context.parent ! progress
      }
      context.become(receive(convertedBytes), discardOld = true)
      context.parent ! ConversionCompleted
  }

  def receive: Receive = receive(ByteString.empty)
}
