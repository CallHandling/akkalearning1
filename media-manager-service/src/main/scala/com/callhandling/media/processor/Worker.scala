package com.callhandling.media.processor

import akka.actor.{Actor, Props}
import com.callhandling.media.OutputFormat
import com.callhandling.media.processor.Worker.Convert
import com.callhandling.media.streams.{InputReader, InputStream, OutputWriter}

object Worker {
  def props[O](
      id: String,
      inputStream: InputStream,
      output: O,
      outputFormat: OutputFormat)
      (implicit writer: OutputWriter[O]): Props =
    Props(new Worker[O](id, inputStream, output, outputFormat))

  case object Convert
}

class Worker[O](
    id: String,
    inputStream: InputStream,
    output: O,
    outputFormat: OutputFormat)
    (implicit writer: OutputWriter[O]) extends Actor {
  override def receive = {
    case Convert =>
  }
}
