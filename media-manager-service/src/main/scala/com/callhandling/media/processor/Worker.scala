package com.callhandling.media.processor

import akka.actor.{Actor, Props}
import com.callhandling.media.OutputFormat
import com.callhandling.media.processor.Worker.Convert
import com.callhandling.media.io.{InputReader, InputStream, OutputWriter}

object Worker {
  def props[O, SO, SM, SI](
      id: String,
      inputStream: InputStream[SO, SM],
      output: O,
      outputFormat: OutputFormat)
      (implicit writer: OutputWriter[O, SI]): Props =
    Props(new Worker[O](id, inputStream, output, outputFormat))

  case object Convert
}

class Worker[O, SO, SM, SI](
    id: String,
    inputStream: InputStream[SO, SM],
    output: O,
    outputFormat: OutputFormat)
    (implicit writer: OutputWriter[O, SI]) extends Actor {
  override def receive = {
    case Convert =>
      val outputStream = writer.write(output, id, outputFormat)

  }
}
