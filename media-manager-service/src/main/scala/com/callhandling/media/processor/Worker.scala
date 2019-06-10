package com.callhandling.media.processor

import akka.actor.Actor
import com.callhandling.media.streams.{InputReader, OutputWriter}

object Worker {

}

class Worker[R, W](reader: R, writer: W)
    (implicit inputStream: InputReader[R], outputWriter: OutputWriter[W]) extends Actor {
  override def receive = ???
}
