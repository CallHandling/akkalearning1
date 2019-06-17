package com.callhandling.media.processor

import java.io.OutputStream

import akka.actor.{Actor, ActorLogging, PoisonPill, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, StreamConverters}
import com.callhandling.media.converters._
import com.callhandling.media.io.{BytesInlet, OutputWriter}
import com.callhandling.media.processor.AudioProcessor.{Failed, FormatConversionStatus, Success}
import com.callhandling.media.processor.Worker.Convert

object Worker {
  def props[O, SM](id: String, inlet: BytesInlet[SM], output: O)
      (implicit writer: OutputWriter[O, SM], mat: ActorMaterializer): Props =
    Props(new Worker[O, SM](id, inlet, output))

  final case class Convert(outputArgs: OutputArgs, timeDuration: Float)
}

class Worker[O, SM](id: String, inlet: BytesInlet[SM], output: O)
    (implicit writer: OutputWriter[O, SM], mat: ActorMaterializer) extends Actor with ActorLogging {
  override def receive = {
    case Convert(outputArgs @ OutputArgs(_, format), timeDuration) =>
      log.info(s"Converting to $format...")

      val outlet = writer.write(output, id, format)
      val inputStream = inlet.runWith(StreamConverters.asInputStream())
      val outputStream: OutputStream = {
        val outletStream = StreamConverters.asOutputStream()
        outletStream.toMat(outlet)(Keep.left).run()
      }

      val conversionError = inputStream.convert(outputStream, timeDuration, outputArgs) { progress =>
        context.parent ! progress
      }

      context.parent ! FormatConversionStatus(
        format, conversionError map Failed getOrElse Success)

      self ! PoisonPill
  }
}