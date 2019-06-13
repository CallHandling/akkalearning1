package com.callhandling.media.processor

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.{ByteString, Timeout}
import com.callhandling.media.OutputFormat
import com.callhandling.media.processor.Worker.Convert
import com.callhandling.media.io.{InputReader, InputBytes, OutputBytes, OutputWriter}

import scala.concurrent.duration.FiniteDuration

object Worker {
  def props[SM](
      id: String,
      inputBytes: InputBytes[SM],
      outputBytes: OutputBytes,
      outputFormat: OutputFormat)
      (implicit materializer: ActorMaterializer): Props =
    Props(new Worker[SM](id, inputBytes, outputBytes, outputFormat))

  case object Convert
}

class Worker[SM](
    id: String,
    inputBytes: InputBytes[SM],
    outputBytes: OutputBytes,
    outputFormat: OutputFormat)
    (implicit materializer: ActorMaterializer) extends Actor {
  override def receive = {
    case Convert =>
      val inputStream = inputBytes.runWith(StreamConverters.asInputStream())
      inputStream.convert()
  }
}
