package com.callhandling.actors

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import com.callhandling.actors.FileActor.SetStreamInfo
import com.callhandling.media.{Converter, StreamDetails}

object StreamActor {
  // Streaming messages
  case object Ack
  case object StreamInitialized
  case object StreamCompleted
  final case class StreamFailure(ex: Throwable)
}

class StreamActor extends Actor with ActorLogging {
  import StreamActor._

  def receive(stream: ByteString): Receive = {
    case StreamInitialized =>
      log.info("Stream Initialized")
      context.parent ! StreamInitialized
      sender() ! Ack
    case data: ByteString =>
      log.info("Received element: {}", data)
      context.become(receive(stream ++ data), discardOld = true)
      sender() ! Ack
    case StreamCompleted =>
      log.info("Stream completed.")

      val streams = StreamDetails.extractFrom(stream)
      val outputFormats = Converter.getOutputFormats(stream.toArray)

      context.parent ! SetStreamInfo(streams, outputFormats)
    case StreamFailure(ex) => log.error(ex, "Stream failed.")
  }

  def receive: Receive = receive(ByteString.empty)
}
