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

  sealed trait ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(reason: ConversionError) extends ConversionStatus

  // FSM States
  sealed trait State
  case object Ready extends ConversionStatus with State
  case object Converting extends ConversionStatus with State

  // FSM Data
  sealed trait Data
  case object EmptyData extends Data
  final case class NonConvertible(reason: ConversionError) extends Data
  final case class Convertible(conversionSet: List[Conversion], timeDuration: Float) extends Data

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

class AudioProcessor[I, O, M](
    id: String,
    outputArgsSet: List[OutputArgs],
    input: I,
    output: O,
    ackActorRef: ActorRef)
    (implicit reader: InputReader[I, M], writer: OutputWriter[O, M])
    extends FSM[State, Data] with ActorLogging {
  lazy val inlet = InputReader.read(input, id)
  lazy val mediaStreams = InputReader.extractStreamDetails(input, id)

  implicit val mat: ActorMaterializer = ActorMaterializer()

  startWith(Ready, EmptyData)

  when(Ready) {
    case Event(StartConversion(_), EmptyData) =>
      val data = prepareConversion match {
        case Left(error) => NonConvertible(error)
        case Right(timeDuration) =>
          Convertible(outputArgsSet.map(Conversion(_, Converting)), timeDuration)
      }
      goto(Converting).using(data)
    case Event(StartConversion(redo), data @ Convertible(conversionSet, _)) =>
      def startConversion: Conversion => Conversion = _.copy(status = Converting)

      val convertedFormats = conversionSet.map {
        case conversion @ Conversion(_, Ready) => startConversion(conversion)
        case conversion @ Conversion(_, Failed(_)) if redo => startConversion(conversion)
        case conversion => conversion
      }

      goto(Converting).using(data.copy(conversionSet = convertedFormats))
  }

  when(Converting) {
    case Event(FormatConversionStatus(format, status), data @ Convertible(conversionSet, _)) =>
      val newConversionSet = conversionSet.map {
        case conversion @ Conversion(OutputArgs(_, `format`), _) =>
          conversion.copy(status = status)
        case conversion => conversion
      }

      val remaining = conversionSet.filter(_.status == Converting)

      val newState =
        if (remaining.isEmpty) {
          val conversionStatus = conversionSet.foldLeft[ConversionStatus](Ready) {
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

      newState.using(data.copy(conversionSet = newConversionSet))
    case Event(progressDetails: ProgressDetails, _) =>
      ackActorRef ! progressDetails
      stay
  }

  def prepareConversion: Either[ConversionError, Float] = {
    implicit class OptionToEither[S](option: Option[S]) {
      def asEither[E](alternative: => E): Either[E, S] =
        option.map(Right(_)) getOrElse Left(alternative)
    }

    for {
      // Get the media stream information.
      mediaStream <- mediaStreams.headOption.asEither(NoMediaStreamAvailable)

      // Extract the time duration. This will be needed to compute the
      // progress percentage.
      timeDuration <- mediaStream.time.duration.asEither(StreamInfoIncomplete)
    } yield timeDuration
  }

  onTransition {
    case Ready -> Converting =>
      log.info("Converting...")
      nextStateData match {
        case Convertible(conversionSet, timeDuration) => conversionSet.foreach {
          case Conversion(outputArgs, Converting) =>
            val worker = context.actorOf(Worker.props(id, inlet, output))
            worker ! Convert(outputArgs, timeDuration)
        }
      }
    case Converting -> Ready => log.info("Conversion completed.")
  }

  initialize()
}
