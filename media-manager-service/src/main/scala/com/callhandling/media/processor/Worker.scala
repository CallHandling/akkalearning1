package com.callhandling.media.processor

import akka.actor.{Actor, PoisonPill, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import com.callhandling.media.converters._
import com.callhandling.media.io.{BytesInlet, OutputWriter}
import com.callhandling.media.processor.AudioProcessor.{Failed, Success}
import com.callhandling.media.processor.Worker.Convert

object Worker {
  def props[O, SM](id: String, inlet: BytesInlet[SM], output: O)
      (implicit outputWriter: OutputWriter[O], materializer: ActorMaterializer): Props =
    Props(new Worker[O, SM](id, inlet, output))

  final case class Convert(outputArgs: OutputArgs, timeDuration: Float)
}

class Worker[O, SM](id: String, inlet: BytesInlet[SM], output: O)
    (implicit outputWriter: OutputWriter[O], materializer: ActorMaterializer) extends Actor {
  override def receive = {
    case Convert(outputArgs, timeDuration) =>
      val outlet = outputWriter.write(output, id, outputArgs.format)
      val inputStream = inlet.runWith(StreamConverters.asInputStream())
      val outletStream = StreamConverters.asOutputStream()

      outletStream.toMat(outlet) { (outputStream, _) =>
        val conversionError = inputStream.convert(outputStream, timeDuration, outputArgs) { progress =>
          context.parent ! progress
        }

        context.parent ! (conversionError map Failed getOrElse Success)
      }

      self ! PoisonPill
  }
}
