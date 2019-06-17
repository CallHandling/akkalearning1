package com.callhandling.media.processor

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.stream.ActorMaterializer
import com.callhandling.media.OutputFormat
import com.callhandling.media.converters._
import com.callhandling.media.io.{InputReader, OutputWriter}
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.processor.Worker.Convert

object AudioProcessor {
  def props[I, O, SM](
      id: String,
      outputArgsSet: List[OutputArgs],
      input: I,
      output: O,
      ackActorRef: ActorRef)
      (implicit reader: InputReader[I, SM], writer: OutputWriter[O, SM]): Props =
    Props(new AudioProcessor(id, outputArgsSet, input, output, ackActorRef))

  // FSM States
  sealed trait ConversionStatus
  case object Ready extends ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(reason: ConversionError) extends ConversionStatus
  case object Converting extends ConversionStatus

  // FSM Data
  sealed trait Data
  case object EmptyData extends Data
  final case class NonEmptyData(conversionSet: List[Conversion]) extends Data

  final case class Conversion(outputArgs: OutputArgs, status: ConversionStatus)

  final case class StartConversion(redoFailed: Boolean)

  /**
    * Conversion status for one output format
    */
  final case class FormatConversionStatus(format: OutputFormat, status: ConversionStatus)

  /**
    * Conversion status for the whole file (all output formats have been considered.)
    */
  final case class FileConversionStatus(id: String, status: ConversionStatus)
}

class AudioProcessor[I, O, SM](
    id: String,
    outputArgsSet: List[OutputArgs],
    input: I,
    output: O,
    ackActorRef: ActorRef)
    (implicit reader: InputReader[I, SM], writer: OutputWriter[O, SM])
    extends FSM[ConversionStatus, Data] with ActorLogging {
  lazy val inlet = InputReader.read(input, id)
  lazy val mediaStreams = InputReader.extractStreamDetails(input, id)

  startWith(Ready, EmptyData)

  when(Ready) {
    case Event(StartConversion(_), EmptyData) =>
      goto(Converting).using(NonEmptyData(outputArgsSet map convertFormat))
    case Event(StartConversion(redo), data @ NonEmptyData(conversionSet)) =>
      val convertedFormats = conversionSet.map {
        case Conversion(outputArgs, Ready) => convertFormat(outputArgs)
        case Conversion(outputArgs, Failed(_)) if redo => convertFormat(outputArgs)
        case conversion => conversion
      }

      goto(Converting).using(data.copy(conversionSet = convertedFormats))
  }

  when(Converting) {
    case Event(FormatConversionStatus(format, status), NonEmptyData(conversionDataSet)) =>
      val newConversionDataSet = conversionDataSet.map {
        case conversion @ Conversion(OutputArgs(_, `format`), _) =>
          conversion.copy(status = status)
        case conversion => conversion
      }

      val remaining = conversionDataSet.filter {
        case Conversion(_, Ready) => true
        case Conversion(_, Converting) => true
        case _ => false
      }

      val newState =
        if (remaining.isEmpty) {
          val conversionStatus = conversionDataSet.foldLeft[ConversionStatus](Ready) {
            case (Failed(ConversionErrorSet(errors)), Conversion(_, Failed(errorCode))) =>
              Failed(ConversionErrorSet(errorCode :: errors))
            case (_, Conversion(_, Failed(errorCode))) =>
              Failed(ConversionErrorSet(errorCode :: Nil))
            case (_, Conversion(_, failed @ Failed(_))) => failed
            case (accStatus, Conversion(_, Success)) => accStatus
          }
          ackActorRef ! FileConversionStatus(id, conversionStatus)

          goto(Ready)
        } else stay

      newState.using(NonEmptyData(newConversionDataSet))
    case Event(progressDetails: ProgressDetails, _) =>
      ackActorRef ! progressDetails
      stay
  }

  def convertFormat(outputArgs: OutputArgs) = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit class OptionToEither[S](option: Option[S]) {
      def orError[E](alternative: => E): Either[E, S] =
        option.map(Right(_)) getOrElse Left(alternative)
    }

    val conversionResult = for {
      mediaStream <- mediaStreams.headOption.orError(NoMediaStreamAvailable)
      timeDuration <- mediaStream.time.duration.orError(StreamInfoIncomplete)
      worker = context.actorOf(Worker.props(id, inlet, output))
    } yield worker ! Convert(outputArgs, timeDuration)

    Conversion(outputArgs, conversionResult match {
      case Left(error) => Failed(error)
      case Right(_) => Converting
    })
  }

  initialize()
}
