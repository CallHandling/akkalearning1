package com.callhandling.media.processor

import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.stream.ActorMaterializer
import cats.syntax.either._
import com.callhandling.media.converters._
import com.callhandling.media.io.{MediaReader, MediaWriter}
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.processor.Worker.Convert
import com.callhandling.media.{MediaStream, OutputFormat}

object AudioProcessor {
  val RegionName = "AudioProcessor"

  def props[I: MediaReader, O: MediaWriter](input: I, output: O): Props =
    Props(new AudioProcessor(input, output))

  sealed trait ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(reason: ConversionError) extends ConversionStatus

  // FSM States
  sealed trait State
  case object Idle extends ConversionStatus with State
  case object Converting extends ConversionStatus with State

  // FSM Data
  sealed trait Data
  final case class AudioData(
      id: String,
      ackActorRef: ActorRef,
      outputArgsSet: Vector[OutputArgs]) extends Data
  final case class Convertible(
      id: String,
      ackActorRef: ActorRef,
      conversionSet: Vector[Conversion],
      timeDuration: Float) extends Data

  final case class Conversion(outputArgs: OutputArgs, status: ConversionStatus)

  final case class SetMediaId(id: String)
  final case class SetAckActorRef(ackActorRef: ActorRef)
  final case class SetOutputArgsSet(outputArgsSet: Vector[OutputArgs])
  final case class StartConversion(redoFailed: Boolean)

  /**
    * Conversion status for one output format
    */
  final case class FormatConversionStatus(format: OutputFormat, status: ConversionStatus)

  /**
    * Conversion status for the whole file (all output formats have been considered.)
    */
  final case class ConversionResults(id: String, status: ConversionStatus)

  final case class FormatProgress(format: OutputFormat, progress: Progress)
}

class AudioProcessor[I: MediaReader, O: MediaWriter](input: I, output: O)
    extends FSM[State, Data] with ActorLogging {
  implicit val mat: ActorMaterializer = ActorMaterializer()

  val EmptyData = AudioData("", ActorRef.noSender, Vector())

  startWith(Idle, EmptyData)

  when(Idle) {
    def setData: StateFunction = {
      case Event(SetMediaId(id), data: AudioData) =>
        stay.using(data.copy(id = id))
      case Event(SetAckActorRef(ackActorRef), data: AudioData) =>
        stay.using(data.copy(ackActorRef = ackActorRef))
      case Event(SetOutputArgsSet(outputArgsSet), data: AudioData) =>
        stay.using(data.copy(outputArgsSet = outputArgsSet))
    }

    def startConversion: StateFunction = {
      case Event(StartConversion(_), data @ AudioData(id, ackActorRef, outputArgsSet)) =>
        val newData = prepareConversion(id) match {
          case Left(error) =>
            ackActorRef ! error
            data
          case Right(timeDuration) =>
            Convertible(id, ackActorRef, outputArgsSet.map(Conversion(_, Converting)), timeDuration)
        }
        goto(Converting).using(newData)
      case Event(StartConversion(redo), data @ Convertible(_, _, conversionSet, _)) =>
        def startConversion: Conversion => Conversion = _.copy(status = Converting)

        val convertedFormats = conversionSet.map {
          case conversion @ Conversion(_, Idle) => startConversion(conversion)
          case conversion @ Conversion(_, Failed(_)) if redo => startConversion(conversion)
          case conversion => conversion
        }

        goto(Converting).using(data.copy(conversionSet = convertedFormats))
    }

    setData orElse startConversion
  }

  when(Converting) {
    case Event(FormatConversionStatus(format, status),
        data @ Convertible(id, ackActorRef, conversionSet, _)) =>
      val newConversionSet = conversionSet.map {
        case conversion @ Conversion(OutputArgs(`format`, _, _, _), _) =>
          conversion.copy(status = status)
        case conversion => conversion
      }

      val remaining = newConversionSet.filter(_.status == Converting)

      val newState = if (remaining.isEmpty) {
        val conversionStatus = newConversionSet.foldLeft[ConversionStatus](Idle) {
          case (Failed(ConversionErrorSet(errors)), Conversion(_, Failed(errorCode))) =>
            Failed(ConversionErrorSet(errorCode :: errors))
          case (_, Conversion(_, Failed(errorCode))) =>
            Failed(ConversionErrorSet(errorCode :: Nil))
          case (_, Conversion(_, failed @ Failed(_))) => failed
          case (accStatus, Conversion(_, Success)) => accStatus
        }
        ackActorRef ! ConversionResults(id, conversionStatus)

        goto(Idle)
      } else stay

      newState.using(data.copy(conversionSet = newConversionSet))
    case Event(formatProgress: FormatProgress, convertible: Convertible) =>
      convertible.ackActorRef ! formatProgress
      stay
  }

  onTransition {
    case Idle -> Converting =>
      log.info("Converting...")
      nextStateData match {
        case Convertible(id, _, conversionSet, timeDuration) => conversionSet.foreach {
          case Conversion(outputArgs, Converting) =>
            val workerOr = for {
              inlet <- MediaReader[I].read(input, id)
            } yield context.actorOf(Worker.props(id, inlet, output))

            workerOr.foreach(_ ! Convert(outputArgs, timeDuration))
        }
      }
    case Converting -> Idle => log.info("Conversion completed.")
  }

  def prepareConversion(id: String): Either[ConversionError, Float] = {
    val mediaStreams = MediaReader[I].mediaStreams(input, id)

    for {
      // Get the media stream information.
      mediaStream <- Either.fromOption[ConversionError, MediaStream](
        mediaStreams.headOption, NoMediaStreamAvailable)

      // Extract the time duration. This will be needed to compute the
      // progress percentage.
      timeDuration <- Either.fromOption[ConversionError, Float](
        mediaStream.time.duration, StreamInfoIncomplete)
    } yield timeDuration
  }

  initialize()
}
