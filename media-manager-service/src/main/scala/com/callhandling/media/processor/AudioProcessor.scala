package com.callhandling.media.processor

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import com.callhandling.media.OutputFormat
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.streams.{InputReader, OutputWriter}

object AudioProcessor {
  def props[A](
      id: String,
      outputFormats: List[OutputFormat],
      inputReader: InputReader[A],
      outputWriter: OutputWriter[A],
      ackActorRef: ActorRef): Props =
    Props(new AudioProcessor[A](
      id, outputFormats, inputReader, outputWriter, ackActorRef))

  // FSM States
  sealed trait ConversionStatus
  case object Ready extends ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(message: String) extends AnyVal with ConversionStatus
  case object Converting extends ConversionStatus

  // FSM Data
  sealed trait Data
  case object EmptyData extends Data
  final case class NonEmptyData[A](conversionDataSet: List[Conversion]) extends Data

  final case class Conversion(
      outputFormat: OutputFormat,
      status: ConversionStatus,
      workerRef: Option[ActorRef])

  final case class StartConversion(redoFailed: Boolean)

  /**
    * Conversion status for one output format
    */
  final case class FormatConversionStatus(format: OutputFormat, conversionStatus: ConversionStatus)

  /**
    * Conversion status for the whole file (all output formats have been considered.)
    */
  final case class FileConversionStatus(id: String, conversionStatus: ConversionStatus)
}

class AudioProcessor[A](
    id: String,
    outputFormats: List[OutputFormat],
    inputReader: InputReader[A],
    outputWriter: OutputWriter[A],
    ackActorRef: ActorRef) extends FSM[ConversionStatus, Data] with ActorLogging {
  startWith(Ready, EmptyData)

  when(Ready) {
    case Event(StartConversion(redo), data @ NonEmptyData(conversionDataSet)) =>
      val pendingFormats = conversionDataSet.filter {
        case Conversion(_, Ready, _) => true
        case Conversion(_, Failed(_), _) => redo
        case _ => false
      }

      // TODO: perform conversion here
      // Create workers if they do not exist yet.

      goto(Converting).using(data.copy(conversionDataSet = conversionDataSet.map {
        _.copy(status = Converting)
      }))
  }

  when(Converting) {
    case Event(FormatConversionStatus(format, status), NonEmptyData(conversionDataSet)) =>
      val newConversionDataSet = conversionDataSet.map {
        case conversion @ Conversion(`format`, _, _) => conversion.copy(status = status)
        case _ => conversionDataSet
      }

      val remaining = conversionDataSet.filter {
        case Conversion(_, Ready, _) => true
        case Conversion(_, Converting, _) => true
        case _ => false
      }

      if (remaining.isEmpty) {
        val conversionStatus = conversionDataSet.foldLeft[ConversionStatus](Ready) {
          case (fail @ Failed(_), _) => fail
          case
        }
        ackActorRef ! FileConversionStatus(id, ???)
      }
  }

  initialize()
}
