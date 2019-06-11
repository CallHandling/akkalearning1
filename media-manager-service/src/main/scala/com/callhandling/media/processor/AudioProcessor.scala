package com.callhandling.media.processor

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.util.ByteString
import com.callhandling.media.OutputFormat
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.processor.Worker.Convert
import com.callhandling.media.io.{InputReader, OutputWriter}

object AudioProcessor {
  def props[I, O, SO, SM, SI](
      id: String,
      outputFormats: List[OutputFormat],
      input: I,
      output: O,
      ackActorRef: ActorRef)
      (implicit reader: InputReader[I, SO, SM], writer: OutputWriter[O, SI]): Props =
    Props(new AudioProcessor(id, outputFormats, input, output, ackActorRef))

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
  final case class NonEmptyData[A](conversionSet: List[Conversion]) extends Data

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

class AudioProcessor[I, O, SO, SM, SI](
    id: String,
    outputFormats: List[OutputFormat],
    input: I,
    output: O,
    ackActorRef: ActorRef)
    (implicit reader: InputReader[I, SO, SM], writer: OutputWriter[O, SI])
    extends FSM[ConversionStatus, Data] with ActorLogging {
  lazy val inputStream = InputReader.read(input, id)

  startWith(Ready, EmptyData)

  when(Ready) {
    case Event(StartConversion(redo), data @ NonEmptyData(conversionSet)) =>
      def isPending: Conversion => Boolean = {
        case Conversion(_, Ready, _) => true
        case Conversion(_, Failed(_), _) => redo
        case _ => false
      }

      val pendingFormats = conversionSet.filter(isPending)

      pendingFormats.foreach { case Conversion(format, status, workerOption) =>
        val worker = workerOption.getOrElse {
          context.actorOf(Worker.props(id, inputStream, output, format))
        }
        worker ! Convert
      }

      goto(Converting).using(data.copy(conversionSet = conversionSet.map { conversion =>
        if (isPending(conversion)) conversion.copy(status = Converting)
        else conversion
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
