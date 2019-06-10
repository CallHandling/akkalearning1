package com.callhandling.media.processor

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import com.callhandling.media.OutputFormat
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.streams.{InputReader, OutputWriter}

object AudioProcessor {
  def props[R, W](
      id: String,
      outputFormats: List[OutputFormat],
      reader: R,
      writer: W,
      ackActorRef: ActorRef)
      (implicit inputStream: InputReader[R], outputWriter: OutputWriter[W]): Props =
    Props(new AudioProcessor[R, W](
      id, outputFormats, reader, writer, ackActorRef))

  // FSM States
  sealed trait ConversionStatus
  case object Ready extends ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(reason: ErrorCode) extends AnyVal with ConversionStatus
  case object Converting extends ConversionStatus

  sealed trait ErrorCode {
    def combine(error: ErrorCode): ErrorCode
  }

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

class AudioProcessor[R, W](
    id: String,
    outputFormats: List[OutputFormat],
    reader: R,
    writer: W,
    ackActorRef: ActorRef)
    (implicit inputReader: InputReader[R], outputWriter: OutputWriter[W])
    extends FSM[ConversionStatus, Data] with ActorLogging {
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
        case conversion => conversion
      }

      val remaining = conversionDataSet.filter {
        case Conversion(_, Ready, _) => true
        case Conversion(_, Converting, _) => true
        case _ => false
      }

      if (remaining.isEmpty) {
        val conversionStatus = conversionDataSet.foldLeft[ConversionStatus](Ready) {
          case (Failed(accErrorCode), Conversion(_, Failed(errorCode), _)) =>
            Failed(accErrorCode combine errorCode)
          case (_, Conversion(_, failed @ Failed(_), _)) => failed
          case (accStatus, Conversion(_, Success, _)) => accStatus
        }
        ackActorRef ! FileConversionStatus(id, conversionStatus)
      }

      goto(Ready).using(NonEmptyData(newConversionDataSet))
  }

  initialize()
}
