package com.callhandling.media.processor

import akka.actor.{Actor, Props}
import com.callhandling.media.OutputFormat
import com.callhandling.media.streams.{InputReader, OutputWriter}

object AudioProcessor {
  def props[A](id: String, outputFormat: OutputFormat,
      inputReader: InputReader[A], outputWriter: OutputWriter[A]): Props =
    Props(new AudioProcessor)
}

class AudioProcessor extends Actor {
  override def receive = ???
}
