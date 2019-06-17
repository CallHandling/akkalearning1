package com.callhandling.media.processor

import java.io.OutputStream

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, StreamConverters}
import akka.util.ByteString
import com.callhandling.media.converters._
import com.callhandling.media.io.{BytesInlet, OutputWriter}
import com.callhandling.media.processor.AudioProcessor.{Failed, FormatConversionStatus, Success}
import com.callhandling.media.processor.Worker.Convert

object Worker {
  def props[O, SM](id: String, inlet: BytesInlet[SM], output: O)
      (implicit outputWriter: OutputWriter[O, SM], materializer: ActorMaterializer): Props =
    Props(new Worker[O, SM](id, inlet, output))

  final case class Convert(outputArgs: OutputArgs, timeDuration: Float)
}

class Worker[O, SM](id: String, inlet: BytesInlet[SM], output: O)
    (implicit write: OutputWriter[O, SM], mat: ActorMaterializer) extends Actor with ActorLogging {
  override def receive = {
    case Convert(outputArgs, timeDuration) =>
      log.info(s"Converting to ${outputArgs.format}...")

      val outlet = write.write(output, id, outputArgs.format)
      val inputStream = inlet.runWith(StreamConverters.asInputStream())
      val outletStream = StreamConverters.asOutputStream()

      val outputStream: OutputStream = outletStream.toMat(outlet)(Keep.left).run()

      val conversionError = inputStream.convert(outputStream, timeDuration, outputArgs) { progress =>
        context.parent ! progress
      }

      context.parent ! FormatConversionStatus(
        outputArgs.format, conversionError map Failed getOrElse Success)


      self ! PoisonPill
  }
}
