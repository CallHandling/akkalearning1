package com.callhandling

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{HttpResponse}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import com.callhandling.actors.{FileActor, StreamActor}
import com.callhandling.actors.StreamActor.{Ack, StreamCompleted, StreamFailure, StreamInitialized}
import com.callhandling.media.Converter.OutputDetails
import com.callhandling.media.Formats.Format
import com.callhandling.media.StreamDetails

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.io.StdIn
import akka.http.scaladsl.server.Directives.{entity, _}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import com.callhandling.media.DataType.Rational
import com.callhandling.media.StreamDetails._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.{Directive1, RejectionHandler}
import com.callhandling.Forms.{ConvertFileForm,  UploadFileForm}

object Service {

  final case class FileIdResult(fileId: String)
  final case class UploadResult(
      fileId: String,
      filename: String,
      description: String,
      streams: List[StreamDetails],
      outputFormats: List[Format])

  final case class ConversionResult(message: String, fileId: String, outputDetails: OutputDetails)

  object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
    type RJF[A] = RootJsonFormat[A]

    implicit val rationalFormat: RJF[Rational] = jsonFormat2(Rational)
    implicit val codecFormat: RJF[Codec] = jsonFormat5(Codec)
    implicit val aspectRationFormat: RJF[AspectRatio] = jsonFormat2(AspectRatio)
    implicit val colorFormat: RJF[Color] = jsonFormat4(Color)
    implicit val dimensionsFormat: RJF[Dimensions] = jsonFormat2(Dimensions)
    implicit val bitsFormat: RJF[Bits] = jsonFormat4(Bits)
    implicit val nbFormat: RJF[Nb] = jsonFormat3(Nb)
    implicit val samplesFormat: RJF[Samples] = jsonFormat2(Samples)
    implicit val frameRatesFormat: RJF[FrameRates] = jsonFormat2(FrameRates)
    implicit val timeFormat: RJF[Time] = jsonFormat6(Time)
    implicit val channelFormat: RJF[Channel] = jsonFormat2(Channel)
    implicit val streamDetailsFormat: RJF[StreamDetails] = jsonFormat21(StreamDetails.apply)
    implicit val fileFormatFormat: RJF[Format] = jsonFormat2(Format)
    implicit val outputDetailsFormat: RJF[OutputDetails] = jsonFormat2(OutputDetails)

    implicit val fileIdResultFormat: RJF[FileIdResult] = jsonFormat1(FileIdResult)
    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat5(UploadResult)
    implicit val conversionResultFormat: RJF[ConversionResult] = jsonFormat3(ConversionResult)

    implicit val uploadFileFormFormat: RJF[UploadFileForm] = jsonFormat1(UploadFileForm)
    implicit val convertFileFormFormat: RJF[ConvertFileForm] = jsonFormat2(ConvertFileForm)

    implicit val validatedFieldFormat = jsonFormat2(FieldErrorInfo)
    def validateForm[T](form: T)(implicit validator: Validator[T]): Directive1[T] = {
      validator(form) match {
        case Nil => provide(form)
        case errors: Seq[FieldErrorInfo] => reject(FormValidationRejection(errors))
      }
    }
  }

  final case class MediaFileDetail(description: String)

  def apply(fileManagerRegion: ActorRef)(
      implicit system: ActorSystem,
      materializer: ActorMaterializer,
      timeout: Timeout) =
    new Service(fileManagerRegion)
}

class Service(fileManagerRegion: ActorRef) (
    implicit system: ActorSystem,
    materializer: ActorMaterializer,
    timeout: Timeout) {
  import FileActor._
  import Service._

  import JsonSupport._
  import Forms._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit val uploadFileFormValidator = UploadFileFormValidator
  implicit val convertFileFormValidator = ConvertFileFormValidator

  implicit def formValidationRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case FormValidationRejection(invalidFields) =>
        complete(invalidFields)
      }
      .result()

  def uploadv1 = pathPrefix("upload") {
    pathEndOrSingleSlash {
      post {
        entity(as[UploadFileForm]) { form =>
          validateForm(form).apply { f =>
            val fileId = FileActor.generateId
            val fileDataF = fileManagerRegion ? EntityMessage(fileId, SetFormDetails(fileId, f))
            onSuccess(fileDataF) {
              case FileData(id, _, _, _, _) =>
                complete(FileIdResult(id))
              case _ => complete(internalError("Could not retrieve file data."))
            }
          }
        }
      }
    } ~
    withoutSizeLimit {
      path(Remaining) { fileId =>
        put {
          fileUpload("file") {
            case (metadata, byteSource) =>
              val streamActorF = fileManagerRegion ? EntityMessage(fileId, SetUpStream)
              val streamActor = Await.result(streamActorF, timeout.duration).asInstanceOf[ActorRef]

              lazy val fileSink = Sink.actorRefWithAck(
                streamActor,
                onInitMessage = StreamInitialized(metadata.fileName),
                ackMessage = Ack,
                onCompleteMessage = StreamCompleted,
                onFailureMessage = StreamFailure)

              byteSource.runWith(fileSink)

              val fileDataF = fileManagerRegion ? EntityMessage(fileId, GetFileData)
              onSuccess(fileDataF) {
                case FileData(id, details, streams, outputFormats, _) =>
                  complete(UploadResult(id, details.filename, details.description, streams, outputFormats))
                case _ => complete(internalError("Could not retrieve file data."))
              }
          }
        }
      }
    }
  }

  def convertv1 = pathPrefix("convert") {
    pathEndOrSingleSlash {
      post {
        entity(as[ConvertFileForm]) { form =>
          validateForm(form).apply {
            f =>
              val outputDetails = OutputDetails("converted", f.format)
              val conversionF = fileManagerRegion ? EntityMessage(f.fileId, ConvertAndKeep(outputDetails))

              onSuccess(conversionF) {
                case ConversionStarted(Left(errorMessage)) => complete(internalError(errorMessage))
                case ConversionStarted(Right(newFileId)) =>
                  complete(ConversionResult("Conversion Started", newFileId, outputDetails))
              }
          }
        }
      }
    }
  }

  def internalError(msg: String) = HttpResponse(InternalServerError, entity = msg)

  def restart(): Unit = {
    val route = pathPrefix("api" / "v1" / "file") { uploadv1 ~ convertv1 }
    val port = 8080
    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
