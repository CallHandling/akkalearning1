package com.callhandling

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.directives.FileInfo
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.util.{ByteString, Timeout}
import com.callhandling.actors.{FileActor, SendToEntity}
import com.callhandling.http.JsonSupport._
import com.callhandling.http.validators.FormValidationRejection
import com.callhandling.http.validators.Validator._
import com.callhandling.http.validators._
import com.callhandling.media.MediaStream
import com.callhandling.media.converters.Converter.{OutputArgs, Progress}
import com.callhandling.media.converters.Formats.Format
import com.callhandling.media.io.{MediaReader, MediaWriter}

import scala.concurrent.ExecutionContextExecutor
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
