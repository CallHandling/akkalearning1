package com.callhandling

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.{Directive1, RejectionHandler, Route}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Keep, Sink}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.{ByteString, Timeout}
import com.callhandling.Forms._
import com.callhandling.actors.{FileActor, SendToEntity}
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.MediaStream._
import com.callhandling.media.converters.Converter.{OutputArgs, Progress}
import com.callhandling.media.io.{MediaReader, MediaWriter}
import com.callhandling.media.{MediaStream, Rational}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.Success

object Service {
  final case class FileIdResult(fileId: String)
  final case class UploadResult(
      fileId: String,
      filename: String,
      description: String,
      streams: Vector[MediaStream],
      outputFormats: Vector[Format])

  final case class ConversionResult(message: String, fileId: String, outputArgs: OutputArgs)

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
    implicit val streamDetailsFormat: RJF[MediaStream] = jsonFormat21(MediaStream.apply)
    implicit val fileFormatFormat: RJF[Format] = jsonFormat2(Format)
    implicit val outputArgsFormat: RJF[OutputArgs] = jsonFormat2(OutputArgs)

    implicit val fileIdResultFormat: RJF[FileIdResult] = jsonFormat1(FileIdResult)
    implicit val uploadResultFormat: RJF[UploadResult] = jsonFormat5(UploadResult)
    implicit val conversionResultFormat: RJF[ConversionResult] = jsonFormat3(ConversionResult)

    implicit val uploadFileFormFormat: RJF[UploadFileForm] = jsonFormat1(UploadFileForm)
    implicit val convertFileFormFormat: RJF[ConvertFileForm] = jsonFormat5(ConvertFileForm)
    implicit val fileIdFormFormat: RJF[FileIdForm] = jsonFormat1(FileIdForm)
    implicit val conversionProgressFormat: RJF[Progress] = jsonFormat10(Progress)

    implicit val validatedFieldFormat: RJF[FieldErrorInfo] = jsonFormat2(FieldErrorInfo)

    // TODO: Move this function somewhere
    def validateForm[T](form: T)(f: T => Route)(implicit validator: Validator[T]): Route = {
      validator(form) match {
        case Nil => provide(form)(f)
        case errors: Seq[FieldErrorInfo] => reject(FormValidationRejection(errors))
      }
    }
  }

  final case class MediaFileDetail(description: String)

  def apply[I, O, M](
      fileRegion: ActorRef,
      audioProcessorRegion: ActorRef,
      input: I,
      output: O)
      (implicit
          system: ActorSystem,
          mat: ActorMaterializer,
          timeout: Timeout,
          reader: MediaReader[I, M],
          writer: MediaWriter[O, M]) =
    new Service(fileRegion, audioProcessorRegion, input, output)
}

class Service[I, O, M](
    fileRegion: ActorRef, audioProcessorRegion: ActorRef, input: I, output: O)
    (implicit
        system: ActorSystem,
        mat: ActorMaterializer,
        timeout: Timeout,
        reader: MediaReader[I, M],
        writer: MediaWriter[O, M]) {
  import FileActor._
  import Forms._
  import Service._
  import JsonSupport._

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  implicit def formValidationRejectionHandler =
    RejectionHandler.newBuilder()
      .handle { case FormValidationRejection(invalidFields) =>
        complete(invalidFields)
      }
      .result()

  def uploadV1 = pathPrefix("upload") {
    pathEndOrSingleSlash {
      post {
        entity(as[UploadFileForm]) { form =>
          validateForm(form) { case UploadFileForm(description) =>
            val fileId = FileActor.generateId
            fileRegion ! SendToEntity(fileId, SetDescription(description))
            complete(FileIdResult(fileId))
          }
        }
      }
    } ~
    withoutSizeLimit {
      path(Remaining) { fileId =>
        put {
          validateForm(FileIdForm(fileId)) { _ =>
            //TODO: Add checking for valid vform.fileId on fileListActor
            if (false) complete(internalError("No actor found with id: " + fileId))
            else fileUpload("file") { case (FileInfo(_, filename, _), byteSource) =>
              fileRegion ! SendToEntity(fileId, UploadStarted)

              val outlet = MediaWriter.write(output, fileId)
              val uploadF = byteSource.toMat(outlet)(Keep.right).run

              onSuccess(uploadF) { _ =>
                fileRegion ! SendToEntity(fileId, UploadCompleted)
                fileRegion ! SendToEntity(fileId, SetFilename(filename))

                val fileDataF = fileRegion ? SendToEntity(fileId, GetDetails)
                onSuccess(fileDataF) {
                  case Details(_, description) =>
                    val streams = MediaReader.mediaStreams(input, fileId)
                    val outputFormats = MediaReader.outputFormats(input, fileId)
                    complete(UploadResult(fileId, filename, description, streams, outputFormats))
                  case _ => complete(internalError("Could not retrieve file data."))
                }
              }
            }
          }
        }
      }
    }
  }

  def convertV1 = pathPrefix("convert") {
    pathEndOrSingleSlash {
      post {
        entity(as[ConvertFileForm]) { form =>
          validateForm(form) { case ConvertFileForm(fileId, format, _, _, _) =>
            // TODO: filename is still hardcoded here
            val outputDetails = OutputArgs("converted", format)

            val conversionF = fileRegion ? SendToEntity(fileId, RequestForConversion(outputDetails))

            onSuccess(conversionF) {
              case ConversionStarted(Left(errorMessage)) => complete(internalError(errorMessage))
              case ConversionStarted(Right(newFileId)) =>
                complete(ConversionResult("Conversion Started", newFileId, outputDetails))
            }
          }
        }
      }
    } ~
    path("status" / Remaining) { fileId =>
      get {
        val form = FileIdForm(fileId)
        validateForm(form) {
          case FileIdForm(_) =>
            val conversionStatusF = fileRegion ? SendToEntity(fileId, GetConversionStatus)

            onSuccess(conversionStatusF) {
              case progress: Progress => complete(progress)
              case _ => complete(internalError("Could not retrieve conversion status."))
            }
        }
      }
    }
  }

  def playV1 = pathPrefix("play") {
    path(Remaining) { fileId =>
      get {
        val form = FileIdForm(fileId)
        validateForm(form) {
          case FileIdForm(fileId) =>
            val bytesF = fileRegion ? SendToEntity(fileId, Play)

            onComplete(bytesF) {
              case Success(bytes: ByteString) => complete(bytes)
              case _ => complete(internalError("Could not play the file"))
            }
        }
      }
    }
  }

  def internalError(msg: String) = HttpResponse(InternalServerError, entity = msg)

  def restart(): Unit = {
    val route = pathPrefix("api" / "v1" / "file") { uploadV1 ~ convertV1 ~ playV1}
    val port = 8080
    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:$port/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
