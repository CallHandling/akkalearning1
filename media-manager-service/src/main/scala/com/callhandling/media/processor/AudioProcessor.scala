package com.callhandling.media.processor

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.stream.ActorMaterializer
import com.callhandling.media.OutputFormat
import com.callhandling.media.converters._
import com.callhandling.media.io.{InputReader, OutputWriter}
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.processor.Worker.Convert

object AudioProcessor {
  def props[I, O, M](input: I, output: O, ackActorRef: ActorRef)
      (implicit reader: InputReader[I, M], writer: OutputWriter[O, M]): Props =
    Props(new AudioProcessor(input, output, ackActorRef))

  sealed trait ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(reason: ConversionError) extends ConversionStatus

  // FSM States
  sealed trait State
  case object Ready extends ConversionStatus with State
  case object Converting extends ConversionStatus with State

  // FSM Data
  sealed trait Data
  final case class AudioData(id: String, outputArgsSet: Vector[OutputArgs]) extends Data
  final case class Convertible(
      id: String,
      conversionSet: Vector[Conversion],
      timeDuration: Float) extends Data

  final case class Conversion(outputArgs: OutputArgs, status: ConversionStatus)

  final case class SetId(id: String)
  final case class SetOutputArgsSet(outputArgsSet: Vector[OutputArgs])
  final case class StartConversion(redoFailed: Boolean)

  /**
    * Conversion status for one output format
    */
  final case class FormatConversionStatus(format: OutputFormat, status: ConversionStatus)

  /**
    * Conversion status for the whole file (all output formats have been considered.)
    */
  final case class FileConversionStatus(id: String, status: ConversionStatus)

  final case class FormatProgress(format: OutputFormat, progress: Progress)
}

class AudioProcessor[I, O, M](input: I, output: O, ackActorRef: ActorRef)
    (implicit reader: InputReader[I, M], writer: OutputWriter[O, M])
    extends FSM[State, Data] with ActorLogging {
  implicit val mat: ActorMaterializer = ActorMaterializer()

  startWith(Ready, AudioData("", Vector()))

  def setData: StateFunction = {
    case Event(SetId(id), data: AudioData) =>
      stay.using(data.copy(id = id))
    case Event(SetOutputArgsSet(outputArgsSet), data: AudioData) =>
      stay.using(data.copy(outputArgsSet = outputArgsSet))
  }

  def startConversion: StateFunction = {
    case Event(StartConversion(_), data @ AudioData(id, outputArgsSet)) =>
      val newData = prepareConversion(id) match {
        case Left(error) =>
          ackActorRef ! error
          data
        case Right(timeDuration) =>
          Convertible(id, outputArgsSet.map(Conversion(_, Converting)), timeDuration)
      }
      goto(Converting).using(newData)
    case Event(StartConversion(redo), data @ Convertible(_, conversionSet, _)) =>
      def startConversion: Conversion => Conversion = _.copy(status = Converting)

      val convertedFormats = conversionSet.map {
        case conversion @ Conversion(_, Ready) => startConversion(conversion)
        case conversion @ Conversion(_, Failed(_)) if redo => startConversion(conversion)
        case conversion => conversion
      }

      goto(Converting).using(data.copy(conversionSet = convertedFormats))
  }

  when(Ready)(setData orElse startConversion)

  when(Converting) {
    case Event(FormatConversionStatus(format, status), data @ Convertible(id, conversionSet, _)) =>
      val newConversionSet = conversionSet.map {
        case conversion @ Conversion(OutputArgs(_, `format`), _) =>
          conversion.copy(status = status)
        case conversion => conversion
      }

      val remaining = newConversionSet.filter(_.status == Converting)

      val newState = if (remaining.isEmpty) {
        val conversionStatus = newConversionSet.foldLeft[ConversionStatus](Ready) {
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
    case Event(formatProgress: FormatProgress, _) =>
      ackActorRef ! formatProgress
      stay
  }

  onTransition {
    case Ready -> Converting =>
      log.info("Converting...")
      nextStateData match {
        case Convertible(id, conversionSet, timeDuration) => conversionSet.foreach {
          case Conversion(outputArgs, Converting) =>
            lazy val inlet = InputReader.read(input, id)
            val worker = context.actorOf(Worker.props(id, inlet, output))
            worker ! Convert(outputArgs, timeDuration)
        }
      }
    case Converting -> Ready => log.info("Conversion completed.")
  }

  def prepareConversion(id: String): Either[ConversionError, Float] = {
    implicit class OptionToEither[S](option: Option[S]) {
      def asEither[E](alternative: => E): Either[E, S] =
        option.map(Right(_)) getOrElse Left(alternative)
    }

    val mediaStreams = InputReader.extractStreamDetails(input, id)

    for {
      // Get the media stream information.
      mediaStream <- mediaStreams.headOption.asEither(NoMediaStreamAvailable)

      // Extract the time duration. This will be needed to compute the
      // progress percentage.
      timeDuration <- mediaStream.time.duration.asEither(StreamInfoIncomplete)
    } yield timeDuration
  }

  initialize()
}
