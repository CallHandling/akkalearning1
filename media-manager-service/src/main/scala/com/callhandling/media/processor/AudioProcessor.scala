package com.callhandling.media.processor

import akka.NotUsed
import akka.actor.{ActorLogging, ActorRef, FSM, Props}
import akka.stream.ActorMaterializer
import akka.util.ByteString
import com.callhandling.media.OutputFormat
import com.callhandling.media.converters.{ConversionError, ConversionErrorSet, NoMediaStreamAvailable, OutputArgs, StreamInfoIncomplete}
import com.callhandling.media.processor.AudioProcessor._
import com.callhandling.media.processor.Worker.Convert
import com.callhandling.media.io.{InputReader, OutputWriter}

object AudioProcessor {
  def props[I, O, SM](
      id: String,
      outputDetails: List[OutputArgs],
      input: I,
      output: O,
      ackActorRef: ActorRef)
      (implicit reader: InputReader[I, SM], writer: OutputWriter[O]): Props =
    Props(new AudioProcessor(id, outputDetails, input, output, ackActorRef))

  // FSM States
  sealed trait ConversionStatus
  case object Ready extends ConversionStatus
  case object Success extends ConversionStatus
  final case class Failed(reason: ConversionError) extends ConversionStatus
  case object Converting extends ConversionStatus

  // FSM Data
  sealed trait Data
  case object EmptyData extends Data
  final case class NonEmptyData[A](conversionSet: List[Conversion]) extends Data

  final case class Conversion(outputArgs: OutputArgs, status: ConversionStatus)

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

class AudioProcessor[I, O, SM](
    id: String,
    outputArgsSet: List[OutputArgs],
    input: I,
    output: O,
    ackActorRef: ActorRef)
    (implicit reader: InputReader[I, SM], writer: OutputWriter[O])
    extends FSM[ConversionStatus, Data] with ActorLogging {
  lazy val inlet = InputReader.read(input, id)
  lazy val mediaStreams = InputReader.extractDetails(input, id)

  startWith(Ready, EmptyData)

  when(Ready) {
    case Event(StartConversion(redo), data @ NonEmptyData(conversionSet)) =>
      def isPending: Conversion => Boolean = {
        case Conversion(_, Ready) => true
        case Conversion(_, Failed(_)) => redo
        case _ => false
      }

      val pendingFormats = conversionSet.filter(isPending)

      /*
      val result = streams.headOption.map { stream =>
        stream.time.duration.map { timeDuration =>
          val newFileId = FileActor.generateId

          val region = ClusterSharding(system).shardRegion(FileActor.RegionName)
          implicit val timeout: Timeout = 2.seconds

          val fileSink = createSink(system, region, newFileId, fileData.details.filename)
          Source.single(bytes).runWith(fileSink)(ActorMaterializer())

          region ! SendToEntity(
            newFileId, SetDetails(id = newFileId, details = fileData.details))

          region ! SendToEntity(
            newFileId, Convert(outputDetails, timeDuration))

          Right(newFileId)
        } getOrElse error("Could not extract time duration.")
      } getOrElse error("No media stream available.")
       */

      pendingFormats.foreach { case Conversion(outputArgs, status) =>
        implicit val materializer: ActorMaterializer = ActorMaterializer()
        implicit class OptionToEither[S](option: Option[S]) {
          def toEither[E](alternative: => E): Either[E, S] =
            option.map(Right(_)) getOrElse Left(alternative)
        }

        val converstionResult = for {
          mediaStream <- mediaStreams.headOption.toEither(NoMediaStreamAvailable)
          timeDuration <- mediaStream.time.duration.toEither(StreamInfoIncomplete)
          worker = context.actorOf(Worker.props(id, inlet, output))
        } yield Right {
          worker ! Convert(outputArgs, timeDuration)
          Success
        }
      }

      goto(Converting).using(data.copy(conversionSet = conversionSet.map { conversion =>
        if (isPending(conversion)) conversion.copy(status = Converting)
        else conversion
      }))
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
      }

      goto(Ready).using(NonEmptyData(newConversionDataSet))
  }

  initialize()
}
